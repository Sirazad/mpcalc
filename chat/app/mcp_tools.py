"""The backend's MCP tools for Claude. The model never sees or supplies the player and character ids: they are removed from the
tool schemas and set by this service from the verified identity on every call (a model-supplied value is overwritten)."""
from __future__ import annotations

import copy
import logging
import time
from dataclasses import dataclass
from typing import Any, Protocol

import httpx2
from mcp import Client
from mcp.client.streamable_http import streamable_http_client

log = logging.getLogger("chat.mcp")

HIDDEN = ("playerId", "characterId")


def to_claude_tool(tool: Any) -> dict[str, Any]:
    """An MCP tool as a Claude tool definition, without the ids the service sets itself."""
    schema = copy.deepcopy(tool.input_schema if hasattr(tool, "input_schema") else tool.inputSchema)
    props = schema.get("properties", {})
    for name in HIDDEN:
        props.pop(name, None)
    if "required" in schema:
        schema["required"] = [r for r in schema["required"] if r not in HIDDEN]
        if not schema["required"]:
            del schema["required"]
    return {"name": tool.name, "description": tool.description or "", "input_schema": schema}


def with_identity(arguments: dict[str, Any] | None, player_id: int, character_id: int) -> dict[str, Any]:
    """The arguments of a tool call with the verified ids; whatever the model put there for them is overwritten."""
    out = dict(arguments or {})
    out["playerId"] = player_id
    out["characterId"] = character_id
    return out


def result_text(result: Any, limit: int) -> tuple[str, bool]:
    """The text of a tool result and whether it is an error; a too long result is cut with a note for the model."""
    parts = [c.text for c in (result.content or []) if getattr(c, "type", None) == "text"]
    text = "\n".join(parts)
    if len(text) > limit:
        text = text[:limit] + "\n[The result was cut because it is too long. Ask again with a filter, for example by school or mosaic ids.]"
    return text, bool(getattr(result, "is_error", False) or getattr(result, "isError", False))


class ToolSession(Protocol):
    """What the agent needs from the tool side (a fake one is used in tests)."""
    async def tools(self) -> list[dict[str, Any]]: ...
    async def call(self, name: str, arguments: dict[str, Any], player_id: int, character_id: int) -> tuple[str, bool]: ...
    async def search_notes(self, question: str, player_id: int, character_id: int) -> tuple[str, bool]: ...


@dataclass
class _Cache:
    tools: list[dict[str, Any]] | None = None
    at: float = 0.0


class McpToolSession:
    """One question's connection to the backend's MCP server (stateless server: nothing is kept between questions)."""

    def __init__(self, url: str, api_key: str, tool_result_limit: int, ttl_seconds: int, cache: _Cache, language: str = "hu"):
        self._url, self._key, self._limit, self._ttl, self._cache, self._lang = url, api_key, tool_result_limit, ttl_seconds, cache, language
        self._http: httpx2.AsyncClient | None = None
        self._client: Client | None = None

    async def __aenter__(self) -> "McpToolSession":
        self._http = httpx2.AsyncClient(headers={"X-API-Key": self._key, "Accept-Language": self._lang}, timeout=httpx2.Timeout(30.0))
        self._client = Client(streamable_http_client(self._url, http_client=self._http), mode="legacy")
        await self._client.__aenter__()
        return self

    async def __aexit__(self, *exc: object) -> None:
        try:
            if self._client is not None:
                await self._client.__aexit__(*exc)  # type: ignore[arg-type]
        finally:
            if self._http is not None:
                await self._http.aclose()

    async def tools(self) -> list[dict[str, Any]]:
        if self._cache.tools is None or time.monotonic() - self._cache.at > self._ttl:
            listed = await self._client.list_tools()  # type: ignore[union-attr]
            self._cache.tools = [to_claude_tool(t) for t in listed.tools]
            self._cache.at = time.monotonic()
        return self._cache.tools

    async def call(self, name: str, arguments: dict[str, Any], player_id: int, character_id: int) -> tuple[str, bool]:
        result = await self._client.call_tool(name, with_identity(arguments, player_id, character_id))  # type: ignore[union-attr]
        return result_text(result, self._limit)

    async def search_notes(self, question: str, player_id: int, character_id: int) -> tuple[str, bool]:
        """The search the service makes for a question before the model answers (the find_notes tool, called by the service, not the model)."""
        return await self.call("find_notes", {"query": question}, player_id, character_id)


class McpToolFactory:
    """Opens a tool session per question; the converted tool list is cached across questions."""

    def __init__(self, base_url: str, api_key: str, tool_result_limit: int, ttl_seconds: int):
        self._url = f"{base_url.rstrip('/')}/api/mcp"
        self._key, self._limit, self._ttl = api_key, tool_result_limit, ttl_seconds
        self._cache = _Cache()

    def open(self, language: str = "hu") -> McpToolSession:
        return McpToolSession(self._url, self._key, self._limit, self._ttl, self._cache, language)
