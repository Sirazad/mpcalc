package hu.mpcalc.backend.service;

import hu.mpcalc.backend.engine.EngineJson;
import hu.mpcalc.backend.model.CharacterSchoolTier;
import hu.mpcalc.backend.model.GameCharacter;
import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.repo.Repositories.*;
import hu.mpcalc.backend.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CharacterService {
    /** Body of create and update. tiers: school id to tier 0..5 (alapvarazslatok is always tier 0 and has no entry). */
    public record CharacterBody(String name, Integer maxMp, Integer currentMp, Map<String, Integer> tiers) {}

    public record CharacterView(Long id, String name, int maxMp, int currentMp, Map<String, Integer> tiers) {}

    private final CharacterRepository characters;
    private final TierRepository tiers;
    private final SchoolRepository schools;
    private final AuditService audit;

    public CharacterService(CharacterRepository characters, TierRepository tiers, SchoolRepository schools, AuditService audit) {
        this.characters = characters; this.tiers = tiers; this.schools = schools; this.audit = audit;
    }

    /** The player's own character; another player's character is reported as not found. */
    @Transactional(readOnly = true)
    public GameCharacter owned(Player p, Long id) {
        return characters.findById(id).filter(c -> c.playerId.equals(p.id)).orElseThrow(() -> ApiException.notFound("api.notFound.character"));
    }

    @Transactional
    public GameCharacter ownedForUpdate(Player p, Long id) {
        return characters.lockById(id).filter(c -> c.playerId.equals(p.id)).orElseThrow(() -> ApiException.notFound("api.notFound.character"));
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> tiersOf(Long characterId) {
        Map<String, Integer> m = new LinkedHashMap<>();
        tiers.findAllByCharacterId(characterId).forEach(t -> m.put(t.school, (int) t.tier));
        return m;
    }

    @Transactional(readOnly = true)
    public CharacterView view(GameCharacter c) { return new CharacterView(c.id, c.name, c.maxMp, c.currentMp, tiersOf(c.id)); }

    @Transactional(readOnly = true)
    public List<CharacterView> list(Player p) { return characters.findAllByPlayerIdOrderByName(p.id).stream().map(this::view).toList(); }

    @Transactional
    public CharacterView create(Player p, CharacterBody b) {
        if (b == null || b.name() == null || b.name().isBlank()) throw ApiException.badRequest("api.bad.nameRequired");
        if (b.maxMp() == null || b.maxMp() < 0) throw ApiException.badRequest("api.bad.maxMpNegative");
        int current = b.currentMp() == null ? b.maxMp() : b.currentMp();
        checkMp(b.maxMp(), current);
        GameCharacter c = new GameCharacter();
        c.playerId = p.id; c.name = b.name().trim(); c.maxMp = b.maxMp(); c.currentMp = current;
        c = characters.saveAndFlush(c);
        replaceTiers(c.id, b.tiers());
        CharacterView after = view(c);
        audit.record(p, "character", String.valueOf(c.id), "create", EngineJson.write(b), null, EngineJson.write(after));
        return after;
    }

    /** Owner edits name, MP and tiers. Missing fields stay as they are; tiers, when given, replace the whole set. */
    @Transactional
    public CharacterView update(Player p, Long id, CharacterBody b) {
        GameCharacter c = ownedForUpdate(p, id);
        CharacterView before = view(c);
        if (b.name() != null) { if (b.name().isBlank()) throw ApiException.badRequest("api.bad.nameEmpty"); c.name = b.name().trim(); }
        if (b.maxMp() != null) c.maxMp = b.maxMp();
        if (b.currentMp() != null) c.currentMp = b.currentMp();
        checkMp(c.maxMp, c.currentMp);
        c.updatedAt = Instant.now();
        characters.saveAndFlush(c);
        if (b.tiers() != null) replaceTiers(c.id, b.tiers());
        CharacterView after = view(c);
        audit.record(p, "character", String.valueOf(c.id), "update", EngineJson.write(b), EngineJson.write(before), EngineJson.write(after));
        return after;
    }

    private static void checkMp(int max, int current) {
        if (max < 0) throw ApiException.badRequest("api.bad.maxMpNegative");
        if (current < 0 || current > max) throw ApiException.badRequest("api.bad.currentMpRange");
    }

    private void replaceTiers(Long characterId, Map<String, Integer> wanted) {
        if (wanted == null) return;
        for (var e : wanted.entrySet()) {
            if (e.getKey().equals("alapvarazslatok")) throw ApiException.badRequest("api.bad.noTierForBasic");
            if (!schools.existsById(e.getKey())) throw ApiException.badRequest("api.bad.unknownSchool", e.getKey());
            if (e.getValue() == null || e.getValue() < 0 || e.getValue() > 5) throw ApiException.badRequest("api.bad.tierRange", e.getKey());
        }
        tiers.deleteAllByCharacterId(characterId);
        tiers.flush();
        wanted.forEach((school, tier) -> tiers.save(new CharacterSchoolTier(characterId, school, tier)));
        tiers.flush();
    }
}
