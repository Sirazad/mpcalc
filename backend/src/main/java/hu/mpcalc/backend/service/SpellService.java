package hu.mpcalc.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.mpcalc.backend.engine.EngineJson;
import hu.mpcalc.backend.model.FormEntity;
import hu.mpcalc.backend.model.GameCharacter;
import hu.mpcalc.backend.model.MosaicEntity;
import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.repo.Repositories.*;
import hu.mpcalc.backend.web.ApiException;
import hu.mpcalc.engine.Fraction;
import hu.mpcalc.engine.Json;
import hu.mpcalc.engine.SpellCalculator;
import hu.mpcalc.engine.SpellResult;
import hu.mpcalc.engine.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Request body of calculate and cast (raw JSON, read with the engine's Jackson 2 mapper):
 * {"characterId": 1, "selections": [{"mosaicId": "...", "inputs": {...}, "k": 0, "options": ["..."], "extraMp": 0}]}
 */
@Service
public class SpellService {
    private final SpellCalculator calculator = new SpellCalculator();
    private final CharacterService characters;
    private final MosaicRepository mosaics;
    private final FormRepository forms;
    private final AuditService audit;
    private final MessageService messages;

    public SpellService(CharacterService characters, MosaicRepository mosaics, FormRepository forms, AuditService audit, MessageService messages) {
        this.characters = characters; this.mosaics = mosaics; this.forms = forms; this.audit = audit; this.messages = messages;
    }

    /** The engine result plus overspendMp (MP above current MP, exact fraction, or null) and schools (schools of the spell's mosaics). */
    @Transactional(readOnly = true)
    public String calculate(Player p, String body) {
        JsonNode root = EngineJson.parse(body);
        GameCharacter c = characters.owned(p, characterId(root));
        Run run = run(root, c);
        ObjectNode out = (ObjectNode) EngineJson.tree(run.result(), messages::text);
        out.set("overspentMp", EngineJson.mapper().valueToTree(overspent(run.result())));
        out.set("schools", EngineJson.mapper().valueToTree(run.schools()));
        return out.toString();
    }

    /**
     * Re-calculates on the server, then lowers current MP by the total. Spending more than the current MP is allowed
     * (chaos magic): current MP becomes 0 and the response reports overspentMp and the schools of the spell, so the page
     * can warn the player. A spell that is not valid, or whose total MP is undefined or not whole, is refused.
     */
    @Transactional
    public String cast(Player p, String body) {
        JsonNode root = EngineJson.parse(body);
        GameCharacter c = characters.ownedForUpdate(p, characterId(root));
        Run run = run(root, c);
        SpellResult result = run.result();
        String why = null; Object[] whyArgs = new Object[0];
        if (!result.valid()) why = "api.cast.notValid";
        else if (!result.totalMp().isDefined()) { why = "api.cast.totalUndefined"; whyArgs = new Object[] {result.totalMp().reason()}; }
        else if (!result.totalMp().value().isWhole()) why = "api.cast.notWhole";
        if (why != null) throw new CastRefused(why, whyArgs, EngineJson.write(result, messages::text));

        int total = result.totalMp().value().numerator().intValueExact();
        int before = c.currentMp;
        int overspent = Math.max(0, total - before);
        c.currentMp = Math.max(0, before - total);
        c.updatedAt = Instant.now();
        audit.record(p, "character", String.valueOf(c.id), "update", body,
                EngineJson.write(Map.of("currentMp", before)),
                EngineJson.write(Map.of("currentMp", c.currentMp, "overspentMp", overspent)));
        ObjectNode out = EngineJson.mapper().createObjectNode();
        out.set("result", EngineJson.tree(result, messages::text));
        out.put("totalMp", total);
        out.put("overspentMp", overspent);
        out.set("schools", EngineJson.mapper().valueToTree(overspent > 0 ? run.schools() : List.of()));
        out.put("currentMp", c.currentMp);
        return out.toString();
    }

    record Run(SpellResult result, List<String> schools) {}

    /** MP above the current MP as an exact fraction, or null when nothing is overspent or the total is undefined. */
    private static Fraction overspent(SpellResult r) {
        if (!r.remainingMp().isDefined() || r.remainingMp().value().signum() >= 0) return null;
        return r.remainingMp().value().negate();
    }

    /** Cast refused: the HTTP layer returns 422 with the engine result as the body. */
    public static class CastRefused extends ApiException {
        public final String resultJson;
        CastRefused(String key, Object[] args, String resultJson) { super(HttpStatus.UNPROCESSABLE_ENTITY, key, args); this.resultJson = resultJson; }
    }

    /** A whole number field: absent means 0; a decimal such as 1.5 is refused, never cut to 1. */
    private static int wholeNumber(JsonNode selection, String field) {
        JsonNode n = selection.path(field);
        if (n.isMissingNode() || n.isNull()) return 0;
        if (!n.isIntegralNumber() || !n.canConvertToInt()) throw ApiException.badRequest("api.bad.notWholeNumber", field);
        return n.asInt();
    }

    private static Long characterId(JsonNode root) {
        if (!root.path("characterId").canConvertToLong()) throw ApiException.badRequest("api.bad.characterIdRequired");
        return root.get("characterId").asLong();
    }

    /** Shared by calculate, cast and validate. */
    Run run(JsonNode root, GameCharacter c) {
        JsonNode sels = root.path("selections");
        if (!sels.isArray() || sels.isEmpty()) throw ApiException.badRequest("api.bad.selectionsRequired");
        List<Selection> selections = new ArrayList<>();
        Set<String> schools = new LinkedHashSet<>();
        for (JsonNode s : sels) {
            String id = s.path("mosaicId").asText("");
            MosaicEntity me = mosaics.findById(id).orElseThrow(() -> ApiException.notFound("api.notFound.mosaic", id));
            Mosaic m = Json.parseMosaic(me.definition);
            schools.add(me.school);
            Map<String, JsonNode> inputs = new LinkedHashMap<>();
            s.path("inputs").fields().forEachRemaining(e -> inputs.put(e.getKey(), e.getValue()));
            Set<String> options = new LinkedHashSet<>();
            s.path("options").forEach(o -> options.add(o.asText()));
            selections.add(Selection.of(m).withInputs(inputs).withK(wholeNumber(s, "k")).withOptions(options).withExtraMp(wholeNumber(s, "extraMp")));
        }
        CharacterSheet sheet = new CharacterSheet(characters.tiersOf(c.id), BigDecimal.valueOf(c.currentMp));
        return new Run(calculator.calculate(new SpellRequest(selections, sheet, allForms())), List.copyOf(schools));
    }

    /** The engine call without any database access, for searches that evaluate many spells. */
    SpellResult evaluate(List<Selection> selections, CharacterSheet sheet, List<Form> forms) {
        return calculator.calculate(new SpellRequest(selections, sheet, forms));
    }

    List<Form> allForms() {
        ArrayNode arr = EngineJson.mapper().createArrayNode();
        for (FormEntity f : forms.findAll()) {
            ObjectNode o = arr.addObject();
            o.put("id", f.id);
            o.put("name", f.name);
            o.put("tier", f.tier);
            o.set("duration", EngineJson.parse(f.duration));
        }
        return Json.parseForms(arr.toString());
    }
}
