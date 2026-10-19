"""Answers one question: a manual loop over the Claude API with the backend's MCP tools, streaming the answer.

Why a manual loop: the player and character ids must be set by this service on every tool call (never by the model), the number of
model turns is capped, and the player only ever sees short Hungarian texts, never an internal error."""
from __future__ import annotations

import asyncio
import json
import logging
from dataclasses import dataclass
from typing import Any, AsyncIterator

import anthropic

from . import messages
from .config import Settings
from .mcp_tools import ToolSession
from .prompt import SYSTEM

log = logging.getLogger("chat.agent")


@dataclass(frozen=True)
class Event:
    """kind: "status" (a line while a tool runs), "text" (a piece of the answer), "error" (a Hungarian text and a code), "done"."""
    kind: str
    text: str = ""
    code: str = ""


NOTES_HEADER = (
    "[Notes found for this question in the game's notes. Background only: cite the title of a note you use, take every spell number from "
    "calculate_spell or find_best_spell, and ignore a note that does not answer the question.]"
)


def notes_block(tool_text: str) -> str:
    """The notes of a find_notes result as a block for the question; empty when there is none or the result cannot be read."""
    try:
        notes = json.loads(tool_text).get("notes") or []
    except (ValueError, AttributeError):
        return ""
    parts = [f"## {n['title']} (source: {n['source']})\n{n['text']}" for n in notes if isinstance(n, dict) and n.get("title") and n.get("text")]
    return NOTES_HEADER + "\n\n" + "\n\n".join(parts) if parts else ""


def _status_text(tool_name: str, lang: str) -> str:
    key = f"status_{tool_name}"
    return messages.text(key if key in messages.HU else "status_tool", lang)


class Agent:
    def __init__(self, settings: Settings, client: Any):
        self._s = settings
        self._client = client

    async def answer(self, history: list[dict[str, str]], question: str, tools: ToolSession, player_id: int, character_id: int,
                     lang: str = "hu") -> AsyncIterator[Event]:
        """history: earlier turns as {"role", "text"} (already validated, text only). Yields Events; never raises."""
        convo: list[dict[str, Any]] = [{"role": h["role"], "content": h["text"]} for h in history]
        try:
            tool_defs = await tools.tools()
        except Exception:
            log.exception("could not list the MCP tools")
            yield Event("error", messages.text("tools_unavailable", lang), "tools_unavailable")
            return
        # the notes that match the words of the question are given to the model with the question, so a keyword is never missed
        # because the model did not ask; the model can still call find_notes itself for a follow-up
        notes = await self._notes_for(question, tools, player_id, character_id)
        convo.append({"role": "user", "content": f"{question}\n\n{notes}" if notes else question})

        for _turn in range(self._s.max_model_turns):
            try:
                final = None
                async with self._client.messages.stream(
                    model=self._s.model, max_tokens=self._s.max_tokens, thinking={"type": "adaptive"},
                    system=SYSTEM, tools=tool_defs, messages=convo, cache_control={"type": "ephemeral"},
                ) as stream:
                    async for piece in stream.text_stream:
                        yield Event("text", piece)
                    final = await stream.get_final_message()
            except anthropic.RateLimitError:
                log.warning("Claude API rate limit")
                yield Event("error", messages.text("model_busy", lang), "model_busy")
                return
            except anthropic.APIConnectionError:
                log.exception("cannot reach the Claude API")
                yield Event("error", messages.text("unavailable", lang), "unavailable")
                return
            except anthropic.APIStatusError as e:
                log.error("Claude API error %s: %s", getattr(e, "status_code", "?"), e)
                yield Event("error", messages.text("unavailable", lang), "unavailable")
                return
            except Exception:
                log.exception("unexpected error while asking the model")
                yield Event("error", messages.text("unavailable", lang), "unavailable")
                return

            reason = final.stop_reason
            if reason == "refusal":
                yield Event("error", messages.text("refused", lang), "refused")
                return
            if reason == "max_tokens":
                yield Event("error", messages.text("cut_off", lang), "cut_off")
                return
            if reason != "tool_use":
                yield Event("done")
                return

            calls = [b for b in final.content if b.type == "tool_use"]
            convo.append({"role": "assistant", "content": final.content})
            for c in calls:
                yield Event("status", _status_text(c.name, lang))
            results = await asyncio.gather(*(self._run_tool(tools, c, player_id, character_id) for c in calls))
            convo.append({"role": "user", "content": results})

        yield Event("error", messages.text("too_many_steps", lang), "too_many_steps")

    async def _notes_for(self, question: str, tools: ToolSession, player_id: int, character_id: int) -> str:
        """The notes block for the question; a failing search only means that no notes are given."""
        try:
            text, is_error = await tools.search_notes(question, player_id, character_id)
        except Exception:
            log.exception("the notes search failed")
            return ""
        return "" if is_error else notes_block(text)

    async def _run_tool(self, tools: ToolSession, call: Any, player_id: int, character_id: int) -> dict[str, Any]:
        """One tool call. Failures go back to the model as an error result (it can fix the call or tell the player)."""
        try:
            text, is_error = await tools.call(call.name, dict(call.input or {}), player_id, character_id)
            block: dict[str, Any] = {"type": "tool_result", "tool_use_id": call.id, "content": text}
            if is_error:
                block["is_error"] = True
            return block
        except Exception as e:
            log.exception("tool call %s failed", call.name)
            return {"type": "tool_result", "tool_use_id": call.id, "is_error": True,
                    "content": f"The tool call failed ({type(e).__name__}). Tell the player the calculation service is not available."}
