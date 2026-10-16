"""Turns run directories into report.md: pass rates with intervals per area, attack and over-refusal rates, the ask-back confusion matrix,
absolute cost and latency, errors by class, and the failures with their notes. Several run directories give a comparison table.
Markdown on purpose: case text comes from tickets-like input and answers of a model, so it is never turned into HTML here."""
from __future__ import annotations

import argparse
import json
import math
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


def wilson(k: int, n: int, z: float = 1.96) -> tuple[float, float]:
    if n == 0:
        return 0.0, 0.0
    p = k / n
    d = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / d
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
    return max(0.0, centre - half), min(1.0, centre + half)


def pct(k: int, n: int) -> str:
    if n == 0:
        return "n/a"
    lo, hi = wilson(k, n)
    return f"{100 * k / n:.0f}% ({k}/{n}, 95% {100 * lo:.0f}-{100 * hi:.0f}%)"


def load_run(path: Path) -> dict[str, Any]:
    def jl(name: str) -> list[dict[str, Any]]:
        p = path / name
        return [json.loads(x) for x in p.read_text(encoding="utf-8").splitlines() if x.strip()] if p.exists() else []
    state = json.loads((path / "_state.json").read_text(encoding="utf-8")) if (path / "_state.json").exists() else {}
    return {"dir": path, "rows": jl("results.jsonl"), "errors": jl("errors.jsonl"), "state": state}


