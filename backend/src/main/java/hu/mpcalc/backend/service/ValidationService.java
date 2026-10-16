package hu.mpcalc.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.mpcalc.backend.engine.EngineJson;
import hu.mpcalc.backend.model.FormEntity;
import hu.mpcalc.backend.model.GameCharacter;
import hu.mpcalc.backend.model.MosaicEntity;
import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.repo.Repositories.FormRepository;
import hu.mpcalc.backend.repo.Repositories.MosaicRepository;
import hu.mpcalc.backend.repo.Repositories.SavedSpellRepository;
import hu.mpcalc.backend.web.ApiException;
import hu.mpcalc.engine.MosaicResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Read-only check that the data the page holds is in sync with the database. Nothing is changed and nothing is audited.
 * Request (what the page holds): {"characterId", "character": {"maxMp", "currentMp", "tiers": {school: tier}},
 * "mosaicVersions": {id: version}, "forms": {id: tier}, "selections": [as in calculate],
 * "displayedTotalMp": {"numerator", "denominator"} or null, "savedSpellIds": [ids]}.
 * Response: {"inSync", "differences": [{"area", "id", "code", "expected", "actual"}], "result": calculate result or null}.
 * expected = what the database says, actual = what the page holds.
 */
@Service
public class ValidationService {
    private final CharacterService characters;
    private final CatalogService catalog;
    private final SpellService spells;
    private final MosaicRepository mosaics;
    private final FormRepository forms;
    private final SavedSpellRepository savedSpells;
    private final MessageService messages;

    public ValidationService(CharacterService characters, CatalogService catalog, SpellService spells, MosaicRepository mosaics,
                             FormRepository forms, SavedSpellRepository savedSpells, MessageService messages) {
        this.characters = characters; this.catalog = catalog; this.spells = spells; this.mosaics = mosaics;
        this.forms = forms; this.savedSpells = savedSpells; this.messages = messages;
    }

    @Transactional(readOnly = true)
    public String validate(Player p, String body) {
        JsonNode root = EngineJson.parse(body);
        if (!root.path("characterId").canConvertToLong()) throw ApiException.badRequest("api.bad.characterIdRequired");
        GameCharacter c = characters.owned(p, root.get("characterId").asLong());
        List<Map<String, Object>> diffs = new ArrayList<>();

        checkCharacter(c, root.path("character"), diffs);
        checkMosaics(c, root.path("mosaicVersions"), diffs);
        checkForms(root.path("forms"), diffs);
        JsonNode result = checkSpell(p, c, root, diffs);
        checkSavedSpells(p, root.path("savedSpellIds"), diffs);

        ObjectNode out = EngineJson.mapper().createObjectNode();
        out.put("inSync", diffs.isEmpty());
        diffs.forEach(d -> d.put("text", messages.text("api.validate." + d.get("code"))));
        out.set("differences", EngineJson.mapper().valueToTree(diffs));
        out.set("result", result);
        return out.toString();
    }

    private void checkCharacter(GameCharacter c, JsonNode held, List<Map<String, Object>> diffs) {
        if (held.path("maxMp").canConvertToInt() && held.get("maxMp").asInt() != c.maxMp)
            diffs.add(diff("mp", "maxMp", "MAX_MP_DIFFERS", c.maxMp, held.get("maxMp").asInt()));
        if (held.path("currentMp").canConvertToInt() && held.get("currentMp").asInt() != c.currentMp)
            diffs.add(diff("mp", "currentMp", "CURRENT_MP_DIFFERS", c.currentMp, held.get("currentMp").asInt()));
        Map<String, Integer> db = characters.tiersOf(c.id);
        Set<String> schools = new TreeSet<>(db.keySet());
        held.path("tiers").fieldNames().forEachRemaining(schools::add);
        for (String s : schools) {
            int expected = db.getOrDefault(s, 0);
            int actual = held.path("tiers").path(s).asInt(0);
            if (expected != actual) diffs.add(diff("tiers", s, "TIER_DIFFERS", expected, actual));
        }
    }

