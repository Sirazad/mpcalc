"""Thin recorders around the production Claude client and tool session. They capture everything the eval needs (every model turn with
model, usage and stop reason; every tool call with its result) without changing production code, and build the trace."""
from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any


def usage_dict(u: Any) -> dict[str, int]:
    return {k: int(getattr(u, k, 0) or 0) for k in ("input_tokens", "output_tokens", "cache_creation_input_tokens", "cache_read_input_tokens")}


@dataclass
class Trace:
    """Everything that happened for one (case, rep): the conversation as JSON-able turns, the model turns, the tool calls."""
    turns: list[dict[str, Any]] = field(default_factory=list)
    model_calls: list[dict[str, Any]] = field(default_factory=list)       # model, usage, stop_reason per call
    tool_calls: list[dict[str, Any]] = field(default_factory=list)        # name, arguments, result, is_error, seconds
    note_searches: list[dict[str, Any]] = field(default_factory=list)     # the service's own notes search per question (not a tool call of the model)

    def add(self, role: str, content: str, **extra: Any) -> None:
        self.turns.append({"role": role, "content": content, **extra})

    @property
    def tool_names(self) -> list[str]:
        return [c["name"] for c in self.tool_calls]

    @property
    def tool_text(self) -> str:
        return "\n".join(str(c["result"]) for c in self.tool_calls)

    @property
    def models_served(self) -> set[str]:
        return {c["model"] for c in self.model_calls if c.get("model")}


class _RecStream:
    def __init__(self, trace: Trace, cm: Any):
        self._trace, self._cm, self._s = trace, cm, None

    async def __aenter__(self) -> "_RecStream":
        self._s = await self._cm.__aenter__()
        return self

    async def __aexit__(self, *exc: object) -> Any:
        return await self._cm.__aexit__(*exc)

    @property
    def text_stream(self) -> Any:
        return self._s.text_stream

    async def get_final_message(self) -> Any:
        final = await self._s.get_final_message()
        self._trace.model_calls.append({"model": getattr(final, "model", None), "stop_reason": final.stop_reason, "usage": usage_dict(getattr(final, "usage", None))})
        for b in final.content:
            if b.type == "text" and b.text:
                self._trace.add("assistant", b.text)
            elif b.type == "tool_use":
                self._trace.add("tool_call", _pretty(b.input), name=b.name)
        return final


def _pretty(obj: Any) -> str:
    import json
    return json.dumps(obj, ensure_ascii=False, indent=2, default=str)


class RecordingClaude:
    """Looks like the Claude client for the one call the agent makes (client.messages.stream)."""
    def __init__(self, inner: Any, trace: Trace):
        self._inner, self._trace = inner, trace
        self.messages = _Messages(self)

    def _stream(self, **kw: Any) -> _RecStream:
        return _RecStream(self._trace, self._inner.messages.stream(**kw))


class _Messages:
    def __init__(self, owner: RecordingClaude):
        self._o = owner

    def stream(self, **kw: Any) -> _RecStream:
        return self._o._stream(**kw)


class RecordingTools:
    """Looks like a ToolSession; records every call with the ids the service set, the result and the time it took."""
    def __init__(self, inner: Any, trace: Trace):
        self._inner, self._trace = inner, trace

    async def tools(self) -> list[dict[str, Any]]:
        return await self._inner.tools()

    async def search_notes(self, question: str, player_id: int, character_id: int) -> tuple[str, bool]:
        t0 = time.monotonic()
        text, is_error = await self._inner.search_notes(question, player_id, character_id)
        self._trace.note_searches.append({"question": question, "result": text, "is_error": is_error, "seconds": time.monotonic() - t0})
        return text, is_error

    async def call(self, name: str, arguments: dict[str, Any], player_id: int, character_id: int) -> tuple[str, bool]:
        t0 = time.monotonic()
        try:
            text, is_error = await self._inner.call(name, arguments, player_id, character_id)
        except Exception as e:
            self._trace.tool_calls.append({"name": name, "arguments": arguments, "result": f"EXCEPTION {type(e).__name__}", "is_error": True, "seconds": time.monotonic() - t0})
            self._trace.add("tool_result", f"EXCEPTION {type(e).__name__}")
            raise
        self._trace.tool_calls.append({"name": name, "arguments": arguments, "result": text, "is_error": is_error, "seconds": time.monotonic() - t0})
        self._trace.add("tool_result", text)
        return text, is_error
