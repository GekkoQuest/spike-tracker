from datetime import UTC, datetime, timedelta

from app.schemas import Match, Team


def demo_matches() -> list[Match]:
    now = datetime.now(UTC)
    return [
        Match(
            id="demo-901",
            status="LIVE",
            teams=(
                Team(
                    id="100",
                    name="Sentinels",
                    country="us",
                    score=1,
                    logo="https://owcdn.net/img/62875027c8e06.png",
                ),
                Team(
                    id="2593",
                    name="Paper Rex",
                    country="sg",
                    score=1,
                    logo="https://owcdn.net/img/62bbeba74d5cb.png",
                ),
            ),
            event="Playoffs · Upper Final",
            tournament="Valorant Champions Tour — Masters",
            starts_at=now - timedelta(hours=1),
        ),
        Match(
            id="demo-902",
            status="LIVE",
            teams=(
                Team(
                    name="FNATIC",
                    country="gb",
                    score=0,
                    logo="https://owcdn.net/img/62a40cc2b5e29.png",
                ),
                Team(
                    name="Gen.G",
                    country="kr",
                    score=1,
                    logo="https://owcdn.net/img/654cc858ea9f5.png",
                ),
            ),
            event="Group Stage · Round 3",
            tournament="VCT 2026 — Global Series",
            starts_at=now - timedelta(minutes=38),
        ),
        Match(
            id="demo-903",
            status="UPCOMING",
            teams=(
                Team(name="Wolves Esports", country="cn"),
                Team(name="Team Heretics", country="es"),
            ),
            event="Playoffs · Lower Semifinal",
            tournament="Valorant Champions Tour — Masters",
            starts_at=now + timedelta(hours=2, minutes=20),
            countdown="2h 20m",
        ),
    ]
