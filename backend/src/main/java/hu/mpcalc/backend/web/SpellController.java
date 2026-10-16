package hu.mpcalc.backend.web;

import hu.mpcalc.backend.service.AdminService;
import hu.mpcalc.backend.service.CatalogService;
import hu.mpcalc.backend.service.SpellService;
import hu.mpcalc.backend.service.ValidationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Reference data, calculate, cast and admin edits. Bodies and results carry engine types, so they are raw JSON text. */
@RestController
public class SpellController {
    private final SpellService spells;
    private final CatalogService catalog;
    private final AdminService admin;
    private final ValidationService validation;
    private final Sessions sessions;

    public SpellController(SpellService spells, CatalogService catalog, AdminService admin, ValidationService validation, Sessions sessions) {
        this.spells = spells; this.catalog = catalog; this.admin = admin; this.validation = validation; this.sessions = sessions;
    }

    @GetMapping("/schools")
    ResponseEntity<String> schools(HttpSession s) { sessions.require(s); return Sessions.json(catalog.schools()); }

    @GetMapping("/forms")
    ResponseEntity<String> forms(HttpSession s) { sessions.require(s); return Sessions.json(catalog.forms()); }

    @GetMapping("/material-groups")
    ResponseEntity<String> materialGroups(HttpSession s) { sessions.require(s); return Sessions.json(catalog.materialGroups()); }

    @GetMapping("/texts")
    ResponseEntity<String> texts(HttpSession s) { sessions.require(s); return Sessions.json(catalog.referenceTexts()); }

    @PostMapping("/calculate")
    ResponseEntity<String> calculate(@RequestBody String body, HttpSession s) { return Sessions.json(spells.calculate(sessions.require(s), body)); }

    @PostMapping("/cast")
    ResponseEntity<String> cast(@RequestBody String body, HttpSession s) { return Sessions.json(spells.cast(sessions.require(s), body)); }

    @PostMapping("/validate")
    ResponseEntity<String> validate(@RequestBody String body, HttpSession s) { return Sessions.json(validation.validate(sessions.require(s), body)); }

    @PutMapping("/admin/mosaics/{id}")
    ResponseEntity<String> updateMosaic(@PathVariable String id, @RequestBody String body, HttpSession s) {
        return Sessions.json(admin.updateMosaic(sessions.require(s), id, body));
    }
}
