package hu.mpcalc.backend.web;

import hu.mpcalc.backend.engine.EngineJson;
import hu.mpcalc.backend.service.MessageService;
import hu.mpcalc.backend.service.SpellService;
import hu.mpcalc.engine.MsgException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every error body is {"error": text in the request's language, "code": message key}. */
@RestControllerAdvice
public class ApiErrors {
    private final MessageService messages;

    public ApiErrors(MessageService messages) { this.messages = messages; }

    @ExceptionHandler(SpellService.CastRefused.class)
    ResponseEntity<String> castRefused(SpellService.CastRefused e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", messages.text(e.key, e.args));
        body.put("code", e.key);
        String json = EngineJson.write(body);
        // the engine result (already translated) is added as raw JSON
        String withResult = json.substring(0, json.length() - 1) + ",\"result\":" + e.resultJson + "}";
        return ResponseEntity.status(e.status).contentType(MediaType.APPLICATION_JSON).body(withResult);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException e) {
        return error(e.status, e.key, messages.text(e.key, e.args));
    }

    @ExceptionHandler(MsgException.class)
    ResponseEntity<Map<String, String>> engineMessage(MsgException e) {
        return error(HttpStatus.BAD_REQUEST, e.msg.code(), messages.text(e.msg));
    }

    /** The body could not be read, for example 20.5 where a whole number is expected. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "api.bad.unreadableBody", messages.text("api.bad.unreadableBody"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> bad(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, "api.bad.generic", messages.text("api.bad.generic"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException e) {
        return error(HttpStatus.CONFLICT, "api.dbConstraint", messages.text("api.dbConstraint"));
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String text) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", text);
        body.put("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
