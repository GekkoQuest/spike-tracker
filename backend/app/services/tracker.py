from __future__ import annotations

import asyncio
import logging
from contextlib import suppress
from datetime import UTC, datetime, timedelta
from typing import TYPE_CHECKING, Protocol

from app.services.demo import demo_matches
from app.services.vlr import UpstreamUnavailable

if TYPE_CHECKING:
    from app.config import Settings
    from app.database import Database
    from app.schemas import Match

logger = logging.getLogger(__name__)


class MatchSource(Protocol):
    async def close(self) -> None: ...

    async def get_results(self) -> list[Match]: ...

    async def get_schedule(self) -> list[Match]: ...


class MatchTracker:
    def __init__(self, settings: Settings, database: Database, client: MatchSource) -> None:
        self.settings = settings
        self.database = database
        self.client = client
        self.matches: dict[str, Match] = {}
        self.last_sync: datetime | None = None
        self.failures = 0
        self.polling_mode = "ACTIVE"
        self._task: asyncio.Task[None] | None = None
        self._wake = asyncio.Event()
        self._subscribers: set[asyncio.Queue[dict[str, object]]] = set()
        self._last_result_sync: datetime | None = None

    async def start(self) -> None:
        if self.settings.demo_mode:
            items = demo_matches()
            self.matches = {item.id: item for item in items}
            await self.database.upsert_matches(items)
            self.last_sync = datetime.now(UTC)
        if self.settings.tracking_enabled:
            self._task = asyncio.create_task(self._run(), name="match-tracker")

    async def stop(self) -> None:
        if self._task:
            self._task.cancel()
            await asyncio.gather(self._task, return_exceptions=True)
        await self.client.close()

    async def refresh(self) -> None:
        self._wake.set()

    async def sync(self) -> None:
        try:
            schedule = await self.client.get_schedule()
            results: list[Match] = []
            now = datetime.now(UTC)
            result_sync_due = self._last_result_sync is None or (
                now - self._last_result_sync >= timedelta(minutes=self.settings.result_sync_minutes)
            )
            if result_sync_due:
                results = await self.client.get_results()
                self._last_result_sync = now

            schedule_ids = {match.id for match in schedule}
            stale_schedule_ids = {
                match_id
                for match_id, match in self.matches.items()
                if match.status in {"LIVE", "UPCOMING"} and match_id not in schedule_ids
            }
            for match_id in stale_schedule_ids:
                self.matches.pop(match_id, None)

            items = schedule + results
            if self.settings.demo_mode and not items:
                items = demo_matches()
            for item in items:
                self.matches[item.id] = item
            await self.database.upsert_matches(items)
            self.last_sync = datetime.now(UTC)
            self.failures = 0
            self.polling_mode = "ACTIVE" if self.live else "IDLE"
            await self.broadcast()
        except UpstreamUnavailable as exc:
            self.failures += 1
            logger.warning(
                "Upstream sync failed (%s/%s): %s",
                self.failures,
                self.settings.max_upstream_failures,
                exc,
            )

    async def _run(self) -> None:
        while True:
            await self.sync()
            interval = (
                self.settings.active_poll_seconds if self.live else self.settings.idle_poll_seconds
            )
            self._wake.clear()
            with suppress(TimeoutError):
                await asyncio.wait_for(self._wake.wait(), timeout=interval)

    @property
    def live(self) -> list[Match]:
        return sorted(
            (match for match in self.matches.values() if match.status == "LIVE"),
            key=lambda match: match.id,
        )

    @property
    def upcoming(self) -> list[Match]:
        return sorted(
            (match for match in self.matches.values() if match.status == "UPCOMING"),
            key=lambda match: match.starts_at or datetime.max.replace(tzinfo=UTC),
        )

    async def broadcast(self) -> None:
        message: dict[str, object] = {
            "type": "matches.updated",
            "data": {
                "live": [item.model_dump(mode="json") for item in self.live],
                "upcoming": [item.model_dump(mode="json") for item in self.upcoming[:8]],
            },
            "sent_at": datetime.now(UTC).isoformat(),
        }
        dead: list[asyncio.Queue[dict[str, object]]] = []
        for queue in self._subscribers:
            try:
                queue.put_nowait(message)
            except asyncio.QueueFull:
                dead.append(queue)
        for queue in dead:
            self._subscribers.discard(queue)

    def subscribe(self) -> asyncio.Queue[dict[str, object]]:
        queue: asyncio.Queue[dict[str, object]] = asyncio.Queue(maxsize=5)
        self._subscribers.add(queue)
        return queue

    def unsubscribe(self, queue: asyncio.Queue[dict[str, object]]) -> None:
        self._subscribers.discard(queue)
