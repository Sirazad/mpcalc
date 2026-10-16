package hu.mpcalc.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.repo.Repositories.MosaicRepository;
import hu.mpcalc.engine.Fraction;
import hu.mpcalc.engine.Json;
import hu.mpcalc.engine.SpellResult;
import hu.mpcalc.engine.model.CharacterSheet;
import hu.mpcalc.engine.model.Form;
import hu.mpcalc.engine.model.Mosaic;
import hu.mpcalc.engine.model.Selection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The search is checked against a brute force that tries EVERY power step and extra MP directly through the engine, so the
 * shortcut (largest affordable k from two engine calls) cannot silently miss a better option.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${MPCALC_IT_DB_URL:jdbc:postgresql://localhost:5432/mpcalc_backend_it}",
        "spring.datasource.username=${MPCALC_IT_DB_USER:${user.name}}",
        "spring.datasource.password=${MPCALC_IT_DB_PASSWORD:}"})
class SpellSearchTest {
    @Autowired PlayerService players;
    @Autowired CharacterService characters;
    @Autowired SpellService spells;
    @Autowired SpellSearch search;
    @Autowired MosaicRepository mosaics;

    /** (E, duration seconds, casting seconds) of one brute-force option. */
    record Point(BigDecimal e, long duration, long casting, Fraction mp) {}

    private Player player() { return players.create("search-" + UUID.randomUUID()); }

    private long character(Player p, int maxMp) {
        return characters.create(p, new CharacterService.CharacterBody("Kereső", maxMp, maxMp, Map.of("mental", 3, "ido", 2))).id();
    }

    private Mosaic mosaic(String id) { return Json.parseMosaic(mosaics.findById(id).orElseThrow().definition); }

    private List<Point> bruteForce(String candidate, int budget, int maxMpOfCharacter) {
        Mosaic cand = mosaic(candidate);
        List<Mosaic> exts = new ArrayList<>();
        exts.add(null);
        for (String id : List.of("ido-nyujtas-kor", "ido-nyujtas-perc", "ido-nyujtas-ora", "ido-nyujtas-nap", "ido-nyujtas-ev")) exts.add(mosaic(id));
        CharacterSheet sheet = new CharacterSheet(Map.of("mental", 3, "ido", 2), BigDecimal.valueOf(budget));
        List<Form> forms = spells.allForms();
        List<Point> out = new ArrayList<>();
        for (Mosaic ext : exts) {
            for (int x = 0; x <= (ext == null ? 0 : budget); x++) {
                for (int k = 0; k <= budget; k++) {
                    List<Selection> sel = new ArrayList<>();
                    sel.add(Selection.of(cand).withK(k));
                    if (ext != null) sel.add(Selection.of(ext).withExtraMp(x));
                    SpellResult r = spells.evaluate(sel, sheet, forms);
                    if (!r.valid() || !r.totalMp().isDefined() || r.totalMp().value().compareTo(Fraction.of(budget)) > 0) continue;
                    var m = r.mosaics().get(0);
                    if (!m.e().isDefined() || !r.duration().isDefined() || !r.duration().value().isTime() || !r.castingTime().isDefined()) continue;
                    var d = r.duration().value().amounts.seconds();
                    var c = r.castingTime().value().amounts.seconds();
                    if (!d.isDefined() || !c.isDefined()) continue;
                    out.add(new Point(m.e().value(), d.value(), c.value(), r.totalMp().value()));
                }
            }
        }
        return out;
    }

    private SpellSearch.Found run(Player p, long id, SpellSearch.Objective o, BigDecimal minE, Long minDur, Long maxCast, int budget) {
        return search.search(p, id, new SpellSearch.Request(List.of("mental-beszed-iras"), o, minE, minDur, maxCast, budget, Map.<String, JsonNode>of(), true, 50));
    }

    @Test
    void strongestWithAtLeastAnHourMatchesBruteForce() {
        Player p = player();
        long id = character(p, 100);
        for (int budget : new int[] {8, 30, 40, 60}) {
            List<Point> bf = bruteForce("mental-beszed-iras", budget, 100).stream().filter(x -> x.duration() >= 3600).toList();
            var found = run(p, id, SpellSearch.Objective.MAX_E, null, 3600L, null, budget);
            if (bf.isEmpty()) { assertNull(found.best(), "budget " + budget); continue; }
            BigDecimal bestE = bf.stream().map(Point::e).max(Comparator.naturalOrder()).orElseThrow();
            assertNotNull(found.best(), "budget " + budget);
            assertEquals(0, bestE.compareTo(found.best().e()), "budget " + budget + " strongest E");
            assertTrue(found.best().durationSeconds() >= 3600);
            assertTrue(found.best().totalMp().compareTo(Fraction.of(budget)) <= 0);
        }
    }

    @Test
    void longestWithAMinimumEMatchesBruteForce() {
        Player p = player();
        long id = character(p, 100);
        for (int budget : new int[] {10, 25, 40}) {
            List<Point> bf = bruteForce("mental-beszed-iras", budget, 100).stream().filter(x -> x.e().compareTo(BigDecimal.valueOf(3)) >= 0).toList();
            var found = run(p, id, SpellSearch.Objective.MAX_DURATION, BigDecimal.valueOf(3), null, null, budget);
            if (bf.isEmpty()) { assertNull(found.best(), "budget " + budget); continue; }
            long longest = bf.stream().mapToLong(Point::duration).max().orElseThrow();
            assertEquals(longest, found.best().durationSeconds(), "budget " + budget + " longest duration");
            assertTrue(found.best().e().compareTo(BigDecimal.valueOf(3)) >= 0);
        }
    }

    @Test
    void fastestMatchesBruteForce() {
        Player p = player();
        long id = character(p, 100);
        List<Point> bf = bruteForce("mental-beszed-iras", 30, 100).stream().filter(x -> x.e().compareTo(BigDecimal.valueOf(5)) >= 0).toList();
        var found = run(p, id, SpellSearch.Objective.MIN_CASTING_TIME, BigDecimal.valueOf(5), null, null, 30);
        assertEquals(bf.stream().mapToLong(Point::casting).min().orElseThrow(), found.best().castingSeconds());
    }

    @Test
    void theTradeOffSetEqualsTheBruteForceParetoSet() {
        Player p = player();
        long id = character(p, 100);
        for (int budget : new int[] {20, 45}) {
            List<Point> all = bruteForce("mental-beszed-iras", budget, 100);
            Set<String> expected = new TreeSet<>();
            for (Point o : all) {
                boolean dominated = all.stream().anyMatch(q -> q.e().compareTo(o.e()) >= 0 && q.duration() >= o.duration()
                        && (q.e().compareTo(o.e()) > 0 || q.duration() > o.duration()));
                if (!dominated) expected.add(o.e().stripTrailingZeros().toPlainString() + "/" + o.duration());
            }
            var found = run(p, id, SpellSearch.Objective.MAX_E, null, null, null, budget);
            Set<String> actual = new TreeSet<>();
            found.pareto().forEach(o -> actual.add(o.e().stripTrailingZeros().toPlainString() + "/" + o.durationSeconds()));
            assertEquals(expected, actual, "budget " + budget);
        }
    }
}
