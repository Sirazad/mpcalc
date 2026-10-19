"""A scripted fake of the Claude client and of the tool side, so the chat loop is tested without any key or network."""
from __future__ import annotations

from types import SimpleNamespace
from typing import Any


def text_block(text: str) -> Any:
    return SimpleNamespace(type="text", text=text)


def tool_use(id: str, name: str, **input: Any) -> Any:
    return SimpleNamespace(type="tool_use", id=id, name=name, input=input)


class Turn:
    """One scripted model turn: pieces streamed as text, the final blocks, the stop reason (or an exception to raise)."""
    def __init__(self, pieces: list[str] | None = None, content: list[Any] | None = None, stop_reason: str = "end_turn", raises: Exception | None = None,
                 model: str = "claude-haiku-5-5", usage: tuple[int, int] = (1000, 100), delay: float = 0.0):
        self.pieces = pieces or []
        self.content = content if content is not None else [text_block("".join(self.pieces))]
        self.stop_reason = stop_reason
        self.raises = raises
        self.model = model
        self.usage = SimpleNamespace(input_tokens=usage[0], output_tokens=usage[1], cache_creation_input_tokens=0, cache_read_input_tokens=0)
        self.delay = delay


class _Stream:
    def __init__(self, turn: Turn):
        self._turn = turn

    async def __aenter__(self) -> "_Stream":
        if self._turn.delay:
            import asyncio
            await asyncio.sleep(self._turn.delay)
        if self._turn.raises is not None:
            raise self._turn.raises
        return self

    async def __aexit__(self, *exc: object) -> None:
        return None

    @property
    def text_stream(self):
        async def gen():
            for p in self._turn.pieces:
                yield p
        return gen()

    async def get_final_message(self) -> Any:
        return SimpleNamespace(content=self._turn.content, stop_reason=self._turn.stop_reason, model=self._turn.model, usage=self._turn.usage)


class FakeClaude:
    """Looks like AsyncAnthropic for the one call the agent makes: client.messages.stream(**kwargs)."""
    def __init__(self, turns: list[Turn]):
        self._turns = list(turns)
        self.calls: list[dict[str, Any]] = []
        self.messages = SimpleNamespace(stream=self._stream)

    def _stream(self, **kwargs: Any) -> _Stream:
        # keep a copy of the conversation as it was at this call (the agent keeps appending to its list)
        snapshot = dict(kwargs)
        snapshot["messages"] = [dict(m) for m in kwargs["messages"]]
        self.calls.append(snapshot)
        return _Stream(self._turns.pop(0) if len(self._turns) > 1 else self._turns[0])


class FakeTools:
    """The tool side: a fixed tool list and scripted results; records every call with the ids the service set."""
    def __init__(self, results: dict[str, tuple[str, bool]] | None = None, tools_error: Exception | None = None, call_error: Exception | None = None,
                 notes: tuple[str, bool] = ('{"notes": []}', False), notes_error: Exception | None = None):
        self.results = results or {}
        self.notes = notes
        self.notes_error = notes_error
        self.note_calls: list[tuple[dict[str, Any], int, int]] = []     # the searches the service makes for the question; not in `calls`
        self.tools_error = tools_error
        self.call_error = call_error
        self.calls: list[tuple[str, dict[str, Any], int, int]] = []

    async def tools(self) -> list[dict[str, Any]]:
        if self.tools_error:
            raise self.tools_error
        return [{"name": "calculate_spell", "description": "d", "input_schema": {"type": "object", "properties": {"selections": {}}}}]

    async def search_notes(self, question: str, player_id: int, character_id: int) -> tuple[str, bool]:
        self.note_calls.append(({"query": question}, player_id, character_id))
        if self.notes_error:
            raise self.notes_error
        return self.notes

    async def call(self, name: str, arguments: dict[str, Any], player_id: int, character_id: int) -> tuple[str, bool]:
        self.calls.append((name, arguments, player_id, character_id))
        if self.call_error:
            raise self.call_error
        return self.results.get(name, ('{"ok": true}', False))


class FakeToolFactory:
    def __init__(self, tools: FakeTools):
        self.tools = tools
        self.languages: list[str] = []

    def open(self, language: str = "hu") -> "FakeToolFactory":
        self.languages.append(language)
        return self

    async def __aenter__(self) -> FakeTools:
        return self.tools

    async def __aexit__(self, *exc: object) -> None:
        return None
