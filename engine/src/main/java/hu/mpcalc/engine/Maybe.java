package hu.mpcalc.engine;

import java.util.Objects;

/** A value that is either defined or "undefined" (with the reason why, as a message). The engine never invents a value. */
public record Maybe<T>(T value, Msg reason) {
    public static <T> Maybe<T> of(T v) { return new Maybe<>(Objects.requireNonNull(v), null); }
    public static <T> Maybe<T> undefined(Msg reason) { return new Maybe<>(null, Objects.requireNonNull(reason)); }
    public static <T> Maybe<T> undefined(MsgCode code, Object... args) { return undefined(Msg.of(code, args)); }
    public boolean isDefined() { return value != null; }
    @Override public String toString() { return isDefined() ? String.valueOf(value) : "undefined (" + reason + ")"; }
}
