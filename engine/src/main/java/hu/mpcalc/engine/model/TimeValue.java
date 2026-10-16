package hu.mpcalc.engine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import hu.mpcalc.engine.Maybe;
import hu.mpcalc.engine.Msg;
import hu.mpcalc.engine.MsgCode;
import hu.mpcalc.engine.MsgException;

import java.util.HashMap;
import java.util.Map;

/**
 * A time value: kind TIME (with amounts), ONE_OFF, PERMANENT, FORM_DEPENDENT or UNDEFINED.
 * Parsing accepts {"kind":"time","amounts":{..}} and also the wrapped form {"kind":"time","time":{"kind":"time","amounts":{..}}}
 * that the format document uses for durations.
 */
public final class TimeValue {
    public enum Kind { TIME, ONE_OFF, PERMANENT, FORM_DEPENDENT, UNDEFINED }
    public final Kind kind;
    public final TimeAmounts amounts;

    public TimeValue(Kind kind, TimeAmounts amounts) { this.kind = kind; this.amounts = amounts; }
    public static TimeValue time(TimeAmounts a) { return new TimeValue(Kind.TIME, a); }
    public static TimeValue undefinedValue() { return new TimeValue(Kind.UNDEFINED, null); }

    @JsonCreator
    public static TimeValue fromJson(JsonNode n) {
        if (n == null || n.isNull()) return undefinedValue();
        String k = n.path("kind").asText("");
        switch (k) {
            case "time" -> {
                if (n.has("amounts")) {
                    Map<String, Long> m = new HashMap<>();
                    n.get("amounts").fields().forEachRemaining(e -> {
                        if (!e.getValue().isIntegralNumber()) throw MsgException.of(MsgCode.PARSE_TIME_AMOUNT_NOT_INTEGER, e.getKey());
                        m.put(e.getKey(), e.getValue().asLong());
                    });
                    return time(TimeAmounts.of(m));
                }
                if (n.has("time")) return fromJson(n.get("time"));
                throw MsgException.of(MsgCode.PARSE_TIME_WITHOUT_AMOUNTS);
            }
            case "oneOff" -> { return new TimeValue(Kind.ONE_OFF, null); }
            case "permanent" -> { return new TimeValue(Kind.PERMANENT, null); }
            case "formDependent" -> { return new TimeValue(Kind.FORM_DEPENDENT, null); }
            case "undefined" -> { return undefinedValue(); }
            default -> throw MsgException.of(MsgCode.PARSE_UNKNOWN_TIME_KIND, k);
        }
    }

    public boolean isTime() { return kind == Kind.TIME; }

    public static Maybe<TimeValue> add(Maybe<TimeValue> a, Maybe<TimeValue> b) {
        if (!a.isDefined()) return a;
        if (!b.isDefined()) return b;
        if (!a.value().isTime() || !b.value().isTime())
            return Maybe.undefined(MsgCode.CANNOT_ADD_TIMES, Msg.kind(a.value().kind), Msg.kind(b.value().kind));
        return Maybe.of(time(a.value().amounts.plus(b.value().amounts)));
    }
    public Maybe<TimeValue> times(long n) {
        if (!isTime()) return Maybe.undefined(MsgCode.CANNOT_MULTIPLY_TIME, Msg.kind(kind));
        return Maybe.of(time(amounts.times(n)));
    }
    /** Maybe-wrapper: TIME is defined, every other kind is returned as defined only if allowed by the caller. */
    public Maybe<TimeValue> asTimeOrUndefined(MsgCode notATime) {
        return isTime() ? Maybe.of(this) : Maybe.undefined(notATime, Msg.kind(kind));
    }
    @Override public boolean equals(Object o) { return o instanceof TimeValue t && kind == t.kind && java.util.Objects.equals(amounts, t.amounts); }
    @Override public int hashCode() { return java.util.Objects.hash(kind, amounts); }
    @Override public String toString() { return kind == Kind.TIME ? amounts.toString() : kind.name(); }
}
