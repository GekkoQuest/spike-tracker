from __future__ import annotations

from typing import TYPE_CHECKING

import pytest

from app.config import Settings
from app.database import Database
from app.schemas import Match, Team
from app.services.tracker import MatchTracker

if TYPE_CHECKING:
    from pathlib import Path


class FakeVlrClient:
    def __init__(self, schedules: list[list[Match]]) -> None:
        self.schedules = iter(schedules)
        self.result_calls = 0

    async def get_schedule(self) -> list[Match]:
        return next(self.schedules)

    async def get_results(self) -> list[Match]:
        self.result_calls += 1
        return []

    async def close(self) -> None:
        pass


@pytest.mark.asyncio
async def test_tracker_reconciles_stale_live_matches_and_respects_result_cadence(
    tmp_path: Path,
) -> None:
    live = Match(
        id="live-1",
        status="LIVE",
        teams=(Team(name="Alpha", score=1), Team(name="Bravo", score=0)),
    )
    client = FakeVlrClient([[live], []])
    database = Database(f"sqlite+aiosqlite:///{tmp_path / 'tracker.db'}")
    await database.create_schema()
    settings = Settings(tracking_enabled=False, result_sync_minutes=10)
    tracker = MatchTracker(settings, database, client)

    await tracker.sync()
    assert [match.id for match in tracker.live] == ["live-1"]
    assert client.result_calls == 1

    await tracker.sync()
    assert tracker.live == []
    assert client.result_calls == 1
    await tracker.stop()
    await database.close()
