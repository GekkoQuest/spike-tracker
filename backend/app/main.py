from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from pathlib import Path
from typing import TYPE_CHECKING

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from prometheus_client import Counter, Histogram, make_asgi_app
from starlette.middleware.trustedhost import TrustedHostMiddleware

if TYPE_CHECKING:
    from collections.abc import AsyncIterator

    from starlette.middleware.base import RequestResponseEndpoint
    from starlette.responses import Response

from app.api import router
from app.config import Settings, get_settings
from app.database import Database
from app.services.tracker import MatchTracker
from app.services.vlr import VlrClient

HTTP_REQUESTS = Counter(
    "spiketracker_http_requests_total",
    "HTTP requests",
    ["method", "path", "status"],
)
HTTP_REQUEST_DURATION = Histogram(
    "spiketracker_http_request_seconds",
    "HTTP request latency",
    ["method", "path"],
)


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        database = Database(settings.database_url)
        await database.create_schema()
        client = VlrClient(
            settings.vlr_api_url,
            settings.vlr_theme,
            settings.upstream_timeout_seconds,
        )
        tracker = MatchTracker(settings, database, client)
        app.state.settings = settings
        app.state.database = database
        app.state.tracker = tracker
        try:
            await tracker.start()
            yield
        finally:
            await tracker.stop()
            await database.close()

    app = FastAPI(
        title=settings.app_name,
        version=settings.app_version,
        description="Real-time Valorant esports match intelligence.",
        docs_url=f"{settings.api_prefix}/docs",
        openapi_url=f"{settings.api_prefix}/openapi.json",
        lifespan=lifespan,
    )
    app.add_middleware(GZipMiddleware, minimum_size=1000)
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=settings.trusted_hosts)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.allowed_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST"],
        allow_headers=["Content-Type", "X-Admin-Key"],
    )

    @app.middleware("http")
    async def observe_and_secure(
        request: Request,
        call_next: RequestResponseEndpoint,
    ) -> Response:
        path = request.url.path
        with HTTP_REQUEST_DURATION.labels(request.method, path).time():
            response = await call_next(request)
        HTTP_REQUESTS.labels(request.method, path, response.status_code).inc()
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
        response.headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()"
        return response

    app.include_router(router, prefix=settings.api_prefix)
    app.mount("/metrics", make_asgi_app())

    static_dir = Path(settings.static_dir)
    if static_dir.is_dir():
        assets = static_dir / "assets"
        if assets.is_dir():
            app.mount("/assets", StaticFiles(directory=assets), name="assets")

        static_root = static_dir.resolve()

        @app.get("/{path:path}", include_in_schema=False)
        async def frontend(path: str) -> FileResponse:
            target = (static_root / path).resolve()
            if path and target.is_relative_to(static_root) and target.is_file():
                return FileResponse(target)
            return FileResponse(static_root / "index.html")
    else:

        @app.get("/", include_in_schema=False)
        async def api_root() -> JSONResponse:
            return JSONResponse(
                {
                    "name": settings.app_name,
                    "version": settings.app_version,
                    "docs": f"{settings.api_prefix}/docs",
                }
            )

    return app


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s — %(message)s")
app = create_app()
