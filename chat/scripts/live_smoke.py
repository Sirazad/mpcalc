"""Live smoke test of the chat with the REAL Claude API (it spends a few cents). Run it only with a real ANTHROPIC_API_KEY.

  docker-compose exec chat python scripts/live_smoke.py            (stack running, real key in infra/.env, chat recreated)
  BACKEND_URL=http://localhost:8080 MCP_API_KEY=... ANTHROPIC_API_KEY=... python scripts/live_smoke.py   (local backend)

It creates a test player and character through the backend, asks three questions through the real agent (real model, real MCP tools)
and prints the answers and the tool calls, so a person can judge them. Exit code 0 only when the strongest-mental-write question was answered
with the engine's numbers (E 75, 100 MP); the other two are for reading (does the model ask back? does it say the delay is undefined?)."""
from __future__ import annotations

import asyncio
import os
import random
import sys

import anthropic
import httpx2

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from app.agent import Agent  # noqa: E402
from app.config import Settings  # noqa: E402
from app.mcp_tools import McpToolFactory  # noqa: E402

QUESTIONS = [
    ("strongest mental write for an hour (must contain the engine's numbers)", "Mi a legerősebb mentál írás legalább egy órára? Az egész MP-met felhasználhatom."),
    ("unclear question (the model should ask back, not guess)", "Mi a legjobb varázslat?"),
    ("delay (the data does not define it: the model should say so)", "Ha legalább E 10-es mentál írást akarok, mennyi ideig halaszthatom el a hatását?"),
]


async def main() -> int:
    if not os.environ.get("ANTHROPIC_API_KEY") or os.environ["ANTHROPIC_API_KEY"].startswith("placeholder"):
        print("No real ANTHROPIC_API_KEY: nothing was run.")
        return 2
    s = Settings.from_env()
    async with httpx2.AsyncClient(base_url=s.backend_url, timeout=15.0) as http:
        name = f"smoke-{random.randint(0, 10**6)}"
        await http.post("/api/players", json={"name": name})
        login = await http.post("/api/session", json={"name": name})
        player_id = login.json()["id"]
        ch = await http.post("/api/characters", json={"name": "Író", "maxMp": 100, "tiers": {"mental": 3, "ido": 2}})
        character_id = ch.json()["id"]
    agent = Agent(s, anthropic.AsyncAnthropic())
    factory = McpToolFactory(s.backend_url, s.mcp_api_key, s.tool_result_max_chars, s.tool_list_ttl_seconds)
    ok = True
    for label, question in QUESTIONS:
        print(f"\n=== {label}\nQ: {question}")
        answer, statuses, error = "", [], None
        async with factory.open("hu") as tools:
            async for ev in agent.answer([], question, tools, player_id, character_id, "hu"):
                if ev.kind == "text":
                    answer += ev.text
                elif ev.kind == "status":
                    statuses.append(ev.text)
                elif ev.kind == "error":
                    error = f"{ev.code}: {ev.text}"
        print("tools:", statuses or "none")
        print("A:", answer.strip() or "(no text)")
        if error:
            print("ERROR:", error)
            ok = ok and label != QUESTIONS[0][0]
        if label == QUESTIONS[0][0]:
            ok = ok and error is None and "75" in answer and "100" in answer
    print("\nRESULT:", "OK" if ok else "FAILED (the first question must contain E 75 and 100 MP)")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
