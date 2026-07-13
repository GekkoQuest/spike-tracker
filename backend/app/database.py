from __future__ import annotations

from datetime import UTC, datetime
from typing import TYPE_CHECKING

from sqlalchemy import DateTime, Index, Integer, String, Text, func, select
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column
from sqlalchemy.pool import StaticPool

if TYPE_CHECKING:
    from collections.abc import AsyncIterator

    from app.schemas import Match


class Base(DeclarativeBase):
    pass


class MatchRecord(Base):
    __tablename__ = "matches"
    __table_args__ = (
        Index("ix_matches_status_starts_at", "status", "starts_at"),
        Index("ix_matches_tournament", "tournament"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    source_id: Mapped[str] = mapped_column(String(32), unique=True, nullable=False, index=True)
    status: Mapped[str] = mapped_column(String(16), nullable=False, index=True)
    team1_id: Mapped[str | None] = mapped_column(String(32))
    team1_name: Mapped[str] = mapped_column(String(160), nullable=False)
    team1_country: Mapped[str] = mapped_column(String(8), default="un")
    team1_score: Mapped[int | None] = mapped_column(Integer)
    team1_logo: Mapped[str | None] = mapped_column(Text)
    team1_won: Mapped[bool | None]
    team2_id: Mapped[str | None] = mapped_column(String(32))
    team2_name: Mapped[str] = mapped_column(String(160), nullable=False)
    team2_country: Mapped[str] = mapped_column(String(8), default="un")
    team2_score: Mapped[int | None] = mapped_column(Integer)
    team2_logo: Mapped[str | None] = mapped_column(Text)
    team2_won: Mapped[bool | None]
    event: Mapped[str] = mapped_column(String(240), default="")
    tournament: Mapped[str] = mapped_column(String(240), default="")
    event_logo: Mapped[str | None] = mapped_column(Text)
    starts_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    countdown: Mapped[str | None] = mapped_column(String(40))
    relative_time: Mapped[str | None] = mapped_column(String(40))
    source_rank: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    first_seen_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )


class Database:
    def __init__(self, url: str) -> None:
        engine_args: dict[str, object] = {"pool_pre_ping": True}
        if url in {"sqlite+aiosqlite://", "sqlite+aiosqlite:///:memory:"}:
            engine_args["poolclass"] = StaticPool
        self.engine: AsyncEngine = create_async_engine(url, **engine_args)
        self.sessions = async_sessionmaker(self.engine, expire_on_commit=False)

    async def create_schema(self) -> None:
        async with self.engine.begin() as connection:
            await connection.run_sync(Base.metadata.create_all)

    async def close(self) -> None:
        await self.engine.dispose()

    async def session(self) -> AsyncIterator[AsyncSession]:
        async with self.sessions() as session:
            yield session

    async def upsert_matches(self, matches: list[Match]) -> None:
        if not matches:
            return
        async with self.sessions() as session, session.begin():
            source_ids = [match.id for match in matches]
            existing = {
                record.source_id: record
                for record in (
                    await session.scalars(
                        select(MatchRecord).where(MatchRecord.source_id.in_(source_ids))
                    )
                ).all()
            }
            now = datetime.now(UTC)
            for match in matches:
                row = existing.get(match.id) or MatchRecord(source_id=match.id)
                row.status = match.status
                row.team1_id, row.team1_name = match.teams[0].id, match.teams[0].name
                row.team1_country, row.team1_score = match.teams[0].country, match.teams[0].score
                row.team1_logo, row.team1_won = match.teams[0].logo, match.teams[0].won
                row.team2_id, row.team2_name = match.teams[1].id, match.teams[1].name
                row.team2_country, row.team2_score = match.teams[1].country, match.teams[1].score
                row.team2_logo, row.team2_won = match.teams[1].logo, match.teams[1].won
                row.event, row.tournament = match.event, match.tournament
                row.event_logo, row.starts_at = match.event_logo, match.starts_at
                row.countdown, row.relative_time, row.updated_at = (
                    match.countdown,
                    match.relative_time,
                    now,
                )
                row.source_rank = match.source_rank
                if match.id not in existing:
                    session.add(row)


def record_to_match(row: MatchRecord) -> Match:
    from app.schemas import Match, Team

    return Match(
        id=row.source_id,
        status=row.status,
        teams=(
            Team(
                id=row.team1_id,
                name=row.team1_name,
                country=row.team1_country,
                score=row.team1_score,
                logo=row.team1_logo,
                won=row.team1_won,
            ),
            Team(
                id=row.team2_id,
                name=row.team2_name,
                country=row.team2_country,
                score=row.team2_score,
                logo=row.team2_logo,
                won=row.team2_won,
            ),
        ),
        event=row.event,
        tournament=row.tournament,
        event_logo=row.event_logo,
        starts_at=row.starts_at,
        countdown=row.countdown,
        relative_time=row.relative_time,
        source_rank=row.source_rank,
        updated_at=row.updated_at,
    )
