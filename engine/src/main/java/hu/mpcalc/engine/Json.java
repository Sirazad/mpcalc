package hu.mpcalc.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.mpcalc.engine.model.Form;
import hu.mpcalc.engine.model.Mosaic;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Parsing of the approved mosaic definition JSON and the forms table. Unknown fields are ignored. */
public final class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
    private Json() {}
    public static ObjectMapper mapper() { return MAPPER; }
    public static Mosaic parseMosaic(String json) {
        try { return MAPPER.readValue(json, Mosaic.class); } catch (IOException e) { throw new MsgException(Msg.of(MsgCode.PARSE_BAD_MOSAIC_JSON, String.valueOf(e.getMessage())), e); }
    }
    public static Mosaic parseMosaic(InputStream in) {
        try { return MAPPER.readValue(in, Mosaic.class); } catch (IOException e) { throw new MsgException(Msg.of(MsgCode.PARSE_BAD_MOSAIC_JSON, String.valueOf(e.getMessage())), e); }
    }
    public static List<Form> parseForms(String json) {
        try { return MAPPER.readValue(json, new TypeReference<List<Form>>() {}); } catch (IOException e) { throw new MsgException(Msg.of(MsgCode.PARSE_BAD_FORMS_JSON, String.valueOf(e.getMessage())), e); }
    }
}
