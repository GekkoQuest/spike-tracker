from datetime import UTC, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, computed_field

MatchStatus = Literal["LIVE", "UPCOMING", "COMPLETED"]


class Team(BaseModel):
    id: str | None = None
    name: str = "TBD"
    country: str = "un"
    score: int | None = None
    logo: str | None = None
    won: bool | None = None


class Match(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    status: MatchStatus
    teams: tuple[Team, Team]
    event: str = "Event to be announced"
    tournament: str = "Valorant Champions Tour"
    event_logo: str | None = None
    starts_at: datetime | None = None
    countdown: str | None = None
    relative_time: str | None = None
    source_rank: int = 0
    updated_at: datetime = Field(default_factory=lambda: datetime.now(UTC))

    @computed_field  # type: ignore[prop-decorator]
    @property
    def vlr_url(self) -> str:
        return f"https://www.vlr.gg/{self.id}"


class Health(BaseModel):
    status: Literal["UP", "DEGRADED"]
    upstream: Literal["UP", "DEGRADED", "UNKNOWN"]
    database: Literal["UP", "DOWN"]
    live_matches: int
    last_sync: datetime | None
    consecutive_failures: int
    polling_mode: Literal["ACTIVE", "IDLE"]
    version: str


class Stats(BaseModel):
    live_matches: int = 0
    upcoming_matches: int = 0
    completed_matches: int = 0
    tracked_teams: int = 0
    active_events: int = 0


class Dashboard(BaseModel):
    live: list[Match]
    upcoming: list[Match]
    recent: list[Match]
    stats: Stats
    health: Health


class ApiResponse[DataT](BaseModel):
    success: bool = True
    data: DataT
    meta: dict[str, object] = Field(default_factory=dict)
