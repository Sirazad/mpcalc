package hu.mpcalc.backend.web;

import org.springframework.http.HttpStatus;

/** An error for the client. The text is not stored here: key and arguments are translated by ApiErrors (messages*.properties). */
public class ApiException extends RuntimeException {
    public final HttpStatus status;
    public final String key;
    public final Object[] args;

    public ApiException(HttpStatus status, String key, Object... args) {
        super(key);
        this.status = status; this.key = key; this.args = args;
    }

    public static ApiException notFound(String key, Object... args) { return new ApiException(HttpStatus.NOT_FOUND, key, args); }
    public static ApiException badRequest(String key, Object... args) { return new ApiException(HttpStatus.BAD_REQUEST, key, args); }
}
