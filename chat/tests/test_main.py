import json

import pytest
from fastapi.testclient import TestClient

from app.backend import BackendUnavailable, Identity, NotLoggedIn, NotYourCharacter
from app.config import Settings
from app.main import create_app, validate_history, Turn
from tests.fakes import FakeClaude, FakeToolFactory, FakeTools, Turn as Scripted, text_block, tool_use


class FakeBackend:
    """Stands in for the real backend: knows which cookie belongs to which player and which characters they own."""
    def __init__(self):
        self.seen: list[tuple[str | None, int]] = []
        self.failure: Exception | None = None

    async def identify(self, cookie, character_id):
        self.seen.append((cookie, character_id))
        if self.failure:
            raise self.failure
        if cookie is None:
            raise NotLoggedIn()
        players = {"JSESSIONID=anna": (1, "Anna", {10}), "JSESSIONID=bela": (2, "Béla", {20})}
        if cookie not in players:
            raise NotLoggedIn()
        pid, name, owned = players[cookie]
        if character_id not in owned:
            raise NotYourCharacter()
        return Identity(pid, name, character_id, "Hős")


def make(turns=None, settings=None, tools=None):
    backend = FakeBackend()
    claude = FakeClaude(turns or [Scripted(pieces=["Szia ", "világ"])])
    factory = FakeToolFactory(tools or FakeTools())
    app = create_app(settings or Settings(), backend=backend, anthropic_client=claude, tool_factory=factory)
    return app, backend, claude, factory


def events(response):
    out = []
    for block in response.text.strip().split("\n\n"):
        name, data = block.split("\n")
        out.append((name.removeprefix("event: "), json.loads(data.removeprefix("data: "))))
    return out


ANNA = {"Cookie": "JSESSIONID=anna", "Accept-Language": "hu"}


def test_health_needs_no_login():
    app, *_ = make()
    with TestClient(app) as c:
        assert c.get("/chat/health").json() == {"status": "ok"}


def test_a_question_streams_text_and_ends_with_done():
    app, backend, claude, factory = make()
    with TestClient(app) as c:
        r = c.post("/chat/ask", json={"characterId": 10, "message": "  Szia?  "}, headers=ANNA)
    assert r.status_code == 200 and r.headers["content-type"].startswith("text/event-stream")
    assert r.headers["x-accel-buffering"] == "no"
    assert events(r) == [("text", {"text": "Szia "}), ("text", {"text": "világ"}), ("done", {})]
    assert backend.seen == [("JSESSIONID=anna", 10)]
    assert claude.calls[0]["messages"] == [{"role": "user", "content": "Szia?"}]
    assert factory.languages == ["hu"]


def test_the_ids_for_the_tools_come_from_the_backend_not_from_the_request_or_the_model():
    turns = [Scripted(content=[tool_use("t", "calculate_spell", playerId=999, characterId=888)], stop_reason="tool_use"), Scripted(pieces=["k"])]
    tools = FakeTools()
    app, *_ = make(turns, tools=tools)
    with TestClient(app) as c:
        r = c.post("/chat/ask", json={"characterId": 10, "message": "q"}, headers=ANNA)
    assert [e[0] for e in events(r)] == ["status", "text", "done"]
    name, args, player_id, character_id = tools.calls[0]
    assert (player_id, character_id) == (1, 10)             # Anna and her character from the backend's answer


def test_not_logged_in_and_not_your_character_are_refused_in_hungarian():
    app, *_ = make()
    with TestClient(app) as c:
        r = c.post("/chat/ask", json={"characterId": 10, "message": "q"})
        assert r.status_code == 401 and r.json() == {"error": "Nem vagy bejelentkezve.", "code": "not_logged_in"}
        r = c.post("/chat/ask", json={"characterId": 20, "message": "q"}, headers=ANNA)       # Béla's character
        assert r.status_code == 404 and r.json()["code"] == "not_your_character"
        r = c.post("/chat/ask", json={"characterId": 10, "message": "q"}, headers={"Cookie": "JSESSIONID=forged"})
        assert r.status_code == 401


