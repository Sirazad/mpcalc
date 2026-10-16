package hu.mpcalc.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import hu.mpcalc.backend.engine.EngineJson;
import hu.mpcalc.backend.model.MosaicEntity;
import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.repo.Repositories.MosaicRepository;
import hu.mpcalc.backend.web.ApiException;
import hu.mpcalc.engine.Json;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AdminService {
    private final MosaicRepository mosaics;
    private final AuditService audit;

    public AdminService(MosaicRepository mosaics, AuditService audit) { this.mosaics = mosaics; this.audit = audit; }

    /**
     * Admin replaces the whole definition of an existing mosaic. The body must be a valid definition (the engine parses it)
     * with the same id. The indexed columns are re-derived from it; the database constraints check they agree.
     * Creating whole new mosaics is not part of this endpoint (open question: admin scope).
     */
    @Transactional
    public String updateMosaic(Player admin, String id, String body) {
        if (!admin.admin) throw new ApiException(HttpStatus.FORBIDDEN, "api.adminRequired");
        MosaicEntity m = mosaics.findById(id).orElseThrow(() -> ApiException.notFound("api.notFound.mosaic", id));
        JsonNode d = EngineJson.parse(body);
        if (!id.equals(d.path("id").asText())) throw ApiException.badRequest("api.bad.definitionIdMismatch");
        Json.parseMosaic(body);
        String before = m.definition;
        m.name = d.path("name").asText();
        m.school = d.path("school").asText();
        m.subgroup = d.path("subgroup").isNull() || d.path("subgroup").isMissingNode() ? null : d.get("subgroup").asText();
        m.tier = d.path("tier").isNull() || d.path("tier").isMissingNode() ? null : (short) d.get("tier").asInt();
        m.statusState = d.path("status").path("state").asText();
        m.costPattern = d.path("cost").path("pattern").asText();
        m.source = d.path("source").asText();
        m.definition = d.toString();
        m.updatedAt = Instant.now();
        mosaics.saveAndFlush(m);
        audit.record(admin, "mosaic", id, "update", body, before, m.definition);
        return m.definition;
    }
}
