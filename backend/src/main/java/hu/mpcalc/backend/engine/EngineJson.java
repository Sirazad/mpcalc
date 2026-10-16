package hu.mpcalc.backend.engine;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import hu.mpcalc.engine.Fraction;
import hu.mpcalc.engine.Json;
import hu.mpcalc.engine.Maybe;
import hu.mpcalc.engine.Msg;
import hu.mpcalc.engine.model.TimeAmounts;
import hu.mpcalc.engine.model.TimeValue;

import java.io.IOException;
import java.util.function.Function;
import java.math.BigDecimal;

/**
 * The engine uses Jackson 2, Spring MVC uses Jackson 3. Everything that carries engine types is read and written
 * with this mapper (the engine's own mapper plus serializers for its value types) as raw JSON text.
 */
public final class EngineJson {
    private static final String TRANSLATOR = "translator";
    private static final ObjectMapper MAPPER = Json.mapper().copy().registerModule(module());

    private EngineJson() {}

    public static ObjectMapper mapper() { return MAPPER; }

    public static JsonNode parse(String json) {
        try { return MAPPER.readTree(json); } catch (IOException e) { throw hu.mpcalc.backend.web.ApiException.badRequest("api.bad.json", String.valueOf(e.getMessage())); }
    }

    public static String write(Object o) {
        try { return MAPPER.writeValueAsString(o); } catch (IOException e) { throw new IllegalStateException("Cannot write JSON: " + e.getMessage(), e); }
    }

    /** Like write(o), and every engine message (Msg) also gets its "text", translated by the given function. */
    public static String write(Object o, Function<Msg, String> translator) {
        try { return MAPPER.writer().withAttribute(TRANSLATOR, translator).writeValueAsString(o); }
        catch (IOException e) { throw new IllegalStateException("Cannot write JSON: " + e.getMessage(), e); }
    }

    /** The JSON tree of o with translated messages (see write(o, translator)). */
    public static JsonNode tree(Object o, Function<Msg, String> translator) { return parse(write(o, translator)); }

    private static SimpleModule module() {
        SimpleModule m = new SimpleModule("mpcalc-engine-types");
        m.addSerializer(Fraction.class, new JsonSerializer<>() {
            @Override public void serialize(Fraction f, JsonGenerator g, SerializerProvider p) throws IOException {
                g.writeStartObject();
                g.writeNumberField("numerator", f.numerator());
                g.writeNumberField("denominator", f.denominator());
                BigDecimal d = f.toExactDecimal();
                if (d == null) g.writeNullField("decimal"); else g.writeNumberField("decimal", d);
                g.writeEndObject();
            }
        });
        m.addSerializer(TimeValue.class, new JsonSerializer<>() {
            @Override public void serialize(TimeValue t, JsonGenerator g, SerializerProvider p) throws IOException {
                g.writeStartObject();
                g.writeStringField("kind", t.kind.name());
                if (t.amounts != null) {
                    g.writeObjectFieldStart("amounts");
                    for (String u : TimeAmounts.UNITS) if (t.amounts.get(u) > 0) g.writeNumberField(u, t.amounts.get(u));
                    g.writeEndObject();
                }
                g.writeEndObject();
            }
        });
        @SuppressWarnings({"unchecked", "rawtypes"})
        JsonSerializer<Maybe> maybe = new JsonSerializer<>() {
            @Override public void serialize(Maybe v, JsonGenerator g, SerializerProvider p) throws IOException {
                g.writeStartObject();
                g.writeBooleanField("defined", v.isDefined());
                if (v.isDefined()) p.defaultSerializeField("value", v.value(), g);
                else p.defaultSerializeField("reason", v.reason(), g);
                g.writeEndObject();
            }
        };
        m.addSerializer(Maybe.class, maybe);
        m.addSerializer(Msg.class, new JsonSerializer<>() {
            @SuppressWarnings("unchecked")
            @Override public void serialize(Msg v, JsonGenerator g, SerializerProvider p) throws IOException {
                g.writeStartObject();
                g.writeStringField("code", v.code());
                p.defaultSerializeField("args", v.args(), g);
                if (v.subject() != null) g.writeStringField("subject", v.subject());
                Function<Msg, String> t = (Function<Msg, String>) p.getAttribute(TRANSLATOR);
                if (t != null) g.writeStringField("text", t.apply(v));
                g.writeEndObject();
            }
        });
        return m;
    }
}
