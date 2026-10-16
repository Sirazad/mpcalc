package hu.mpcalc.backend.service;

import hu.mpcalc.backend.model.AuditLog;
import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.repo.Repositories.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Every write calls this inside its own transaction (MANDATORY: it never opens one itself). */
@Service
public class AuditService {
    private final AuditLogRepository log;

    public AuditService(AuditLogRepository log) { this.log = log; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Player actor, String entityType, String entityId, String action, String request, String before, String after) {
        AuditLog a = new AuditLog();
        a.playerId = actor == null ? null : actor.id;
        a.playerName = actor == null ? null : actor.name;
        a.entityType = entityType;
        a.entityId = entityId;
        a.action = action;
        a.request = request;
        a.dataBefore = before;
        a.dataAfter = after;
        log.save(a);
    }
}
