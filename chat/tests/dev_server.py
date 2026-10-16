"""Development harness (not part of the image): the real chat service with a scripted stand-in for Claude.

The stand-in calls the real backend tool find_best_spell (with FORGED player and character ids, to show they are overwritten) and then
reports the numbers the tool returned. Everything else is real: the login check, the MCP link with the API key, the rate limit.
Run: BACKEND_URL=... MCP_API_KEY=... python tests/dev_server.py"""
from __future__ import annotations

import json
import os
import sys
from typing import Any

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import uvicorn  # noqa: E402

from app.config import Settings  # noqa: E402
from app.main import create_app  # noqa: E402
from tests.fakes import FakeClaude, Turn, tool_use  # noqa: E402


class ScriptedClaude(FakeClaude):
    def __init__(self):
        super().__init__([Turn(pieces=["?"])])

    def _stream(self, **kwargs: Any):
        results = [b for m in kwargs["messages"] if isinstance(m["content"], list) for b in m["content"] if isinstance(b, dict) and b.get("type") == "tool_result"]
        if not results:
            ask = kwargs["messages"][-1]["content"]
            args = {"playerId": 999, "characterId": 888, "candidateMosaicIds": ["mental-beszed-iras"], "objective": "MAX_E", "minDurationSeconds": 3600}
            turn = Turn(content=[tool_use("call-1", "find_best_spell", **args)], stop_reason="tool_use")
        else:
            text = results[-1]["content"]
            try:
                best = json.loads(text)["best"]
                answer = (f"Legerősebb változat: E {best['e']}, {best['totalMp']['numerator']} MP, hatás {best['durationSeconds']} mp, "
                          f"bővítés: {best['extensionId']}.")
            except Exception:
                answer = "A szolgáltatás válasza: " + text[:300]
            turn = Turn(pieces=[answer[:30], answer[30:]])
        self.calls.append({"messages": [dict(m) for m in kwargs["messages"]], "tools": [t["name"] for t in kwargs["tools"]], "tool_schemas": kwargs["tools"]})
        from tests.fakes import _Stream
        return _Stream(turn)


if __name__ == "__main__":
    app = create_app(Settings.from_env(), anthropic_client=ScriptedClaude())
    uvicorn.run(app, host="127.0.0.1", port=int(os.environ.get("PORT", "8000")), log_level="info")