def counted(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Truncated answers are shown but not averaged in as wrong."""
    return [r for r in rows if r["status"] == "ok"]


def row_cost(r: dict[str, Any]) -> float:
    return float(r["meta"].get("cost_usd", 0.0))


def confusion(rows: list[dict[str, Any]]) -> dict[str, int]:
    tp = fn = fp = tn = 0
    for r in counted(rows):
        expected = r["meta"].get("expect_asks_back")
        if expected is None or "asks_back_ok" not in r["grade"]:
            continue
        asked = expected if r["grade"]["asks_back_ok"] else not expected
        if expected and asked:
            tp += 1
        elif expected:
            fn += 1
        elif asked:
            fp += 1
        else:
            tn += 1
    return {"tp": tp, "fn": fn, "fp": fp, "tn": tn}


def summarize(run: dict[str, Any]) -> dict[str, Any]:
    rows = run["rows"]
    ok = counted(rows)
    by_area: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for r in ok:
        by_area[r["tags"][0]].append(r)
    attack = [r for r in ok if r["meta"].get("attack")]
    benign = [r for r in ok if r["meta"].get("benign_near_miss")]
    return {
        "rows": len(rows), "ok": len(ok), "truncated": len(rows) - len(ok),
        "passed": sum(r["grade"]["passed"] for r in ok),
        "areas": {a: (sum(r["grade"]["passed"] for r in rs), len(rs)) for a, rs in sorted(by_area.items())},
        "attack_succeeded": (sum(not r["grade"]["passed"] for r in attack), len(attack)),
        "over_refusal": (sum(not r["grade"]["passed"] for r in benign), len(benign)),
        "confusion": confusion(rows),
        "cost_total": sum(row_cost(r) for r in rows) + sum(float(e.get("cost_usd", 0)) for e in run["errors"]),
        "cost_per_row": (sum(row_cost(r) for r in rows) / len(rows)) if rows else 0.0,
        "latency": (sum(r["latency_s"] for r in rows) / len(rows)) if rows else 0.0,
        "tool_calls": (sum(r["tool_calls"] for r in rows) / len(rows)) if rows else 0.0,
        "tokens": (sum(r["usage"]["input_tokens"] + r["usage"]["output_tokens"] for r in rows) / len(rows)) if rows else 0.0,
        "refusals": sum(r["refusal"] for r in rows),
        "errors": Counter(e["failure_class"] for e in run["errors"]),
        "metrics": _metric_rates(ok),
    }


def _metric_rates(rows: list[dict[str, Any]]) -> dict[str, tuple[int, int]]:
    seen: dict[str, list[bool]] = defaultdict(list)
    for r in rows:
        for m, v in r["grade"].items():
            if m != "passed":
                seen[m].append(bool(v))
    return {m: (sum(v), len(v)) for m, v in sorted(seen.items())}


def noise_floor(rows: int) -> float:
    """About 1/sqrt(n) for a pass rate (n = case-repetitions): a difference smaller than this is not a result."""
    return 1 / math.sqrt(rows) if rows else 1.0


def render_one(run: dict[str, Any]) -> str:
    s = summarize(run)
    name = run["dir"].name
    st = run["state"]
    out = [f"# Chat eval: {name}", "",
           f"Model: `{st.get('model', '?')}`, judge: `{st.get('judge_model', '?')}`. Case-repetitions scored: {s['ok']}"
           f" (truncated, not averaged: {s['truncated']}; errors, never scored: {sum(s['errors'].values())}).", "",
           f"**Overall pass rate: {pct(s['passed'], s['ok'])}.** Noise floor about +/-{100 * noise_floor(s['ok']):.0f} points: smaller differences between runs are not results.", "",
           "## Per area", "", "| area | pass rate |", "|---|---|"]
    out += [f"| {a} | {pct(k, n)} |" for a, (k, n) in s["areas"].items()]
    a_ok, a_n = s["attack_succeeded"]
    b_bad, b_n = s["over_refusal"]
    out += ["", "## Safety", "",
            f"- Attack success rate (lower is better): {pct(a_ok, a_n)}",
            f"- Over-refusal on benign near-miss questions (lower is better): {pct(b_bad, b_n)}",
            f"- Refusals by the model: {s['refusals']}"]
    c = s["confusion"]
    if sum(c.values()):
        prec = c["tp"] / (c["tp"] + c["fp"]) if c["tp"] + c["fp"] else float("nan")
        rec = c["tp"] / (c["tp"] + c["fn"]) if c["tp"] + c["fn"] else float("nan")
        spec = c["tn"] / (c["tn"] + c["fp"]) if c["tn"] + c["fp"] else float("nan")
        out += ["", "## Asks back when it should, and only then", "",
                f"| | asked back | answered |", "|---|---|---|",
                f"| should ask back | {c['tp']} | {c['fn']} |", f"| should answer | {c['fp']} | {c['tn']} |", "",
                f"Precision {prec:.2f}, recall {rec:.2f}, specificity {spec:.2f}."]
    out += ["", "## Per check", "", "| check | passed |", "|---|---|"]
    out += [f"| {m} | {pct(k, n)} |" for m, (k, n) in s["metrics"].items()]
    out += ["", "## Cost and speed (absolute)", "",
            f"- Measured spend of this run: **${s['cost_total']:.4f}** (answers and judge, failed attempts included).",
            f"- Per case-repetition: ${s['cost_per_row']:.5f}, {s['latency']:.1f} s, {s['tokens']:.0f} tokens, {s['tool_calls']:.1f} tool calls."]
    if s["errors"]:
        out += ["", "## Errors (never scored)", ""] + [f"- {k}: {v}" for k, v in sorted(s["errors"].items())]
    failed = [r for r in run["rows"] if r["status"] == "ok" and not r["grade"]["passed"]]
    if failed:
        out += ["", "## Failures", ""]
        for r in failed:
            notes = "; ".join(f"{k}: {v}" for k, v in r["explanation"].items()) or "no note"
            out.append(f"- `{r['prompt_id']}` rep {r['rep']} ({', '.join(r['tags'])}): {notes} -> `traces/{r['prompt_id']}_rep{r['rep']}.json`")
    return "\n".join(out) + "\n"


def render_compare(runs: list[dict[str, Any]]) -> str:
    sums = [summarize(r) for r in runs]
    areas = sorted({a for s in sums for a in s["areas"]})
    head = "| run | model | overall | " + " | ".join(areas) + " | attack success | $/case-rep | s/case-rep | total $ |"
    sep = "|" + "---|" * (7 + len(areas))
    lines = ["# Chat eval comparison", "", head, sep]
    for r, s in zip(runs, sums):
        a, n = s["attack_succeeded"]
        lines.append(f"| {r['dir'].name} | {r['state'].get('model', '?')} | {pct(s['passed'], s['ok'])} | "
                     + " | ".join(pct(*s["areas"].get(x, (0, 0))) for x in areas)
                     + f" | {pct(a, n)} | {s['cost_per_row']:.5f} | {s['latency']:.1f} | {s['cost_total']:.3f} |")
    floor = max(noise_floor(s["ok"]) for s in sums)
    lines += ["", f"Noise floor about +/-{100 * floor:.0f} points: an overall difference below that is not a result.", ""]
    best, worst = max(sums, key=lambda s: s["passed"] / max(s["ok"], 1)), min(sums, key=lambda s: s["passed"] / max(s["ok"], 1))
    gap = best["passed"] / max(best["ok"], 1) - worst["passed"] / max(worst["ok"], 1)
    lines.append(f"Largest overall gap: {100 * gap:.0f} points, which is {'ABOVE' if gap > floor else 'within'} the noise floor.")
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("runs", nargs="+")
    a = ap.parse_args(argv)
    runs = [load_run(Path(p)) for p in a.runs]
    for r in runs:
        (r["dir"] / "report.md").write_text(render_one(r), encoding="utf-8")
        print(f"wrote {r['dir'] / 'report.md'}")
    if len(runs) > 1:
        out = runs[0]["dir"].parent / "comparison.md"
        out.write_text(render_compare(runs), encoding="utf-8")
        print(f"wrote {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
