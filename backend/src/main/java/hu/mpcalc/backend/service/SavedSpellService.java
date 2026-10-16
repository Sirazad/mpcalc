package hu.mpcalc.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import hu.mpcalc.backend.engine.EngineJson;
import hu.mpcalc.backend.model.GameCharacter;
import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.model.SavedSpell;
import hu.mpcalc.backend.repo.Repositories.SavedSpellRepository;
import hu.mpcalc.backend.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Saved spells of a character. Body: {"name": "...", "content": {...}}; content is a JSON object kept as given. */
@Service
public class SavedSpellService {
    private final SavedSpellRepository spells;
    private final CharacterService characters;
    private final AuditService audit;

    public SavedSpellService(SavedSpellRepository spells, CharacterService characters, AuditService audit) {
        this.spells = spells; this.characters = characters; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public String list(Player p, Long characterId) {
        characters.owned(p, characterId);
        return "[" + String.join(",", spells.findAllByCharacterIdOrderByName(characterId).stream().map(SavedSpellService::json).toList()) + "]";
    }

    @Transactional(readOnly = true)
    public String get(Player p, Long id) { return json(owned(p, id)); }

    @Transactional
    public String create(Player p, Long characterId, String body) {
        characters.owned(p, characterId);
        JsonNode b = EngineJson.parse(body);
        SavedSpell s = new SavedSpell();
        s.characterId = characterId;
        s.name = name(b);
        s.content = content(b);
        s = spells.saveAndFlush(s);
        audit.record(p, "saved_spell", String.valueOf(s.id), "create", body, null, json(s));
        return json(s);
    }

    @Transactional
    public String update(Player p, Long id, String body) {
        SavedSpell s = owned(p, id);
        String before = json(s);
        JsonNode b = EngineJson.parse(body);
        if (b.has("name")) s.name = name(b);
        if (b.has("content")) s.content = content(b);
        s.updatedAt = Instant.now();
        spells.saveAndFlush(s);
        audit.record(p, "saved_spell", String.valueOf(s.id), "update", body, before, json(s));
        return json(s);
    }

    @Transactional
    public void delete(Player p, Long id) {
        SavedSpell s = owned(p, id);
        String before = json(s);
        spells.delete(s);
        spells.flush();
        audit.record(p, "saved_spell", String.valueOf(id), "delete", null, before, null);
    }

    private SavedSpell owned(Player p, Long id) {
        SavedSpell s = spells.findById(id).orElseThrow(() -> ApiException.notFound("api.notFound.savedSpell"));
        GameCharacter c = characters.owned(p, s.characterId);
        if (c == null) throw ApiException.notFound("api.notFound.savedSpell");
        return s;
    }

    private static String name(JsonNode b) {
        String n = b.path("name").asText("").trim();
        if (n.isEmpty()) throw ApiException.badRequest("api.bad.nameRequired");
        return n;
    }

    private static String content(JsonNode b) {
        if (!b.path("content").isObject()) throw ApiException.badRequest("api.bad.contentNotObject");
        return b.get("content").toString();
    }

    private static String json(SavedSpell s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("characterId", s.characterId);
        m.put("name", s.name);
        m.put("content", EngineJson.parse(s.content));
        return EngineJson.write(m);
    }
}
