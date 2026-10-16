from types import SimpleNamespace

from app.mcp_tools import result_text, to_claude_tool, with_identity


def tool(schema):
    return SimpleNamespace(name="find_best_spell", description="Search", input_schema=schema)


def test_the_ids_are_removed_from_what_the_model_sees():
    schema = {"type": "object", "properties": {"playerId": {"type": "integer"}, "characterId": {"type": "integer"}, "objective": {"type": "string"}},
              "required": ["playerId", "characterId", "objective"]}
    out = to_claude_tool(tool(schema))
    assert set(out["input_schema"]["properties"]) == {"objective"}
    assert out["input_schema"]["required"] == ["objective"]
    assert out["name"] == "find_best_spell" and out["description"] == "Search"
    assert "playerId" in schema["properties"], "the original schema is not changed"


def test_required_is_dropped_when_only_the_ids_were_required():
    out = to_claude_tool(tool({"type": "object", "properties": {"playerId": {}, "characterId": {}}, "required": ["playerId", "characterId"]}))
    assert "required" not in out["input_schema"]


def test_a_model_supplied_id_is_overwritten_by_the_verified_one():
    sent = with_identity({"playerId": 999, "characterId": 888, "objective": "MAX_E"}, player_id=7, character_id=9)
    assert sent == {"playerId": 7, "characterId": 9, "objective": "MAX_E"}
    assert with_identity(None, 7, 9) == {"playerId": 7, "characterId": 9}


def test_result_text_joins_text_and_flags_errors():
    r = SimpleNamespace(content=[SimpleNamespace(type="text", text="a"), SimpleNamespace(type="image", text="x"), SimpleNamespace(type="text", text="b")], is_error=False)
    assert result_text(r, 100) == ("a\nb", False)
    assert result_text(SimpleNamespace(content=[SimpleNamespace(type="text", text="x")], is_error=True), 100)[1] is True


def test_a_too_long_result_is_cut_with_a_note_for_the_model():
    text, _ = result_text(SimpleNamespace(content=[SimpleNamespace(type="text", text="x" * 500)], is_error=False), 100)
    assert text.startswith("x" * 100) and "cut" in text and len(text) < 400
