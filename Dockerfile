# syntax=docker/dockerfile:1
FROM node:26.8-alpine AS frontend-build
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm install --global npm@12.0.1 && npm ci
COPY frontend/ ./
RUN npm run build

FROM python:3.14.6-slim AS python-base
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPYCACHEPREFIX=/tmp/pycache \
    PIP_DISABLE_PIP_VERSION_CHECK=1
WORKDIR /app/backend
RUN addgroup --system app && adduser --system --ingroup app app
COPY backend/ ./
RUN --mount=type=cache,target=/root/.cache/pip pip install --no-cache-dir .

FROM python-base AS development
RUN --mount=type=cache,target=/root/.cache/pip pip install --no-cache-dir '.[dev]'
USER app
EXPOSE 8000
CMD ["sh", "-c", "alembic upgrade head && exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload"]

FROM python-base AS production
RUN chown -R app:app /app/backend
COPY --from=frontend-build --chown=app:app /build/frontend/dist /app/static
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8080/api/v1/health', timeout=3)"
CMD ["sh", "-c", "alembic upgrade head && exec uvicorn app.main:app --host 0.0.0.0 --port 8080 --proxy-headers --forwarded-allow-ips='*'"]
