package hu.mpcalc.engine;

import hu.mpcalc.engine.model.TimeValue;

import java.util.Arrays;
import java.util.List;

/**
 * A message as code plus arguments (never text, so the engine needs no language). subject, when set, is what the message is
 * about (the mosaic's name); the text is then shown as "subject: text". An argument may itself be a Msg (translated first).
 */
public record Msg(String code, List<Object> args, String subject) {
    public static Msg of(MsgCode code, Object... args) { return new Msg(code.key, Arrays.asList(args), null); }

    public Msg about(String subject) { return new Msg(code, args, subject); }

    /** A time kind as an argument: translated as "Egyszeri", "Maradandó", ... */
    public static Msg kind(TimeValue.Kind k) {
        return of(switch (k) {
            case TIME -> MsgCode.KIND_TIME;
            case ONE_OFF -> MsgCode.KIND_ONE_OFF;
            case PERMANENT -> MsgCode.KIND_PERMANENT;
            case FORM_DEPENDENT -> MsgCode.KIND_FORM_DEPENDENT;
            case UNDEFINED -> MsgCode.KIND_UNDEFINED;
        });
    }

    /** Readable form for tests and logs: code[args]. */
    @Override public String toString() { return (subject == null ? "" : subject + ": ") + code + (args.isEmpty() ? "" : args.toString()); }
}
