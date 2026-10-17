package hu.mpcalc.engine.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * type = choice | number | form | composition | text.
 * A choice input may carry labels (value to the Hungarian name shown to the player) and descriptions (value to its explanation).
 */
public class Input {
    public String id;
    public String label;
    public String type;
    public List<String> values;
    @JsonInclude(JsonInclude.Include.NON_NULL) public Map<String, String> labels;
    @JsonInclude(JsonInclude.Include.NON_NULL) public Map<String, String> descriptions;
    /** choice: value to the tiers needed for that value, for example the effect type of a protective circle. */
    @JsonInclude(JsonInclude.Include.NON_NULL) public Map<String, List<Requirement>> requires;
    public String unit;
    /** number: only whole numbers are accepted (for example the radius of a sphere in whole meters). */
    @JsonInclude(JsonInclude.Include.NON_NULL) public Boolean integer;
    /** number: the smallest accepted value. */
    @JsonInclude(JsonInclude.Include.NON_NULL) public java.math.BigDecimal min;
}
