package hu.mpcalc.backend.service;

import hu.mpcalc.backend.engine.EngineJson;
import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.repo.Repositories.PlayerRepository;
import hu.mpcalc.backend.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Interim login: "pick your name". Not safe on the internet (see the plan backlog); replaced by Google login later. */
@Service
public class PlayerService {
    private final PlayerRepository players;
    private final AuditService audit;

    public PlayerService(PlayerRepository players, AuditService audit) { this.players = players; this.audit = audit; }

    @Transactional(readOnly = true)
    public List<String> names() { return players.findAll().stream().map(p -> p.name).sorted().toList(); }

    @Transactional(readOnly = true)
    public Player byName(String name) {
        return players.findByName(name).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "api.unknownPlayer"));
    }

    @Transactional(readOnly = true)
    public Player byId(Long id) {
        return players.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "api.unknownPlayer"));
    }

    /** New players are never admins: the admin flag is set directly in the database. */
    @Transactional
    public Player create(String name) {
        if (name == null || name.isBlank()) throw ApiException.badRequest("api.bad.nameRequired");
        Player p = new Player();
        p.name = name.trim();
        p.admin = false;
        p = players.saveAndFlush(p);
        audit.record(p, "player", String.valueOf(p.id), "create", EngineJson.write(Map.of("name", p.name)), null,
                EngineJson.write(Map.of("id", p.id, "name", p.name, "admin", false)));
        return p;
    }
}
