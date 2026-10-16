"""Per-player limit: at most N questions in a time window and a few at the same time. In memory: enough for one container."""
from __future__ import annotations

import time
from collections import defaultdict, deque
from dataclasses import dataclass
from typing import Callable


@dataclass(frozen=True)
class Verdict:
    ok: bool
    reason: str | None = None          # "rate" or "busy"
    retry_after_seconds: int = 0


class RateLimiter:
    def __init__(self, questions: int, window_seconds: int, concurrent: int, clock: Callable[[], float] = time.monotonic):
        self._questions = questions
        self._window = window_seconds
        self._concurrent = concurrent
        self._clock = clock
        self._times: dict[int, deque[float]] = defaultdict(deque)
        self._running: dict[int, int] = defaultdict(int)

    def acquire(self, player_id: int) -> Verdict:
        """Counts the question when allowed; call release() when its answer is finished."""
        now = self._clock()
        times = self._times[player_id]
        while times and now - times[0] >= self._window:
            times.popleft()
        if self._running[player_id] >= self._concurrent:
            return Verdict(False, "busy", 1)
        if len(times) >= self._questions:
            wait = int(self._window - (now - times[0])) + 1
            return Verdict(False, "rate", max(wait, 1))
        times.append(now)
        self._running[player_id] += 1
        return Verdict(True)

    def release(self, player_id: int) -> None:
        if self._running[player_id] > 0:
            self._running[player_id] -= 1
