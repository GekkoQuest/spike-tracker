from functools import lru_cache
from pathlib import Path

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
        enable_decoding=False,
    )

    app_name: str = "SpikeTracker"
    app_version: str = "2.0.0"
    environment: str = "development"
    debug: bool = False
    api_prefix: str = "/api/v1"
    database_url: str = "sqlite+aiosqlite:///./backend/.data/spiketracker.db"
    vlr_api_url: str = "https://vlr.orlandomm.net"
    vlr_theme: str = "dark"
    active_poll_seconds: int = Field(default=20, ge=10, le=300)
    idle_poll_seconds: int = Field(default=120, ge=30, le=900)
    result_sync_minutes: int = Field(default=10, ge=1, le=120)
    upstream_timeout_seconds: float = Field(default=20, ge=2, le=60)
    max_upstream_failures: int = Field(default=5, ge=1, le=20)
    allowed_origins: list[str] = Field(
        default_factory=lambda: ["http://localhost:5173", "http://localhost:8080"]
    )
    trusted_hosts: list[str] = Field(default_factory=lambda: ["*"])
    admin_key: str | None = None
    demo_mode: bool = False
    tracking_enabled: bool = True
    static_dir: Path = Path("/app/static")

    @field_validator("allowed_origins", "trusted_hosts", mode="before")
    @classmethod
    def parse_csv(cls, value: object) -> object:
        if isinstance(value, str) and not value.strip().startswith("["):
            return [part.strip() for part in value.split(",") if part.strip()]
        return value


@lru_cache
def get_settings() -> Settings:
    return Settings()
