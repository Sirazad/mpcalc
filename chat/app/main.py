"""The chat service: POST /chat/ask streams the answer as server-sent events. nginx passes /chat/... to this service unchanged."""
from __future__ import annotations

import json
import logging
import os
from contextlib import asynccontextmanager
from typing import Any, AsyncIterator, Callable

import anthropic
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

from . import messages
from .agent import Agent, Event
from .backend import BackendClient, BackendUnavailable, NotLoggedIn, NotYourCharacter
from .config import Settings
from .mcp_tools import McpToolFactory
from .ratelimit import RateLimiter

log = logging.getLogger("chat")


class Turn(BaseModel):
    role: str
    text: str


class Ask(BaseModel):
    characterId: int
    message: str = ""
    history: list[Turn] = Field(default_factory=list)


def validate_history(history: list[Turn], settings: Settings) -> list[dict[str, str]] | None:
    """Only plain text turns, alternating user/assistant starting with the user, within the limits. Anything else is refused,
    so a page (or someone pretending to be it) can never inject tool calls or system text into the conversation."""
    if len(history) > settings.max_history_turns:
        return None
    total = 0
    expected = "user"
    out: list[dict[str, str]] = []
    for t in history:
        if t.role != expected or not t.text.strip():
            return None
        total += len(t.text)
        if total > settings.max_history_chars:
            return None
        out.append({"role": t.role, "text": t.text})
        expected = "assistant" if expected == "user" else "user"
    if out and out[-1]["role"] != "assistant":
        return None
    return out


def sse(event: Event) -> str:
    data: dict[str, Any] = {"text": event.text} if event.kind in ("status", "text") else {}
    if event.kind == "error":
        data = {"text": event.text, "code": event.code}
    return f"event: {event.kind}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def create_app(settings: Settings | None = None, backend: Any = None, anthropic_client: Any = None,
               tool_factory: Any = None, clock: Callable[[], float] | None = None) -> FastAPI:
    """Everything that talks to the outside can be replaced (the tests do): backend client, Claude client, tool factory."""
    s = settings or Settings.from_env()
    state: dict[str, Any] = {}

    @asynccontextmanager
    async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
        state["backend"] = backend or BackendClient(s.backend_url)
        state["factory"] = tool_factory or McpToolFactory(s.backend_url, s.mcp_api_key, s.tool_result_max_chars, s.tool_list_ttl_seconds)
        state["agent"] = Agent(s, anthropic_client or anthropic.AsyncAnthropic())
        state["limiter"] = RateLimiter(s.rate_questions, s.rate_window_seconds, s.rate_concurrent, *([clock] if clock else []))
        yield
        if hasattr(state["backend"], "aclose"):
            await state["backend"].aclose()

    app = FastAPI(title="mpcalc-chat", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)

    def fail(status: int, key: str, lang: str, headers: dict[str, str] | None = None, **args: object) -> JSONResponse:
        return JSONResponse({"error": messages.text(key, lang, **args), "code": key}, status_code=status, headers=headers)

    @app.get("/chat/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.post("/chat/ask")
    async def ask(body: Ask, request: Request):
        lang = messages.pick_language(request.headers.get("accept-language"))
        question = body.message.strip()
        if not question:
            return fail(400, "message_required", lang)
        if len(question) > s.max_message_chars:
            return fail(400, "message_too_long", lang, limit=s.max_message_chars)
        history = validate_history(body.history, s)
        if history is None:
            return fail(400, "history_invalid", lang)
        try:
            who = await state["backend"].identify(request.headers.get("cookie"), body.characterId)
        except NotLoggedIn:
            return fail(401, "not_logged_in", lang)
        except NotYourCharacter:
            return fail(404, "not_your_character", lang)
        except BackendUnavailable:
            return fail(503, "backend_unavailable", lang)
        verdict = state["limiter"].acquire(who.player_id)
        if not verdict.ok:
            if verdict.reason == "busy":
                return fail(429, "busy", lang, headers={"Retry-After": "1"})
            return fail(429, "rate_limited", lang, headers={"Retry-After": str(verdict.retry_after_seconds)}, seconds=verdict.retry_after_seconds)

        async def events() -> AsyncIterator[str]:
            try:
                async with state["factory"].open(lang) as tools:
                    async for ev in state["agent"].answer(history, question, tools, who.player_id, who.character_id, lang):
                        yield sse(ev)
            except Exception:
                log.exception("chat question failed")
                yield sse(Event("error", messages.text("tools_unavailable", lang), "tools_unavailable"))
            finally:
                state["limiter"].release(who.player_id)

        return StreamingResponse(events(), media_type="text/event-stream",
                                 headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})

    return app


logging.basicConfig(level=os.environ.get("CHAT_LOG_LEVEL", "INFO"))
app = create_app()
