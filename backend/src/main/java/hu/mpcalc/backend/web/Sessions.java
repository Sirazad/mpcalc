package hu.mpcalc.backend.web;

import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.service.PlayerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Reads the logged-in player from the HTTP session (interim login). */
@Component
public class Sessions {
    static final String KEY = "playerId";
    private final PlayerService players;

    public Sessions(PlayerService players) { this.players = players; }

    public Player require(HttpSession s) {
        Object id = s.getAttribute(KEY);
        if (!(id instanceof Long l)) throw new ApiException(HttpStatus.UNAUTHORIZED, "api.notLoggedIn");
        return players.byId(l);
    }

    public static ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
