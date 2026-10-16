from app.ratelimit import RateLimiter


class Clock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now


def test_the_eleventh_question_in_the_window_is_refused_with_the_wait_time():
    c = Clock()
    rl = RateLimiter(questions=10, window_seconds=300, concurrent=100, clock=c)
    for _ in range(10):
        assert rl.acquire(1).ok
        rl.release(1)
        c.now += 1
    v = rl.acquire(1)
    assert not v.ok and v.reason == "rate" and 280 <= v.retry_after_seconds <= 300
    c.now += 300
    assert rl.acquire(1).ok


def test_players_are_counted_separately():
    rl = RateLimiter(1, 300, 5, clock=Clock())
    assert rl.acquire(1).ok and rl.acquire(2).ok and not rl.acquire(1).ok


def test_only_a_few_questions_run_at_the_same_time_and_release_frees_a_place():
    rl = RateLimiter(100, 300, 2, clock=Clock())
    assert rl.acquire(1).ok and rl.acquire(1).ok
    v = rl.acquire(1)
    assert not v.ok and v.reason == "busy"
    rl.release(1)
    assert rl.acquire(1).ok
