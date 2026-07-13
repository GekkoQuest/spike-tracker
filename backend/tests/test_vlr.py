import pytest

from app.services.vlr import VlrClient


def test_match_parser_normalizes_upstream_values() -> None:
    raw: dict[str, object] = {
        "id": 702209,
        "status": "live",
        "teams": [
            {"id": 1, "name": "Alpha", "country": "US", "score": "1", "won": True},
            {"id": 2, "name": "Bravo", "country": "CA", "score": 0, "won": "false"},
        ],
        "event": "Playoffs",
        "tournament": "Challengers",
        "utc": "2026-07-13T02:00:00Z",
        "in": 5,
    }

    match = VlrClient._parse_match(raw, "UPCOMING")

    assert match.id == "702209"
    assert match.status == "LIVE"
    assert match.teams[0].score == 1
    assert match.teams[0].won is True
    assert match.teams[1].won is None
    assert match.countdown == "5"
    assert match.starts_at is not None


def test_mapping_parser_rejects_non_string_keys() -> None:
    raw: dict[object, object] = {"id": 1, 2: "invalid"}

    with pytest.raises(TypeError, match="string keys"):
        VlrClient._as_mapping(raw)
