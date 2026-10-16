package hu.mpcalc.backend.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.mpcalc.backend.engine.EngineJson;
import hu.mpcalc.backend.model.FormEntity;
import hu.mpcalc.backend.model.GameCharacter;
import hu.mpcalc.backend.model.MosaicEntity;
import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.repo.Repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Read-only reference data. Results are raw JSON text. */
@Service
public class CatalogService {
    private final SchoolRepository schools;
    private final MosaicRepository mosaics;
    private final FormRepository forms;
    private final MaterialGroupRepository groups;
    private final CharacterService characters;

    public CatalogService(SchoolRepository schools, MosaicRepository mosaics, FormRepository forms, MaterialGroupRepository groups, CharacterService characters) {
        this.schools = schools; this.mosaics = mosaics; this.forms = forms; this.groups = groups; this.characters = characters;
    }

    @Transactional(readOnly = true)
    public String schools() {
        ArrayNode arr = EngineJson.mapper().createArrayNode();
        schools.findAll().stream().sorted((a, b) -> a.id.compareTo(b.id)).forEach(s -> {
            ObjectNode o = arr.addObject();
            o.put("id", s.id); o.put("name", s.name); o.put("hasTiers", s.hasTiers);
        });
        return arr.toString();
    }

    @Transactional(readOnly = true)
    public String forms() {
        ArrayNode arr = EngineJson.mapper().createArrayNode();
        for (FormEntity f : forms.findAll()) {
            ObjectNode o = arr.addObject();
            o.put("id", f.id); o.put("name", f.name); o.put("tier", f.tier);
            o.set("duration", EngineJson.parse(f.duration));
        }
        return arr.toString();
    }

    @Transactional(readOnly = true)
    public String materialGroups() {
        ArrayNode arr = EngineJson.mapper().createArrayNode();
        groups.findAll().stream().sorted((a, b) -> a.id.compareTo(b.id)).forEach(g -> {
            ObjectNode o = arr.addObject();
            o.put("id", g.id); o.put("name", g.name);
            ArrayNode ex = o.putArray("examples");
            for (String e : g.examples) ex.add(e);
        });
        return arr.toString();
    }

    /**
     * Mosaics the character may use: mosaic tier at most the character's tier in that school (alapvarazslatok: always tier 0).
     * A mosaic without a tier (incomplete rows) is left out. Incomplete mosaics with a tier are included; the engine refuses them.
     */
    @Transactional(readOnly = true)
    public String availableMosaics(Player p, Long characterId) {
        GameCharacter c = characters.owned(p, characterId);
        ObjectNode out = EngineJson.mapper().createObjectNode();
        ArrayNode list = out.putArray("mosaics");
        ObjectNode versions = out.putObject("versions");
        for (MosaicEntity m : available(c)) {
            list.add(EngineJson.parse(m.definition));
            versions.put(m.id, version(m));
        }
        return out.toString();
    }

    /** Mosaics the character may use, ordered by school, tier and name. */
    @Transactional(readOnly = true)
    public List<MosaicEntity> available(GameCharacter c) {
        Map<String, Integer> tiers = characters.tiersOf(c.id);
        return mosaics.findAllByOrderBySchoolAscTierAscNameAsc().stream()
                .filter(m -> m.tier != null && tiers.getOrDefault(m.school, 0) >= m.tier).toList();
    }

    /** Changes whenever the mosaic is edited (the admin edit sets updated_at). */
    public static String version(MosaicEntity m) { return String.valueOf(m.updatedAt); }
}
