from __future__ import annotations

import logging
from datetime import UTC, datetime
from typing import TYPE_CHECKING, cast

import httpx

from app.schemas import Match, MatchStatus, Team

if TYPE_CHECKING:
    from collections.abc import Mapping

logger = logging.getLogger(__name__)


class UpstreamUnavailable(RuntimeError):
    pass


class VlrClient:
    def __init__(self, base_url: str, theme: str, timeout: float) -> None:
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=httpx.Timeout(timeout),
            headers={
                "User-Agent": "SpikeTracker/2.0 (+https://github.com/GekkoQuest/spike-tracker)"
            },
        )
        self.theme = theme

    async def close(self) -> None:
        await self._client.aclose()

    async def get_schedule(self) -> list[Match]:
        return await self._get("/api/v1/matches", "UPCOMING")

    async def get_results(self) -> list[Match]:
        return await self._get("/api/v1/results", "COMPLETED", page=1)

    async def _get(
        self,
        path: str,
        fallback_status: MatchStatus,
        **params: str | int,
    ) -> list[Match]:
        try:
            response = await self._client.get(path, params={"theme": self.theme, **params})
            response.raise_for_status()
            payload: object = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise UpstreamUnavailable(f"VLR API request failed: {exc}") from exc

        payload_mapping = self._as_mapping(payload)
        raw_matches = payload_mapping.get(
            "data",
            payload_mapping.get("matches", payload_mapping.get("results", [])),
        )
        if not isinstance(raw_matches, list):
            raise UpstreamUnavailable("VLR API returned an unexpected response shape")

        parsed: list[Match] = []
        for rank, raw in enumerate(cast("list[object]", raw_matches)):
            try:
                match = self._parse_match(self._as_mapping(raw), fallback_status)
                match.source_rank = rank
                parsed.append(match)
            except (KeyError, TypeError, ValueError) as exc:
                logger.warning("Skipping malformed VLR match: %s", exc)
        return parsed

    @staticmethod
    def _parse_match(raw: Mapping[str, object], fallback_status: MatchStatus) -> Match:
        teams_value = raw.get("teams")
        raw_teams = (
            cast("list[object]", teams_value)
            if isinstance(teams_value, list)
            else [raw.get("team1", {}), raw.get("team2", {})]
        )
        if len(raw_teams) < 2:
            raise ValueError("match has fewer than two teams")

        status_text = str(raw.get("status", fallback_status)).upper()
        status: MatchStatus = fallback_status
        if status_text == "LIVE":
            status = "LIVE"
        elif status_text == "UPCOMING":
            status = "UPCOMING"
        elif status_text == "COMPLETED":
            status = "COMPLETED"

        starts_at = None
        utc_value = raw.get("utc")
        timestamp_value = raw.get("timestamp")
        if utc_value:
            starts_at = datetime.fromisoformat(str(utc_value).replace("Z", "+00:00"))
        elif timestamp_value:
            starts_at = datetime.fromtimestamp(int(str(timestamp_value)), tz=UTC)

        def parse_team(team: Mapping[str, object]) -> Team:
            score = team.get("score")
            won = team.get("won")
            return Team(
                id=str(team["id"]) if team.get("id") is not None else None,
                name=str(team.get("name") or "TBD"),
                country=str(team.get("country") or "un").lower(),
                score=int(str(score)) if score not in (None, "", "-") else None,
                logo=VlrClient._safe_logo(team.get("logo")),
                won=won if isinstance(won, bool) else None,
            )

        return Match(
            id=str(raw["id"]),
            status=status,
            teams=(
                parse_team(VlrClient._as_mapping(raw_teams[0])),
                parse_team(VlrClient._as_mapping(raw_teams[1])),
            ),
            event=str(raw.get("event") or "Match day"),
            tournament=str(raw.get("tournament") or "Valorant esports"),
            event_logo=VlrClient._safe_logo(raw.get("img")),
            starts_at=starts_at,
            countdown=VlrClient._optional_text(raw.get("in")),
            relative_time=VlrClient._optional_text(raw.get("ago")),
        )

    @staticmethod
    def _as_mapping(value: object) -> Mapping[str, object]:
        if not isinstance(value, dict) or not all(isinstance(key, str) for key in value):
            raise TypeError("expected an object with string keys")
        return cast("dict[str, object]", value)

    @staticmethod
    def _optional_text(value: object) -> str | None:
        return str(value) if value is not None else None

    @staticmethod
    def _safe_logo(value: object) -> str | None:
        if not value:
            return None
        logo = str(value)
        if "tmp/vlr.png" in logo:
            return None
        if logo.startswith("//"):
            return f"https:{logo}"
        return logo if logo.startswith("https://") else None