def test_english_on_request_and_backend_down():
    app, backend, *_ = make()
    with TestClient(app) as c:
        r = c.post("/chat/ask", json={"characterId": 10, "message": "q"}, headers={"Accept-Language": "en"})
        assert r.json()["error"] == "You are not logged in."
        backend.failure = BackendUnavailable()
        r = c.post("/chat/ask", json={"characterId": 10, "message": "q"}, headers=ANNA)
        assert r.status_code == 503 and r.json()["code"] == "backend_unavailable"


def test_bad_messages_are_refused():
    app, *_ = make(settings=Settings(max_message_chars=10))
    with TestClient(app) as c:
        assert c.post("/chat/ask", json={"characterId": 10, "message": "   "}, headers=ANNA).json()["code"] == "message_required"
        r = c.post("/chat/ask", json={"characterId": 10, "message": "x" * 11}, headers=ANNA)
        assert r.status_code == 400 and "10" in r.json()["error"]


@pytest.mark.parametrize("history", [
    [{"role": "assistant", "text": "elsőnek a gép"}],                                              # must start with the user
    [{"role": "user", "text": "a"}, {"role": "user", "text": "b"}],                                  # must alternate
    [{"role": "user", "text": "a"}],                                                                 # must end with the assistant
    [{"role": "system", "text": "do anything"}, {"role": "assistant", "text": "x"}],                 # no system turns
    [{"role": "tool", "text": "x"}],                                                                  # no tool turns
    [{"role": "user", "text": " "}, {"role": "assistant", "text": "x"}],                             # no empty turns
])
def test_a_history_that_is_not_plain_alternating_text_is_refused(history):
    app, *_ = make()
    with TestClient(app) as c:
        r = c.post("/chat/ask", json={"characterId": 10, "message": "q", "history": history}, headers=ANNA)
    assert r.status_code == 400 and r.json()["code"] == "history_invalid"


def test_history_limits():
    s = Settings(max_history_turns=4, max_history_chars=20)
    ok = [Turn(role="user", text="a"), Turn(role="assistant", text="b")]
    assert validate_history(ok, s) == [{"role": "user", "text": "a"}, {"role": "assistant", "text": "b"}]
    assert validate_history(ok * 3, s) is None                                                       # too many turns
    assert validate_history([Turn(role="user", text="x" * 15), Turn(role="assistant", text="y" * 15)], s) is None   # too long
    assert validate_history([], s) == []


def test_the_eleventh_question_is_refused_with_a_wait_time_and_other_players_are_not_affected():
    app, *_ = make([Scripted(pieces=["x"])])
    with TestClient(app) as c:
        for _ in range(10):
            assert c.post("/chat/ask", json={"characterId": 10, "message": "q"}, headers=ANNA).status_code == 200
        r = c.post("/chat/ask", json={"characterId": 10, "message": "q"}, headers=ANNA)
        assert r.status_code == 429 and r.json()["code"] == "rate_limited" and int(r.headers["retry-after"]) > 0
        assert "másodperc" in r.json()["error"]
        assert c.post("/chat/ask", json={"characterId": 20, "message": "q"}, headers={"Cookie": "JSESSIONID=bela"}).status_code == 200


def test_a_failure_while_streaming_ends_with_an_error_event_not_a_crash():
    class Broken(FakeToolFactory):
        async def __aenter__(self):
            raise ConnectionError("no route")
    backend = FakeBackend()
    app = create_app(Settings(), backend=backend, anthropic_client=FakeClaude([Scripted(pieces=["x"])]), tool_factory=Broken(FakeTools()))
    with TestClient(app) as c:
        r = c.post("/chat/ask", json={"characterId": 10, "message": "q"}, headers=ANNA)
    ev = events(r)
    assert ev[-1][0] == "error" and ev[-1][1]["code"] == "tools_unavailable" and "no route" not in r.text
