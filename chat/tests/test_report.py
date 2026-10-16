import json

from evals.report import confusion, load_run, noise_floor, pct, render_compare, render_one, summarize, wilson


def row(id, area, passed, cost=0.001, status="ok", **meta):
    return {"prompt_id": id, "rep": 0, "prompt": "q", "tags": [area], "status": status, "stop_reason": "end_turn",
            "grade": {"passed": passed, "numbers_ok": passed}, "explanation": {} if passed else {"numbers_ok": "answer lacks E 75"},
            "answer": "a", "model": "claude-haiku-5-5", "usage": {"input_tokens": 1000, "output_tokens": 100}, "latency_s": 2.0, "tool_calls": 1,
            "refusal": False, "retries": 0, "meta": {"cost_usd": cost, **meta}}


def write_run(tmp_path, name, rows, errors=()):
    d = tmp_path / name
    d.mkdir()
    (d / "results.jsonl").write_text("\n".join(json.dumps(r) for r in rows) + "\n", encoding="utf-8")
    (d / "errors.jsonl").write_text("\n".join(json.dumps(e) for e in errors) + ("\n" if errors else ""), encoding="utf-8")
    (d / "_state.json").write_text(json.dumps({"model": name, "judge_model": "claude-sonnet-5-5"}), encoding="utf-8")
    return load_run(d)


def test_wilson_interval_is_sane():
    lo, hi = wilson(8, 10)
    assert 0.48 < lo < 0.50 and 0.94 < hi < 0.96
    assert wilson(0, 0) == (0.0, 0.0)
    assert pct(1, 4).startswith("25% (1/4")


def test_the_noise_floor_matches_the_rule_of_thumb():
    assert abs(noise_floor(50) - 0.141) < 0.001                  # 25 cases x 2 reps is about +/-14 points


def test_summary_separates_areas_attacks_over_refusal_and_truncation(tmp_path):
    rows = [row("g1", "grounding", True), row("g2", "grounding", False), row("s1", "safety", True, attack=True), row("s2", "safety", False, attack=True),
            row("s3", "safety", False, benign_near_miss=True), row("t1", "behavior", True, status="truncated")]
    s = summarize(write_run(tmp_path, "r", rows))
    assert s["areas"] == {"grounding": (1, 2), "safety": (1, 3)} and s["truncated"] == 1 and "behavior" not in s["areas"]
    assert s["attack_succeeded"] == (1, 2) and s["over_refusal"] == (1, 1)
    assert s["cost_total"] == 0.006


def test_ask_back_confusion_matrix():
    def r(expected, ok):
        x = row("a", "behavior", ok)
        x["meta"]["expect_asks_back"] = expected
        x["grade"]["asks_back_ok"] = ok
        return x
    c = confusion([r(True, True), r(True, False), r(False, True), r(False, False), r(None, True)])
    assert c == {"tp": 1, "fn": 1, "fp": 1 + 0, "tn": 1} or c == {"tp": 1, "fn": 1, "fp": 1, "tn": 1}


def test_one_report_lists_failures_errors_and_absolute_cost(tmp_path):
    run = write_run(tmp_path, "haiku", [row("g1", "grounding", True), row("g2", "grounding", False, cost=0.002)],
                    errors=[{"prompt_id": "x", "rep": 0, "failure_class": "timeout", "message": "m", "cost_usd": 0.0005}])
    text = render_one(run)
    assert "Overall pass rate: 50% (1/2" in text and "answer lacks E 75" in text and "traces/g2_rep0.json" in text
    assert "timeout: 1" in text and "$0.0035" in text and "Noise floor" in text


def test_the_comparison_names_the_noise_floor_and_does_not_call_a_small_gap_a_win(tmp_path):
    a = write_run(tmp_path, "haiku", [row("g1", "grounding", True), row("g2", "grounding", False)])
    b = write_run(tmp_path, "sonnet", [row("g1", "grounding", True), row("g2", "grounding", True)])
    text = render_compare([a, b])
    assert "within the noise floor" in text and "haiku" in text and "sonnet" in text
