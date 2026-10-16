package hu.mpcalc.backend.service;

import hu.mpcalc.engine.Msg;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

/** Turns message keys and engine messages (code + arguments) into text in the language of the current request. */
@Service
public class MessageService {
    private final MessageSource source;

    public MessageService(MessageSource source) { this.source = source; }

    /** Text of a key from messages*.properties. A missing key is an error (no silent fallback to the key). */
    public String text(String key, Object... args) {
        return source.getMessage(key, resolve(args), LocaleContextHolder.getLocale());
    }

    /** Text of a key in a given language (for code that runs before the request's locale is set, such as a filter). */
    public String text(java.util.Locale locale, String key, Object... args) {
        return source.getMessage(key, resolve(args), locale);
    }

    /** Text of an engine message; "subject: text" when the message is about a mosaic. */
    public String text(Msg m) {
        String t = text(m.code(), m.args().toArray());
        return m.subject() == null ? t : m.subject() + ": " + t;
    }

    /** Arguments that are messages themselves (for example a time kind) are translated first. */
    private Object[] resolve(Object[] args) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) out[i] = args[i] instanceof Msg m ? text(m) : args[i];
        return out;
    }
}
