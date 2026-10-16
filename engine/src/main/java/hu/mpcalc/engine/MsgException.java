package hu.mpcalc.engine;

/** An input that cannot be read; carries the message as code and arguments so the caller can translate it. */
public class MsgException extends IllegalArgumentException {
    public final Msg msg;

    public MsgException(Msg msg) { super(msg.toString()); this.msg = msg; }

    public MsgException(Msg msg, Throwable cause) { super(msg.toString(), cause); this.msg = msg; }

    public static MsgException of(MsgCode code, Object... args) { return new MsgException(Msg.of(code, args)); }
}
