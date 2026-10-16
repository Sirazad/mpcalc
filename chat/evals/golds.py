"""The ground truth, computed by the engine at run time through the backend's own tools (never by a model, never stored)."""
from __future__ import annotations

import json
from typing import Any

from .cases import Case

SECONDS = {"sz": 1, "kor": 4, "perc": 60, "ora": 3600, "nap": 86400}


class GoldError(Exception):
    """The gold could not be computed: a setup or case error, not a model failure."""


def time_seconds(tv: dict[str, Any] | None) -> int | None:
    """Seconds of an engine time value {"kind": "TIME", "amounts": {...}}; None when it has no seconds (one-off, undefined, years)."""
    if not tv or tv.get("kind") != "TIME":
        return None
    amounts = tv.get("amounts") or {}
    if any(u not in SECONDS for u in amounts):
        return None
    return sum(n * SECONDS[u] for u, n in amounts.items())


def _decimal(f: dict[str, Any] | None) -> float | None:
    if not f:
        return None
    d = f.get("decimal")
    return float(d) if d is not None else None


async def compute_gold(case: Case, tools: Any, player_id: int, character_id: int) -> dict[str, Any] | None:
    """tools: a ToolSession (not a recording one, so the gold calls do not appear in the case's trace)."""
    g = case.gold
    if g.kind in ("none", "undefined"):
        return None
    if g.kind == "calculate":
        text, is_error = await tools.call("calculate_spell", {"selections": g.selections}, player_id, character_id)
        try:
            r = json.loads(text)
        except ValueError as e:
            raise GoldError(f"{case.id}: calculate_spell returned no JSON: {text[:120]}") from e
        if is_error or "error" in r or not r.get("valid"):
            raise GoldError(f"{case.id}: the gold spell is not valid: {text[:200]}")
        first = r["mosaics"][0]
        e = first["e"]["value"] if first["e"]["defined"] else None
        return {"e": e, "mp": _decimal((r["totalMp"] or {}).get("value")), "duration_seconds": time_seconds((r["duration"] or {}).get("value"))}
    text, is_error = await tools.call("find_best_spell", dict(g.search or {}), player_id, character_id)
    try:
        r = json.loads(text)
    except ValueError as e:
        raise GoldError(f"{case.id}: find_best_spell returned no JSON: {text[:120]}") from e
    if is_error or "error" in r:
        raise GoldError(f"{case.id}: the gold search failed: {text[:200]}")
    best = r.get("best")
    if not best:
        raise GoldError(f"{case.id}: the gold search found no option (a case that expects numbers needs one)")
    return {"e": best["e"], "mp": _decimal(best["totalMp"]), "duration_seconds": best.get("durationSeconds")}
