package hu.mpcalc.backend.web;

import hu.mpcalc.backend.service.CatalogService;
import hu.mpcalc.backend.service.CharacterService;
import hu.mpcalc.backend.service.CharacterService.CharacterBody;
import hu.mpcalc.backend.service.CharacterService.CharacterView;
import hu.mpcalc.backend.service.SavedSpellService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CharacterController {
    private final CharacterService characters;
    private final CatalogService catalog;
    private final SavedSpellService spells;
    private final Sessions sessions;

    public CharacterController(CharacterService characters, CatalogService catalog, SavedSpellService spells, Sessions sessions) {
        this.characters = characters; this.catalog = catalog; this.spells = spells; this.sessions = sessions;
    }

    @GetMapping("/characters")
    List<CharacterView> list(HttpSession s) { return characters.list(sessions.require(s)); }

    @PostMapping("/characters")
    @ResponseStatus(HttpStatus.CREATED)
    CharacterView create(@RequestBody CharacterBody b, HttpSession s) { return characters.create(sessions.require(s), b); }

    @GetMapping("/characters/{id}")
    CharacterView get(@PathVariable Long id, HttpSession s) {
        return characters.view(characters.owned(sessions.require(s), id));
    }

    @PutMapping("/characters/{id}")
    CharacterView update(@PathVariable Long id, @RequestBody CharacterBody b, HttpSession s) {
        return characters.update(sessions.require(s), id, b);
    }

    @GetMapping("/characters/{id}/mosaics")
    ResponseEntity<String> mosaics(@PathVariable Long id, HttpSession s) {
        return Sessions.json(catalog.availableMosaics(sessions.require(s), id));
    }

    @GetMapping("/characters/{id}/spells")
    ResponseEntity<String> spells(@PathVariable Long id, HttpSession s) { return Sessions.json(spells.list(sessions.require(s), id)); }

    @PostMapping("/characters/{id}/spells")
    ResponseEntity<String> saveSpell(@PathVariable Long id, @RequestBody String body, HttpSession s) {
        return ResponseEntity.status(HttpStatus.CREATED).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(spells.create(sessions.require(s), id, body));
    }

    @GetMapping("/spells/{id}")
    ResponseEntity<String> loadSpell(@PathVariable Long id, HttpSession s) { return Sessions.json(spells.get(sessions.require(s), id)); }

    @PutMapping("/spells/{id}")
    ResponseEntity<String> updateSpell(@PathVariable Long id, @RequestBody String body, HttpSession s) {
        return Sessions.json(spells.update(sessions.require(s), id, body));
    }

    @DeleteMapping("/spells/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSpell(@PathVariable Long id, HttpSession s) { spells.delete(sessions.require(s), id); }
}
