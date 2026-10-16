package hu.mpcalc.engine.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;

/**
 * One selected mosaic. inputs: id -> value (choice: string; number: number; form: form id string;
 * composition: array of {"group":"I|II|III","massG":number}; text: string). k = chosen power step (0 = base).
 * extraMp = extra MP on a modifier (durationPerExtraMp); options = ticked option ids.
 */
public record Selection(Mosaic mosaic, Map<String, JsonNode> inputs, int k, Set<String> options, int extraMp) {
    public static Selection of(Mosaic m) { return new Selection(m, Map.of(), 0, Set.of(), 0); }
    public Selection withInputs(Map<String, JsonNode> in) { return new Selection(mosaic, in, k, options, extraMp); }
    public Selection withK(int k) { return new Selection(mosaic, inputs, k, options, extraMp); }
    public Selection withOptions(Set<String> o) { return new Selection(mosaic, inputs, k, o, extraMp); }
    public Selection withExtraMp(int x) { return new Selection(mosaic, inputs, k, options, x); }
}
