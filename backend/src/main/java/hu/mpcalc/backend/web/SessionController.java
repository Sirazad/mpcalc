package hu.mpcalc.backend.web;

import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.service.PlayerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Interim login ("pick your name"). */
@RestController
public class SessionController {
    public record NameBody(String name) {}
    public record PlayerView(Long id, String name, boolean admin) {}

    private final PlayerService players;
    private final Sessions sessions;

    public SessionController(PlayerService players, Sessions sessions) { this.players = players; this.sessions = sessions; }

    @GetMapping("/players")
    List<String> names() { return players.names(); }

    @PostMapping("/players")
    @ResponseStatus(HttpStatus.CREATED)
    PlayerView create(@RequestBody NameBody b) { return view(players.create(b.name())); }

    @PostMapping("/session")
    PlayerView login(@RequestBody NameBody b, HttpSession session) {
        Player p = players.byName(b.name());
        session.setAttribute(Sessions.KEY, p.id);
        return view(p);
    }

    @GetMapping("/session")
    PlayerView current(HttpSession session) { return view(sessions.require(session)); }

    @DeleteMapping("/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpSession session) { session.invalidate(); }

    private static PlayerView view(Player p) { return new PlayerView(p.id, p.name, p.admin); }
}
