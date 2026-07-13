import asyncio
from datetime import UTC, datetime

from fastapi import APIRouter, Header, HTTPException, Query, Request, WebSocket, WebSocketDisconnect
from sqlalchemy import func, or_, select, text
from sqlalchemy.exc import SQLAlchemyError

from app.database import MatchRecord, record_to_match
from app.schemas import ApiResponse, Dashboard, Health, Match, Stats

router = APIRouter()


async def build_health_status(request: Request) -> Health:
    tracker = request.app.state.tracker
    settings = request.app.state.settings
    upstream = (
        "UNKNOWN" if tracker.last_sync is None else ("UP" if tracker.failures == 0 else "DEGRADED")
    )
    database_status = "UP"
    try:
        async with request.app.state.database.sessions() as session:
            await session.execute(text("SELECT 1"))
    except SQLAlchemyError:
        database_status = "DOWN"

    is_degraded = tracker.failures >= settings.max_upstream_failures or database_status == "DOWN"
    return Health(
        status="DEGRADED" if is_degraded else "UP",
        upstream=upstream,
        database=database_status,
        live_matches=len(tracker.live),
        last_sync=tracker.last_sync,
        consecutive_failures=tracker.failures,
        polling_mode=tracker.polling_mode,
        version=settings.app_version,
    )


async def build_stats(request: Request) -> Stats:
    tracker = request.app.state.tracker
    async with request.app.state.database.sessions() as session:
        completed = await session.scalar(
            select(func.count()).select_from(MatchRecord).where(MatchRecord.status == "COMPLETED")
        )
        teams = await session.execute(
            select(MatchRecord.team1_name).union(select(MatchRecord.team2_name))
        )
    active_events = len({match.tournament for match in tracker.live + tracker.upcoming})
    return Stats(
        live_matches=len(tracker.live),
        upcoming_matches=len(tracker.upcoming),
        completed_matches=completed or 0,
        tracked_teams=len(set(teams.scalars().all())),
        active_events=active_events,
    )


@router.get("/dashboard", response_model=ApiResponse[Dashboard])
async def get_dashboard(request: Request) -> ApiResponse[Dashboard]:
    tracker = request.app.state.tracker
    async with request.app.state.database.sessions() as session:
        rows = (
            await session.scalars(
                select(MatchRecord)
                .where(MatchRecord.status == "COMPLETED")
                .order_by(MatchRecord.source_rank.asc(), MatchRecord.updated_at.desc())
                .limit(8)
            )
        ).all()
    data = Dashboard(
        live=tracker.live,
        upcoming=tracker.upcoming[:8],
        recent=[record_to_match(row) for row in rows],
        stats=await build_stats(request),
        health=await build_health_status(request),
    )
    return ApiResponse(
        data=data,
        meta={"generated_at": datetime.now(UTC).isoformat()},
    )


@router.get("/matches", response_model=ApiResponse[list[Match]])
async def list_matches(
    request: Request,
    status: str | None = Query(default=None, pattern="^(LIVE|UPCOMING|COMPLETED)$"),
    query: str | None = Query(default=None, min_length=2, max_length=80),
    limit: int = Query(default=20, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
) -> ApiResponse[list[Match]]:
    statement = select(MatchRecord)
    if status:
        statement = statement.where(MatchRecord.status == status)
    if query:
        term = f"%{query.strip()}%"
        statement = statement.where(
            or_(
                MatchRecord.team1_name.ilike(term),
                MatchRecord.team2_name.ilike(term),
                MatchRecord.tournament.ilike(term),
                MatchRecord.event.ilike(term),
            )
        )
    statement = statement.order_by(MatchRecord.updated_at.desc()).offset(offset).limit(limit)
    async with request.app.state.database.sessions() as session:
        rows = (await session.scalars(statement)).all()
    return ApiResponse(
        data=[record_to_match(row) for row in rows],
        meta={"limit": limit, "offset": offset},
    )


@router.get("/health", response_model=Health)
async def health(request: Request) -> Health:
    return await build_health_status(request)


@router.get("/stats", response_model=ApiResponse[Stats])
async def get_stats(request: Request) -> ApiResponse[Stats]:
    return ApiResponse(data=await build_stats(request))


@router.post("/admin/refresh", status_code=202, response_model=ApiResponse[dict[str, bool]])
async def refresh_matches(
    request: Request,
    x_admin_key: str | None = Header(default=None),
) -> ApiResponse[dict[str, bool]]:
    configured = request.app.state.settings.admin_key
    if not configured or x_admin_key != configured:
        raise HTTPException(status_code=403, detail="Administrative key required")
    await request.app.state.tracker.refresh()
    return ApiResponse(data={"queued": True})


@router.websocket("/ws")
async def websocket_updates(websocket: WebSocket) -> None:
    await websocket.accept()
    tracker = websocket.app.state.tracker
    queue = tracker.subscribe()
    await websocket.send_json(
        {
            "type": "matches.snapshot",
            "data": {
                "live": [item.model_dump(mode="json") for item in tracker.live],
                "upcoming": [item.model_dump(mode="json") for item in tracker.upcoming[:8]],
            },
        }
    )
    try:
        while True:
            try:
                message = await asyncio.wait_for(queue.get(), timeout=25)
                await websocket.send_json(message)
            except TimeoutError:
                await websocket.send_json({"type": "heartbeat"})
    except WebSocketDisconnect:
        pass
    finally:
        tracker.unsubscribe(queue)
