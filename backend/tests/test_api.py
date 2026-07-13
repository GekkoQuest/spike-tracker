from __future__ import annotations

from typing import TYPE_CHECKING

import pytest
from httpx import ASGITransport, AsyncClient

from app.config import Settings
from app.main import create_app

if TYPE_CHECKING:
    from pathlib import Path


@pytest.fixture
def settings(tmp_path: Path) -> Settings:
    return Settings(
        database_url=f"sqlite+aiosqlite:///{tmp_path / 'test.db'}",
        demo_mode=True,
        tracking_enabled=False,
        static_dir=tmp_path / "missing",
    )


def test_comma_separated_hosts_are_parsed() -> None:
    settings = Settings(
        allowed_origins="http://localhost:5173,https://spike.example.com",
        trusted_hosts="localhost,spike.example.com",
    )
    assert settings.allowed_origins == ["http://localhost:5173", "https://spike.example.com"]
    assert settings.trusted_hosts == ["localhost", "spike.example.com"]


@pytest.mark.asyncio
async def test_dashboard_has_demo_matches(settings: Settings) -> None:
    app = create_app(settings)
    async with (
        app.router.lifespan_context(app),
        AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client,
    ):
        response = await client.get("/api/v1/dashboard")
    assert response.status_code == 200
    payload = response.json()
    assert payload["success"] is True
    assert len(payload["data"]["live"]) == 2
    assert payload["data"]["health"]["status"] == "UP"
    assert payload["data"]["health"]["database"] == "UP"


@pytest.mark.asyncio
async def test_match_search_and_health(settings: Settings) -> None:
    app = create_app(settings)
    async with (
        app.router.lifespan_context(app),
        AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client,
    ):
        search = await client.get("/api/v1/matches", params={"query": "Paper", "limit": 5})
        health = await client.get("/api/v1/health")
    assert search.status_code == 200
    assert len(search.json()["data"]) == 1
    assert health.json()["live_matches"] == 2


@pytest.mark.asyncio
async def test_admin_refresh_is_closed_by_default(settings: Settings) -> None:
    app = create_app(settings)
    async with (
        app.router.lifespan_context(app),
        AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client,
    ):
        response = await client.post("/api/v1/admin/refresh")
    assert response.status_code == 403