    private void checkMosaics(GameCharacter c, JsonNode held, List<Map<String, Object>> diffs) {
        Map<String, MosaicEntity> available = new LinkedHashMap<>();
        catalog.available(c).forEach(m -> available.put(m.id, m));
        Set<String> heldIds = new TreeSet<>();
        held.fieldNames().forEachRemaining(heldIds::add);
        for (String id : heldIds) {
            MosaicEntity m = available.get(id);
            if (m == null) {
                Optional<MosaicEntity> any = mosaics.findById(id);
                diffs.add(diff("mosaic", id, any.isEmpty() ? "MOSAIC_MISSING" : "MOSAIC_NOT_AVAILABLE", null, held.get(id).asText()));
            } else if (!CatalogService.version(m).equals(held.get(id).asText())) {
                diffs.add(diff("mosaic", id, "MOSAIC_CHANGED", CatalogService.version(m), held.get(id).asText()));
            }
        }
        for (String id : available.keySet()) if (!heldIds.contains(id)) diffs.add(diff("mosaic", id, "MOSAIC_NEW", CatalogService.version(available.get(id)), null));
    }

    private void checkForms(JsonNode held, List<Map<String, Object>> diffs) {
        Map<String, FormEntity> db = new TreeMap<>();
        forms.findAll().forEach(f -> db.put(f.id, f));
        Set<String> heldIds = new TreeSet<>();
        held.fieldNames().forEachRemaining(heldIds::add);
        for (String id : heldIds) {
            FormEntity f = db.get(id);
            if (f == null) diffs.add(diff("form", id, "FORM_MISSING", null, held.get(id).asInt()));
            else if (f.tier != held.get(id).asInt()) diffs.add(diff("form", id, "FORM_CHANGED", (int) f.tier, held.get(id).asInt()));
        }
        for (String id : db.keySet()) if (!heldIds.contains(id)) diffs.add(diff("form", id, "FORM_NEW", (int) db.get(id).tier, null));
    }

    /** Recalculates the current spell with the engine; returns the result, or null when there is no spell or a mosaic is missing. */
    private JsonNode checkSpell(Player p, GameCharacter c, JsonNode root, List<Map<String, Object>> diffs) {
        JsonNode sels = root.path("selections");
        if (!sels.isArray() || sels.isEmpty()) return null;
        boolean missing = false;
        for (JsonNode s : sels) {
            String id = s.path("mosaicId").asText("");
            if (!mosaics.existsById(id)) { diffs.add(diff("spell", id, "SPELL_MOSAIC_MISSING", null, id)); missing = true; }
        }
        if (missing) return null;
        SpellService.Run run = spells.run(root, c);
        for (MosaicResult m : run.result().mosaics())
            if (m.refused()) diffs.add(diff("spell", m.mosaicId(), "SPELL_REFUSED", m.errors(), null));
        var total = run.result().totalMp();
        JsonNode shown = root.path("displayedTotalMp");
        if (shown.isObject()) {
            boolean same = total.isDefined()
                    && total.value().numerator().toString().equals(shown.path("numerator").asText())
                    && total.value().denominator().toString().equals(shown.path("denominator").asText());
            if (!same) diffs.add(diff("spell", "totalMp", "TOTAL_DIFFERS", total.isDefined() ? total.value().numerator() + "/" + total.value().denominator() : "undefined",
                    shown.path("numerator").asText() + "/" + shown.path("denominator").asText()));
        }
        return EngineJson.tree(run.result(), messages::text);
    }

    private void checkSavedSpells(Player p, JsonNode ids, List<Map<String, Object>> diffs) {
        for (JsonNode n : ids) {
            long id = n.asLong();
            boolean ok = savedSpells.findById(id).map(s -> {
                try { characters.owned(p, s.characterId); return true; } catch (ApiException e) { return false; }
            }).orElse(false);
            if (!ok) diffs.add(diff("savedSpell", String.valueOf(id), "SAVED_SPELL_MISSING", null, id));
        }
    }

    private static Map<String, Object> diff(String area, String id, String code, Object expected, Object actual) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("area", area); m.put("id", id); m.put("code", code); m.put("expected", expected); m.put("actual", actual);
        return m;
    }
}
