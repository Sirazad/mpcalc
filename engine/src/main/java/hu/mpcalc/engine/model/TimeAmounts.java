package hu.mpcalc.engine.model;

import hu.mpcalc.engine.Maybe;
import hu.mpcalc.engine.MsgCode;
import hu.mpcalc.engine.MsgException;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Counts per time unit, kept per unit and never normalised (1 ora 2 kor + 1 ora = 2 ora 2 kor). */
public final class TimeAmounts {
    /** Unit ids from largest to smallest, as in the format document. */
    public static final List<String> UNITS = List.of("ev", "nap", "ora", "perc", "kor", "sz");
    /** Seconds per unit. "ev" is absent on purpose: its length is unknown. nap = 24 h is assumed (to be confirmed). */
    private static final Map<String, Long> SECONDS = Map.of("sz", 1L, "kor", 4L, "perc", 60L, "ora", 3600L, "nap", 86400L);

    private final Map<String, Long> amounts = new TreeMap<>();

    public static TimeAmounts zero() { return new TimeAmounts(); }
    public static TimeAmounts of(Map<String, Long> m) {
        TimeAmounts t = new TimeAmounts();
        m.forEach((u, c) -> {
            if (!UNITS.contains(u)) throw MsgException.of(MsgCode.PARSE_UNKNOWN_TIME_UNIT, u);
            if (c < 0) throw MsgException.of(MsgCode.PARSE_NEGATIVE_TIME, u);
            if (c > 0) t.amounts.put(u, c);
        });
        return t;
    }
    public long get(String unit) { return amounts.getOrDefault(unit, 0L); }
    public boolean isZero() { return amounts.isEmpty(); }
    public TimeAmounts plus(TimeAmounts o) {
        TimeAmounts r = new TimeAmounts();
        for (String u : UNITS) { long s = get(u) + o.get(u); if (s > 0) r.amounts.put(u, s); }
        return r;
    }
    public TimeAmounts times(long n) {
        TimeAmounts r = new TimeAmounts();
        if (n > 0) for (String u : UNITS) if (get(u) > 0) r.amounts.put(u, Math.multiplyExact(get(u), n));
        return r;
    }
    /** Total seconds, or undefined if the amounts contain "ev". */
    public Maybe<Long> seconds() {
        if (get("ev") > 0) return Maybe.undefined(MsgCode.EV_LENGTH_UNKNOWN);
        long s = 0;
        for (var e : SECONDS.entrySet()) s += get(e.getKey()) * e.getValue();
        return Maybe.of(s);
    }
    /** Comparison in seconds; "ev" only against "ev" when all other units are 0 on both sides; otherwise undefined. */
    public Maybe<Integer> compareTo(TimeAmounts o) {
        boolean a = get("ev") > 0, b = o.get("ev") > 0;
        if (!a && !b) return Maybe.of(Long.compare(seconds().value(), o.seconds().value()));
        if (a && b && onlyEv(this) && onlyEv(o)) return Maybe.of(Long.compare(get("ev"), o.get("ev")));
        return Maybe.undefined(MsgCode.EV_COMPARE_UNITS);
    }
    private static boolean onlyEv(TimeAmounts t) { return t.amounts.size() == 1; }
    public Map<String, Long> asMap() { return new TreeMap<>(amounts); }
    @Override public boolean equals(Object o) { return o instanceof TimeAmounts t && amounts.equals(t.amounts); }
    @Override public int hashCode() { return amounts.hashCode(); }
    @Override public String toString() {
        if (amounts.isEmpty()) return "0";
        StringBuilder sb = new StringBuilder();
        for (String u : UNITS) if (get(u) > 0) { if (sb.length() > 0) sb.append(' '); sb.append(get(u)).append(' ').append(u); }
        return sb.toString();
    }
}
