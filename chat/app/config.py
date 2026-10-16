"""Settings of the chat service, all from environment variables (compose passes them)."""
from __future__ import annotations

import os
from dataclasses import dataclass


def _int(name: str, default: int) -> int:
    raw = os.environ.get(name, "")
    return int(raw) if raw.strip() else default


@dataclass(frozen=True)
class Settings:
    backend_url: str = "http://localhost:8080"
    mcp_api_key: str = ""
    # Claude Haiku 5.5 (user decision 2026-10-14); another model is one setting away.
    model: str = "claude-haiku-5-5"
    max_tokens: int = 16000
    # at most this many model turns for one question (a turn that calls tools, and the answer after it)
    max_model_turns: int = 8
    rate_questions: int = 10
    rate_window_seconds: int = 300
    rate_concurrent: int = 2
    max_history_turns: int = 20
    max_message_chars: int = 2000
    max_history_chars: int = 20000
    tool_result_max_chars: int = 30000
    tool_list_ttl_seconds: int = 300

    @staticmethod
    def from_env() -> "Settings":
        d = Settings()
        return Settings(
            backend_url=os.environ.get("BACKEND_URL", d.backend_url).rstrip("/"),
            mcp_api_key=os.environ.get("MCP_API_KEY", d.mcp_api_key),
            model=os.environ.get("CHAT_MODEL", d.model),
            max_tokens=_int("CHAT_MAX_TOKENS", d.max_tokens),
            max_model_turns=_int("CHAT_MAX_MODEL_TURNS", d.max_model_turns),
            rate_questions=_int("CHAT_RATE_QUESTIONS", d.rate_questions),
            rate_window_seconds=_int("CHAT_RATE_WINDOW_SECONDS", d.rate_window_seconds),
            rate_concurrent=_int("CHAT_RATE_CONCURRENT", d.rate_concurrent),
            max_history_turns=_int("CHAT_MAX_HISTORY_TURNS", d.max_history_turns),
            max_message_chars=_int("CHAT_MAX_MESSAGE_CHARS", d.max_message_chars),
            max_history_chars=_int("CHAT_MAX_HISTORY_CHARS", d.max_history_chars),
            tool_result_max_chars=_int("CHAT_TOOL_RESULT_MAX_CHARS", d.tool_result_max_chars),
            tool_list_ttl_seconds=_int("CHAT_TOOL_LIST_TTL_SECONDS", d.tool_list_ttl_seconds),
        )
