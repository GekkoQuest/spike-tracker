# Spike Tracker

Spike Tracker is a self-hosted Valorant match dashboard. It retrieves data from the community-maintained [vlresports API](https://vlr.orlandomm.net/docs), stores match history in PostgreSQL, and presents live, upcoming, and completed matches through a React interface and FastAPI API.

## Run

Copy `.env.example` to `.env` and replace `POSTGRES_PASSWORD`. Replace `ADMIN_KEY`, or leave it empty to disable manual refreshes.

```bash
docker compose up --build
```

Open <http://localhost:8080>. API documentation is at <http://localhost:8080/api/v1/docs>. PostgreSQL data persists in the `postgres-data` Docker volume.

PostgreSQL 16 volumes must be exported and restored when moving to PostgreSQL 18; they cannot be mounted directly by the newer server.

## Development

```bash
docker compose -f docker-compose.dev.yml up --build
```

- Frontend: <http://localhost:5173>
- API: <http://localhost:8000>

The development stack provides hot reload while keeping dependencies and Python bytecode out of the repository.

## Structure

- `backend/` — Python 3.14, FastAPI, Pydantic, async SQLAlchemy, Alembic, and pytest
- `frontend/` — React 19, TypeScript, Vite, and TanStack Query
- `monitoring/` — Prometheus configuration
- `docker-compose.yml` — production application and PostgreSQL
- `docker-compose.dev.yml` — local development services

The production image runs as a non-root user, applies database migrations at startup, and serves the API and compiled frontend from one container. Dashboard updates use WebSockets with a REST fallback.

## API

Application endpoints use the `/api/v1` prefix.

| Endpoint | Description |
| --- | --- |
| `GET /dashboard` | Dashboard snapshot |
| `GET /matches` | Filterable match archive |
| `GET /health` | Application, database, and upstream status |
| `GET /stats` | Match and team totals |
| `POST /admin/refresh` | Authenticated upstream refresh |
| `WS /ws` | Dashboard update stream |

Prometheus metrics are available at `GET /metrics`.

## Configuration

Runtime settings use environment variables. Common production values are listed in `.env.example`; polling, timeouts, and other defaults are defined in `backend/app/config.py`. The administrative refresh endpoint requires the configured key in the `X-Admin-Key` header.

## Checks

These commands run the CI checks without writing dependencies or caches into the repository:

```bash
docker build --target development -t spike-tracker-api-dev .
docker run --rm spike-tracker-api-dev sh -c "ruff check --no-cache . && ruff format --check --no-cache . && mypy --cache-dir=/tmp/mypy-cache app tests && python -m pytest -p no:cacheprovider"

docker run --rm -v "$PWD/frontend:/src:ro" -w /work node:24.18-alpine \
  sh -c "npm install --global npm@12.0.1 && cp -R /src/. /work/ && npm ci --no-audit --no-fund && npm run format:check && npm run lint && npm test && npm run build"
```

## Data and license

Match data is supplied by the independently maintained [vlresports API](https://vlr.orlandomm.net/docs) and links back to VLR. Spike Tracker is licensed under the [GNU General Public License v3.0](LICENSE).
