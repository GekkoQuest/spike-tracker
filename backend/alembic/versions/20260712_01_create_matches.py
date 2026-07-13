"""Create normalized matches table."""

import sqlalchemy as sa

from alembic import op

revision = "20260712_01"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "matches",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("source_id", sa.String(32), nullable=False, unique=True),
        sa.Column("status", sa.String(16), nullable=False),
        sa.Column("team1_id", sa.String(32)),
        sa.Column("team1_name", sa.String(160), nullable=False),
        sa.Column("team1_country", sa.String(8)),
        sa.Column("team1_score", sa.Integer()),
        sa.Column("team1_logo", sa.Text()),
        sa.Column("team1_won", sa.Boolean()),
        sa.Column("team2_id", sa.String(32)),
        sa.Column("team2_name", sa.String(160), nullable=False),
        sa.Column("team2_country", sa.String(8)),
        sa.Column("team2_score", sa.Integer()),
        sa.Column("team2_logo", sa.Text()),
        sa.Column("team2_won", sa.Boolean()),
        sa.Column("event", sa.String(240)),
        sa.Column("tournament", sa.String(240)),
        sa.Column("event_logo", sa.Text()),
        sa.Column("starts_at", sa.DateTime(timezone=True)),
        sa.Column("countdown", sa.String(40)),
        sa.Column("relative_time", sa.String(40)),
        sa.Column("source_rank", sa.Integer(), nullable=False, server_default="0"),
        sa.Column(
            "first_seen_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
    )
    op.create_index("ix_matches_source_id", "matches", ["source_id"], unique=True)
    op.create_index("ix_matches_status", "matches", ["status"])
    op.create_index("ix_matches_status_starts_at", "matches", ["status", "starts_at"])
    op.create_index("ix_matches_tournament", "matches", ["tournament"])


def downgrade() -> None:
    op.drop_table("matches")
