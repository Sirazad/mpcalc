package hu.mpcalc.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import hu.mpcalc.backend.model.GameCharacter;
import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.web.ApiException;
import hu.mpcalc.engine.Fraction;
import hu.mpcalc.engine.Json;
import hu.mpcalc.engine.Maybe;
import hu.mpcalc.engine.Msg;
import hu.mpcalc.engine.SpellResult;
import hu.mpcalc.engine.model.CharacterSheet;
import hu.mpcalc.engine.model.Form;
import hu.mpcalc.engine.model.Mosaic;
import hu.mpcalc.engine.model.Selection;
import hu.mpcalc.engine.model.TimeValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * Searches for the best spell by what the player asks for: strongest (E), longest (effect duration) or fastest (casting time),
 * within an MP budget and optional limits. It adds no rules: every number comes from the engine.
 *
 * Choices: the candidate mosaic, its power step k, and at most one time extension (a modifier with a duration per extra MP)
 * with extra MP. Cost is linear in k and in the extra MP, so the largest affordable k is computed from two engine calls
 * and the reported option is then recalculated by the engine (and lowered until it fits, as a safety).
 */
@Service
public class SpellSearch {
    public enum Objective { MAX_E, MAX_DURATION, MIN_CASTING_TIME }

    public static final int MAX_BUDGET = 1000;

    public record Request(List<String> candidateIds, Objective objective, BigDecimal minE, Long minDurationSeconds,
                          Long maxCastingSeconds, Integer mpBudget, Map<String, JsonNode> inputs, boolean allowExtension, int limit) {}

    public record Option(String mosaicId, String extensionId, int k, int extraMp, BigDecimal e, String unit, Fraction totalMp,
                         Fraction mpLeft, Maybe<TimeValue> duration, Long durationSeconds, Maybe<TimeValue> castingTime,
                         Long castingSeconds, List<Map<String, Object>> selections) {}

    public record Skipped(String mosaicId, List<Msg> errors) {}

    public record Found(String objective, int mpBudget, int considered, Option best, List<Option> pareto, List<Skipped> skipped, Msg reason) {}

    private final CharacterService characters;
    private final CatalogService catalog;
    private final SpellService spells;

    public SpellSearch(CharacterService characters, CatalogService catalog, SpellService spells) {
        this.characters = characters; this.catalog = catalog; this.spells = spells;
    }

    @Transactional(readOnly = true)
    public Found search(Player p, long characterId, Request r) {
        GameCharacter c = characters.owned(p, characterId);
        int budget = r.mpBudget() == null ? c.currentMp : r.mpBudget();
        if (budget < 0 || budget > MAX_BUDGET) throw ApiException.badRequest("api.mcp.budgetRange", MAX_BUDGET);
        if (r.candidateIds() == null || r.candidateIds().isEmpty()) throw ApiException.badRequest("api.mcp.noCandidates");
        if (r.objective() == null) throw ApiException.badRequest("api.mcp.objectiveRequired");

        Map<String, Mosaic> available = new LinkedHashMap<>();
        catalog.available(c).forEach(m -> available.put(m.id, Json.parseMosaic(m.definition)));
        List<Mosaic> extensions = !r.allowExtension() ? List.of() : available.values().stream()
                .filter(m -> m.modifier != null && m.modifier.durationPerExtraMp != null && m.status != null && !"incomplete".equals(m.status.state))
                .toList();
        CharacterSheet sheet = new CharacterSheet(characters.tiersOf(c.id), BigDecimal.valueOf(budget));
        List<Form> forms = spells.allForms();
        Map<String, JsonNode> inputs = r.inputs() == null ? Map.of() : r.inputs();

        List<Option> feasible = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        int considered = 0;
        for (String id : new LinkedHashSet<>(r.candidateIds())) {
            Mosaic cand = available.get(id);
            if (cand == null) throw ApiException.badRequest("api.mcp.candidateNotAvailable", id);
            if (cand.modifier != null) { skipped.add(new Skipped(id, List.of(new Msg("api.mcp.candidateIsModifier", List.of(id), null)))); continue; }
            SpellResult probe = eval(cand, inputs, 0, null, 0, sheet, forms);
            if (!probe.valid()) { skipped.add(new Skipped(id, probe.errors())); continue; }
            boolean hasStep = cand.power != null && cand.power.step != null;
            List<Mosaic> choices = new ArrayList<>();
            choices.add(null);
            choices.addAll(extensions);
            for (Mosaic ext : choices) {
                for (int x = 0; x <= (ext == null ? 0 : budget); x++) {
                    SpellResult r0 = eval(cand, inputs, 0, ext, x, sheet, forms);
                    considered++;
                    if (!r0.valid()) break;                       // for example the extension needs a tier the character lacks
                    if (!r0.totalMp().isDefined()) continue;
                    Fraction mp0 = r0.totalMp().value();
                    if (mp0.compareTo(Fraction.of(budget)) > 0) break;   // more extra MP only costs more
                    int k = 0;
                    if (hasStep) {
                        SpellResult r1 = eval(cand, inputs, 1, ext, x, sheet, forms);
                        if (r1.valid() && r1.totalMp().isDefined()) {
                            Fraction step = r1.totalMp().value().subtract(mp0);
                            if (step.signum() > 0) {
                                Fraction q = Fraction.of(budget).subtract(mp0).divide(step);
                                k = q.numerator().divide(q.denominator()).min(BigInteger.valueOf(100_000)).intValue();
                            }
                        }
                    }
                    SpellResult res = eval(cand, inputs, k, ext, x, sheet, forms);
                    while (k > 0 && !fits(res, budget)) res = eval(cand, inputs, --k, ext, x, sheet, forms);
                    if (!res.valid() || !fits(res, budget)) continue;
                    Option o = option(cand, ext, k, x, res, budget, inputs);
                    if (satisfies(o, r)) feasible.add(o);
                }
            }
        }

        Option best = feasible.stream().min(comparator(r.objective())).orElse(null);
        List<Option> pareto = pareto(feasible, Math.max(1, r.limit()));
        Msg reason = best == null ? new Msg("api.mcp.noOption", List.of(), null) : null;
        return new Found(r.objective().name(), budget, considered, best, pareto, skipped, reason);
    }

