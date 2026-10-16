"""Prices in dollars per million tokens (Claude API reference, cached 2026-10-06). Cost is derived from each row's model and usage,
never stored as a constant, so a model swap cannot carry a stale rate."""
from __future__ import annotations

PRICES: dict[str, tuple[float, float]] = {          # model: (input, output) per million tokens
    "claude-haiku-5-5": (0.10, 0.50),
    "claude-sonnet-5-5": (2.00, 10.00),
    "claude-opus-5-5": (4.00, 20.00),
}
CACHE_WRITE = 1.25      # x input price
CACHE_READ = 0.10       # x input price


def cost_usd(model: str | None, usage: dict | None) -> float:
    """Dollars for one call. An unknown model is an error, not a zero: a silent zero would hide spend."""
    if not usage or not any(usage.values()):
        return 0.0
    if model is None or model not in PRICES:
        raise KeyError(f"no price for model {model!r}: add it to evals/prices.py")
    pin, pout = PRICES[model]
    inp = usage.get("input_tokens", 0) or 0
    out = usage.get("output_tokens", 0) or 0
    cw = usage.get("cache_creation_input_tokens", 0) or 0
    cr = usage.get("cache_read_input_tokens", 0) or 0
    return (inp * pin + cw * pin * CACHE_WRITE + cr * pin * CACHE_READ + out * pout) / 1_000_000
