import anthropic
import httpx2

from app.agent import Agent
from app.config import Settings
from tests.fakes import FakeClaude, FakeTools, Turn, text_block, tool_use

S = Settings(max_model_turns=3)


async def run(agent, tools, history=None, question="kérdés"):
    return [e async for e in agent.answer(history or [], question, tools, player_id=7, character_id=9, lang="hu")]


def kinds(events):
    return [e.kind for e in events]


async def test_a_tool_call_then_the_answer():
    claude = FakeClaude([
        Turn(content=[text_block("Megnézem."), tool_use("t1", "calculate_spell", selections=[{"mosaicId": "alap-furkeszes"}])], stop_reason="tool_use"),
        Turn(pieces=["A ", "válasz."], stop_reason="end_turn"),
    ])
    tools = FakeTools({"calculate_spell": ('{"totalMp": 10}', False)})
    events = await run(Agent(S, claude), tools, history=[{"role": "user", "text": "szia"}, {"role": "assistant", "text": "Szia!"}])
    assert kinds(events) == ["status", "text", "text", "done"]
    assert events[0].text == "Számolok…"
    assert "".join(e.text for e in events if e.kind == "text") == "A válasz."
    # the service set the ids (the model never supplied them) and passed the tool's arguments through
    assert tools.calls == [("calculate_spell", {"selections": [{"mosaicId": "alap-furkeszes"}]}, 7, 9)]
    # the second model call carries the tool_use turn and the tool_result in one user message
    second = claude.calls[1]["messages"]
    assert second[0] == {"role": "user", "content": "szia"} and second[1] == {"role": "assistant", "content": "Szia!"}
    assert second[-1]["role"] == "user" and second[-1]["content"][0]["type"] == "tool_result"
    assert second[-1]["content"][0]["tool_use_id"] == "t1" and "is_error" not in second[-1]["content"][0]


async def test_the_request_to_claude_has_the_agreed_shape():
    claude = FakeClaude([Turn(pieces=["ok"])])
    await run(Agent(S, claude), FakeTools())
    call = claude.calls[0]
    assert call["model"] == "claude-haiku-5-5"
    assert call["thinking"] == {"type": "adaptive"}
    assert "temperature" not in call and "top_p" not in call       # the current models reject sampling parameters
    assert call["tools"][0]["name"] == "calculate_spell"
    assert "Hungarian" in call["system"]


async def test_parallel_tool_calls_go_back_in_one_user_message():
    claude = FakeClaude([
        Turn(content=[tool_use("a", "calculate_spell"), tool_use("b", "find_best_spell")], stop_reason="tool_use"),
        Turn(pieces=["kész"]),
    ])
    tools = FakeTools()
    events = await run(Agent(S, claude), tools)
    assert [c[0] for c in tools.calls] == ["calculate_spell", "find_best_spell"]
    results = claude.calls[1]["messages"][-1]["content"]
    assert [r["tool_use_id"] for r in results] == ["a", "b"]
    assert [e.text for e in events if e.kind == "status"] == ["Számolok…", "A legjobb változat keresése…"]


async def test_a_failing_tool_goes_back_as_an_error_result_and_the_loop_goes_on():
    claude = FakeClaude([Turn(content=[tool_use("a", "calculate_spell")], stop_reason="tool_use"), Turn(pieces=["nem érhető el"])])
    events = await run(Agent(S, claude), FakeTools(call_error=RuntimeError("secret detail")))
    assert kinds(events)[-1] == "done"
    sent = claude.calls[1]["messages"][-1]["content"][0]
    assert sent["is_error"] is True and "secret detail" not in sent["content"]


async def test_an_error_result_of_the_tool_is_marked():
    claude = FakeClaude([Turn(content=[tool_use("a", "calculate_spell")], stop_reason="tool_use"), Turn(pieces=["x"])])
    await run(Agent(S, claude), FakeTools({"calculate_spell": ('{"error":"baj"}', True)}))
    assert claude.calls[1]["messages"][-1]["content"][0]["is_error"] is True


async def test_too_many_model_turns_stop_with_a_hungarian_message():
    claude = FakeClaude([Turn(content=[tool_use("a", "calculate_spell")], stop_reason="tool_use")])
    events = await run(Agent(S, claude), FakeTools())
    assert len(claude.calls) == 3
    assert events[-1].kind == "error" and events[-1].code == "too_many_steps" and "túl sok lépést" in events[-1].text


async def test_refusal_and_cut_off_have_hungarian_messages():
    refused = await run(Agent(S, FakeClaude([Turn(pieces=[], stop_reason="refusal")])), FakeTools())
    assert refused[-1].code == "refused" and refused[-1].text == "Erre a kérdésre nem tudok válaszolni."
    cut = await run(Agent(S, FakeClaude([Turn(pieces=["fél"], stop_reason="max_tokens")])), FakeTools())
    assert cut[-1].code == "cut_off"


def _request():
    return httpx2.Request("POST", "https://api.anthropic.com/v1/messages")


async def test_api_failures_become_short_hungarian_messages_without_details():
    cases = [
        (anthropic.RateLimitError("limit secret", response=httpx2.Response(429, request=_request()), body=None), "model_busy"),
        (anthropic.AuthenticationError("bad key secret", response=httpx2.Response(401, request=_request()), body=None), "unavailable"),
        (anthropic.APIConnectionError(request=_request()), "unavailable"),
        (RuntimeError("boom secret"), "unavailable"),
    ]
    for exc, code in cases:
        events = await run(Agent(S, FakeClaude([Turn(raises=exc)])), FakeTools())
        assert events[-1].kind == "error" and events[-1].code == code
        assert "secret" not in events[-1].text and "key" not in events[-1].text.lower()


async def test_a_tool_list_failure_is_reported_in_hungarian():
    events = await run(Agent(S, FakeClaude([Turn(pieces=["x"])])), FakeTools(tools_error=ConnectionError("no")))
    assert [e.code for e in events] == ["tools_unavailable"]


def test_the_system_prompt_pins_the_hungarian_words_and_the_exact_mosaic_names():
    # a live check showed the model writing "mosaic" and "spell" inside Hungarian sentences and shortening mosaic names
    from app.prompt import SYSTEM
    assert "Never put English words into a Hungarian sentence" in SYSTEM
    assert "mozaik (mosaic)" in SYSTEM and "varázslat (spell)" in SYSTEM
    assert "exactly as it appears in the tool results" in SYSTEM
    assert "oppositeElement" in SYSTEM and "Ős tűz and Ős víz are opposites" in SYSTEM
    assert "elementLabel" in SYSTEM and "armorSfe" in SYSTEM