    private SpellResult eval(Mosaic cand, Map<String, JsonNode> inputs, int k, Mosaic ext, int extraMp, CharacterSheet sheet, List<Form> forms) {
        List<Selection> sel = new ArrayList<>();
        sel.add(Selection.of(cand).withInputs(inputs).withK(k));
        if (ext != null) sel.add(Selection.of(ext).withExtraMp(extraMp));
        return spells.evaluate(sel, sheet, forms);
    }

    private static boolean fits(SpellResult r, int budget) {
        return r.totalMp().isDefined() && r.totalMp().value().compareTo(Fraction.of(budget)) <= 0;
    }

    private static Option option(Mosaic cand, Mosaic ext, int k, int extraMp, SpellResult res, int budget, Map<String, JsonNode> inputs) {
        var first = res.mosaics().get(0);
        BigDecimal e = first.e().isDefined() ? first.e().value() : null;
        Fraction total = res.totalMp().value();
        List<Map<String, Object>> selections = new ArrayList<>();
        Map<String, Object> s0 = new LinkedHashMap<>();
        s0.put("mosaicId", cand.id); s0.put("inputs", inputs); s0.put("k", k); s0.put("options", List.of()); s0.put("extraMp", 0);
        selections.add(s0);
        if (ext != null) {
            Map<String, Object> s1 = new LinkedHashMap<>();
            s1.put("mosaicId", ext.id); s1.put("inputs", Map.of()); s1.put("k", 0); s1.put("options", List.of()); s1.put("extraMp", extraMp);
            selections.add(s1);
        }
        return new Option(cand.id, ext == null ? null : ext.id, k, extraMp, e, first.powerUnit(), total,
                Fraction.of(budget).subtract(total), res.duration(), seconds(res.duration()), res.castingTime(), seconds(res.castingTime()), selections);
    }

    /** Seconds of a time value, or null when it has none (one-off, permanent, undefined, or years). */
    private static Long seconds(Maybe<TimeValue> t) {
        if (!t.isDefined() || !t.value().isTime()) return null;
        Maybe<Long> s = t.value().amounts.seconds();
        return s.isDefined() ? s.value() : null;
    }

    private static boolean satisfies(Option o, Request r) {
        if (r.minE() != null && (o.e() == null || o.e().compareTo(r.minE()) < 0)) return false;
        if (r.minDurationSeconds() != null && (o.durationSeconds() == null || o.durationSeconds() < r.minDurationSeconds())) return false;
        if (r.maxCastingSeconds() != null && (o.castingSeconds() == null || o.castingSeconds() > r.maxCastingSeconds())) return false;
        return switch (r.objective()) {
            case MAX_E -> o.e() != null;
            case MAX_DURATION -> o.durationSeconds() != null;
            case MIN_CASTING_TIME -> o.castingSeconds() != null;
        };
    }

    /** Best first. Ties: stronger, then longer, then cheaper. */
    private static Comparator<Option> comparator(Objective obj) {
        Comparator<Option> byE = Comparator.comparing((Option o) -> o.e() == null ? BigDecimal.valueOf(-1) : o.e()).reversed();
        Comparator<Option> byDuration = Comparator.comparing((Option o) -> o.durationSeconds() == null ? -1L : o.durationSeconds()).reversed();
        Comparator<Option> byCasting = Comparator.comparing((Option o) -> o.castingSeconds() == null ? Long.MAX_VALUE : o.castingSeconds());
        Comparator<Option> byCost = Comparator.comparing(Option::totalMp);
        return switch (obj) {
            case MAX_E -> byE.thenComparing(byDuration).thenComparing(byCost);
            case MAX_DURATION -> byDuration.thenComparing(byE).thenComparing(byCost);
            case MIN_CASTING_TIME -> byCasting.thenComparing(byE).thenComparing(byDuration).thenComparing(byCost);
        };
    }

    /** The options no other option beats on both E and duration (the time-versus-power trade-off), strongest first. */
    static List<Option> pareto(List<Option> options, int limit) {
        List<Option> withBoth = options.stream().filter(o -> o.e() != null && o.durationSeconds() != null).toList();
        List<Option> front = new ArrayList<>();
        for (Option o : withBoth) {
            boolean dominated = withBoth.stream().anyMatch(p -> p != o
                    && p.e().compareTo(o.e()) >= 0 && p.durationSeconds() >= o.durationSeconds()
                    && (p.e().compareTo(o.e()) > 0 || p.durationSeconds() > o.durationSeconds()));
            if (!dominated) front.add(o);
        }
        front.sort(Comparator.comparing((Option o) -> o.e()).reversed().thenComparing(Comparator.comparing(Option::durationSeconds).reversed()).thenComparing(Option::totalMp));
        List<Option> unique = new ArrayList<>();
        for (Option o : front) {
            boolean seen = unique.stream().anyMatch(u -> u.e().compareTo(o.e()) == 0 && u.durationSeconds().equals(o.durationSeconds()));
            if (!seen) unique.add(o);
        }
        return unique.size() > limit ? unique.subList(0, limit) : unique;
    }
}
