package hu.mpcalc.engine;

import com.fasterxml.jackson.databind.JsonNode;
import hu.mpcalc.engine.model.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/** Rules engine: pure calculation, no database, no Spring. All MP arithmetic is exact (Fraction). */
public final class SpellCalculator {
    /** A spell cast through a Zóna varázsjel costs this many MP more (once) and takes one kör more. */
    public static final int ZONE_SIGN_MP = 5;

    private static final Set<String> GROUPS = Set.of("I", "II", "III");

    /** Internal per-mosaic evaluation before the spell-level modifiers are applied. */
    private static final class Eval {
        Selection sel; Mosaic m;
        final List<Msg> errors = new ArrayList<>();
        final List<Msg> warnings = new ArrayList<>();
        Maybe<Fraction> mp = Maybe.undefined(MsgCode.NOT_CALCULATED);
        Maybe<BigDecimal> e = Maybe.undefined(MsgCode.NOT_CALCULATED);
        Maybe<String> effect = Maybe.undefined(MsgCode.NOT_CALCULATED);
        Maybe<Resistance> resistance = Maybe.undefined(MsgCode.NOT_CALCULATED);
        Maybe<TimeValue> casting = Maybe.undefined(MsgCode.NOT_CALCULATED);
        final List<CastingPart> castingParts = new ArrayList<>();
        boolean infinitePower;
        boolean includedInWrapper;
        Maybe<TimeValue> baseDuration = Maybe.undefined(MsgCode.NOT_CALCULATED);
        Maybe<TimeValue> modDuration; // only for modifiers
        boolean isModifier() { return m.modifier != null; }
        boolean isWrapper() { return m.cost != null && "wrapper".equals(m.cost.pattern); }
        boolean refused() { return !errors.isEmpty(); }
    }

    public SpellResult calculate(SpellRequest req) {
        List<Eval> evals = new ArrayList<>();
        for (Selection s : req.selections()) evals.add(evaluate(s, req));

        // A wrapper mosaic (Végrehajtó varázskör, ...) costs its base plus a factor times the MP of the OTHER mosaics, in any order of adding.
        // Its casting time includes the time of those mosaics, so theirs is not added; their durations apply when the wrapper acts
        // (or keep applying, for a wrapper with keepsDuration). A wrapper may accept only some mosaics (wraps).
        for (Eval w : evals) {
            if (w.refused() || !w.isWrapper() || w.m.cost.wraps == null || !w.m.cost.wraps.isArray() || w.m.cost.wraps.isEmpty()) continue;
            for (Eval o : evals)
                if (o != w && !o.isWrapper() && !o.refused() && !accepts(w.m.cost.wraps, o.m))
                    w.errors.add(Msg.of(MsgCode.ERR_WRAPPER_WRONG_MOSAIC, o.m.name, acceptedLabels(w.m.cost.wraps)));
        }
        boolean anyWrapper = evals.stream().anyMatch(ev -> !ev.refused() && ev.isWrapper());
        boolean keepsDuration = evals.stream().anyMatch(ev -> !ev.refused() && ev.isWrapper() && Boolean.TRUE.equals(ev.m.cost.keepsDuration));
        for (Eval w : evals) {
            if (w.refused() || !w.isWrapper()) continue;
            if (w.mp.isDefined()) {
                Fraction sum = Fraction.ZERO; Maybe<Fraction> bad = null;
                for (Eval o : evals) {
                    if (o == w || o.isWrapper() || o.refused()) continue;
                    Maybe<Fraction> om = w.m.cost.innerE != null ? mpAtE(o, req, w.m.cost.innerE) : o.mp;
                    if (!om.isDefined()) { bad = Maybe.undefined(om.reason().about(o.m.name)); break; }
                    sum = sum.add(om.value());
                }
                if (bad != null) w.mp = bad;
                else if (w.m.cost.factor == null) w.mp = Maybe.undefined(MsgCode.RATE_UNDEFINED);
                else w.mp = Maybe.of(w.mp.value().add(Fraction.of(w.m.cost.factor).multiply(sum)));
            }
        }
        if (anyWrapper) for (Eval o : evals) if (!o.refused() && !o.isWrapper()) o.includedInWrapper = true;

        // Spell-level modifier duration (applies to every non-modifier mosaic).
        Maybe<TimeValue> modTotal = null;
        for (Eval ev : evals) {
            if (!ev.isModifier()) continue;
            Maybe<TimeValue> d = ev.refused() ? Maybe.undefined(MsgCode.MODIFIER_REFUSED) : ev.modDuration;
            modTotal = modTotal == null ? d : TimeValue.add(modTotal, d);
        }

        List<MosaicResult> results = new ArrayList<>();
        List<Maybe<TimeValue>> effDurations = new ArrayList<>();
        for (Eval ev : evals) {
            Maybe<TimeValue> dur;
            if (ev.refused()) dur = Maybe.undefined(MsgCode.MOSAIC_REFUSED);
            else if (ev.isModifier()) dur = Maybe.undefined(MsgCode.MODIFIER_ONLY_ADDS_DURATION);
            else {
                dur = ev.baseDuration;
                if (modTotal != null) {
                    boolean addsNothing = modTotal.isDefined() && modTotal.value().isTime() && modTotal.value().amounts.isZero();
                    if (!addsNothing) dur = TimeValue.add(dur, modTotal);
                }
                if (ev.isWrapper() && Boolean.TRUE.equals(ev.m.cost.keepsDuration)) dur = Maybe.undefined(MsgCode.DURATION_FROM_WRAPPED);   // the duration is the wrapped mosaics'
                else if (!ev.includedInWrapper || keepsDuration) effDurations.add(dur);
            }
            Maybe<Fraction> mp = ev.refused() ? Maybe.undefined(MsgCode.MOSAIC_REFUSED) : ev.mp;
            results.add(new MosaicResult(ev.m.id, ev.m.name, ev.refused(), List.copyOf(ev.errors), List.copyOf(ev.warnings), mp,
                    ev.refused() ? Maybe.undefined(MsgCode.MOSAIC_REFUSED) : ev.e,
                    ev.m.power == null ? null : ev.m.power.unit,
                    ev.refused() ? Maybe.undefined(MsgCode.MOSAIC_REFUSED) : ev.effect,
                    ev.refused() ? Maybe.undefined(MsgCode.MOSAIC_REFUSED) : ev.resistance,
                    ev.refused() ? Maybe.undefined(MsgCode.MOSAIC_REFUSED) : ev.casting, dur,
                    ev.refused() ? List.of() : List.copyOf(ev.castingParts), ev.infinitePower, ev.includedInWrapper));
        }

        Set<Msg> warnings = new LinkedHashSet<>();
        List<Msg> errors = new ArrayList<>();
        for (Eval ev : evals) {
            for (Msg w : ev.warnings) warnings.add(w.about(ev.m.name));
            for (Msg er : ev.errors) errors.add(er.about(ev.m.name));
        }
        boolean valid = errors.isEmpty();
        boolean anyNonWhole = results.stream().anyMatch(r -> r.mp().isDefined() && !r.mp().value().isWhole());
        if (anyNonWhole) warnings.add(Msg.of(MsgCode.WARN_NOT_WHOLE_MP));

        // Totals
        Maybe<Fraction> total;
        if (evals.isEmpty()) total = Maybe.undefined(MsgCode.NO_MOSAIC_SELECTED);
        else {
            Fraction sum = Fraction.ZERO; Msg why = null;
            for (MosaicResult r : results) {
                if (r.includedInWrapper()) continue;   // its MP is already part of the wrapper's
                if (!r.mp().isDefined()) { why = r.mp().reason().about(r.name()); break; }
                sum = sum.add(r.mp().value());
            }
            total = why == null ? Maybe.of(sum) : Maybe.undefined(why);
        }
        // a spell cast through a Zóna varázsjel costs +5 MP once and takes +1 kör
        if (req.viaZoneSign() && total.isDefined()) total = Maybe.of(total.value().add(Fraction.of(ZONE_SIGN_MP)));
        Maybe<TimeValue> casting = evals.isEmpty() ? Maybe.undefined(MsgCode.NO_MOSAIC_SELECTED) : Maybe.of(TimeValue.time(TimeAmounts.zero()));
        for (MosaicResult r : results) if (!r.includedInWrapper()) casting = TimeValue.add(casting, r.castingTime());
        if (req.viaZoneSign()) casting = TimeValue.add(casting, Maybe.of(TimeValue.time(TimeAmounts.of(Map.of("kor", 1L)))));
        // a drawing needs a trance (10 minutes of meditation and 1 psi point), once per spell, unless the caster is already in one
        boolean tranceNeeded = evals.stream().anyMatch(ev -> !ev.refused() && ev.m.castingTime != null && "drawing".equals(ev.m.castingTime.label));
        boolean tranceApplied = tranceNeeded && !req.inTrance();
        if (tranceApplied) casting = TimeValue.add(casting, Maybe.of(TimeValue.time(TimeAmounts.of(Map.of("perc", 10L)))));
        Set<String> labels = new TreeSet<>();
        for (Eval ev : evals) if (!ev.refused() && !ev.includedInWrapper && ev.m.castingTime != null && ev.m.castingTime.label != null) labels.add(ev.m.castingTime.label);
        if (labels.size() > 1) warnings.add(Msg.of(MsgCode.WARN_MIXED_CASTING_TIMES));

        Maybe<TimeValue> duration = totalDuration(effDurations, evals);
        Maybe<Fraction> remaining = total.isDefined()
                ? Maybe.of(Fraction.of(req.character().currentMp).subtract(total.value()))
                : Maybe.undefined(MsgCode.TOTAL_MP_UNDEFINED);
        boolean enough = remaining.isDefined() && remaining.value().signum() >= 0;
        if (remaining.isDefined() && !enough) warnings.add(Msg.of(MsgCode.WARN_NOT_ENOUGH_MP));
        return new SpellResult(valid, results, total, casting, duration, remaining, enough, new ArrayList<>(warnings), errors,
                new SpellResult.Trance(tranceNeeded, tranceApplied), req.viaZoneSign());
    }

    private Maybe<TimeValue> totalDuration(List<Maybe<TimeValue>> eff, List<Eval> evals) {
        if (evals.stream().anyMatch(Eval::refused)) return Maybe.undefined(MsgCode.A_MOSAIC_REFUSED);
        if (eff.isEmpty()) return Maybe.undefined(MsgCode.NO_NON_MODIFIER_MOSAIC);
        for (Maybe<TimeValue> d : eff) if (!d.isDefined()) return d;
        TimeValue first = eff.get(0).value();
        for (Maybe<TimeValue> d : eff) {
            TimeValue t = d.value();
            if (t.kind != first.kind) return Maybe.undefined(MsgCode.DURATION_KINDS_DIFFER);
            if (t.isTime()) {
                Maybe<Integer> c = t.amounts.compareTo(first.amounts);
                if (!c.isDefined() || c.value() != 0) return Maybe.undefined(MsgCode.DURATIONS_DIFFER);
            }
        }
        return Maybe.of(first);
    }

    // ------------------------------------------------------------------ per mosaic

    private Eval evaluate(Selection s, SpellRequest req) {
        Eval ev = new Eval();
        ev.sel = s; ev.m = s.mosaic();
        Mosaic m = ev.m;
        List<String> missing = m.status == null || m.status.missing == null ? List.of() : m.status.missing;

        // status
        if (m.status == null || m.status.state == null) ev.errors.add(Msg.of(MsgCode.ERR_STATUS_MISSING));
        else if (m.status.state.equals("incomplete") || missing.contains("cost")) ev.errors.add(Msg.of(MsgCode.ERR_INCOMPLETE_MOSAIC));
        else if (!m.status.state.equals("complete") && !m.status.state.equals("partial")) ev.errors.add(Msg.of(MsgCode.ERR_UNKNOWN_STATUS, m.status.state));

        // tier
        if (m.school == null) ev.errors.add(Msg.of(MsgCode.ERR_SCHOOL_MISSING));
        else if (m.tier == null) ev.errors.add(Msg.of(MsgCode.ERR_TIER_MISSING));
        else {
            // a character without an entry for the school has tier 0 there (basic magic requires tier 0)
            int have = req.character().tiers.getOrDefault(m.school, 0);
            if (have < m.tier) ev.errors.add(Msg.of(MsgCode.ERR_TIER_BELOW, have, m.school, m.tier));
        }
        // extra requirements: tiers needed in other schools
        checkRequirements(ev, req, m.requires);

        // power step
        if (s.k() < 0) ev.errors.add(Msg.of(MsgCode.ERR_K_NEGATIVE));
        else if (s.k() > 0 && (m.power == null || m.power.step == null)) ev.errors.add(Msg.of(MsgCode.ERR_POWER_NOT_RAISABLE));

        // options
        Set<String> declared = new HashSet<>();
        for (Option o : m.options) declared.add(o.id);
        for (String id : s.options()) if (!declared.contains(id)) ev.errors.add(Msg.of(MsgCode.ERR_UNKNOWN_OPTION, id));

        // modifier extra MP
        if (s.extraMp() < 0) ev.errors.add(Msg.of(MsgCode.ERR_EXTRA_MP_NEGATIVE));
        else if (s.extraMp() > 0 && (m.modifier == null || m.modifier.durationPerExtraMp == null))
            ev.errors.add(Msg.of(MsgCode.ERR_EXTRA_MP_NOT_ALLOWED));
        if (m.modifier != null && !Boolean.TRUE.equals(m.modifier.appliesToWholeSpell))
            ev.errors.add(Msg.of(MsgCode.ERR_MODIFIER_NOT_WHOLE_SPELL));

        // inputs
        validateInputs(ev, req);
        if (ev.refused()) return ev;

        // cost
        Maybe<Fraction> base = baseCost(ev);
        if (ev.refused()) return ev;
        if (base.isDefined()) {
            Fraction c = base.value();
            if (s.k() > 0) c = c.add(Fraction.of(m.power.step.mp).multiply(Fraction.of(s.k())));
            for (Option o : m.options) if (s.options().contains(o.id)) {
                if (o.extraMp == null) { base = Maybe.undefined(MsgCode.OPTION_WITHOUT_EXTRA_MP, o.id); break; }
                c = c.add(Fraction.of(o.extraMp));
            }
            if (base.isDefined()) {
                c = c.add(Fraction.of(s.extraMp()));
                ev.mp = Maybe.of(c);
                if (!c.isWhole()) ev.warnings.add(Msg.of(MsgCode.WARN_NOT_WHOLE_MP));
            } else ev.mp = base;
        } else ev.mp = base;

        // power E
        if (m.power == null) ev.e = Maybe.undefined(MsgCode.NO_E);
        else if (Boolean.TRUE.equals(m.power.infinite)) { ev.e = Maybe.undefined(MsgCode.POWER_INFINITE); ev.infinitePower = true; }
        else if (missing.contains("power") || m.power.baseE == null) ev.e = Maybe.undefined(MsgCode.POWER_UNDEFINED);
        else if (s.k() > 0 && m.power.step.e == null) ev.e = Maybe.undefined(MsgCode.STEP_E_UNDEFINED);
        else ev.e = Maybe.of(m.power.baseE.add(m.power.step == null || s.k() == 0 ? BigDecimal.ZERO : m.power.step.e.multiply(BigDecimal.valueOf(s.k()))));

        // effect, resistance
        ev.effect = (m.effect == null || m.effect.isBlank() || missing.contains("effect")) ? Maybe.undefined(MsgCode.EFFECT_NOT_DEFINED) : Maybe.of(m.effect);
        ev.resistance = (m.resistance == null || m.resistance.kind == null || m.resistance.kind.equals("undefined") || missing.contains("resistance"))
                ? Maybe.undefined(MsgCode.RESISTANCE_NOT_DEFINED) : resolveResistance(m, s);

        // casting time (own + ticked options + what a modifier adds); the parts are kept so the page can show how it adds up
        Maybe<TimeValue> ct;
        if (m.castingTime == null || m.castingTime.time == null || missing.contains("castingTime")) ct = Maybe.undefined(MsgCode.CASTING_TIME_NOT_DEFINED);
        else ct = m.castingTime.time.asTimeOrUndefined(MsgCode.CASTING_TIME_NOT_A_TIME);
        ev.castingParts.add(new CastingPart(CastingPart.Kind.OWN, null, ct));
        for (Option o : m.options) if (s.options().contains(o.id) && o.extraCastingTime != null) {
            Maybe<TimeValue> extra = o.extraCastingTime.asTimeOrUndefined(MsgCode.OPTION_CASTING_TIME_NOT_A_TIME);
            ev.castingParts.add(new CastingPart(CastingPart.Kind.OPTION, o.label, extra));
            ct = TimeValue.add(ct, extra);
        }
        if (m.modifier != null) {
            Maybe<TimeValue> adds = m.modifier.addsCastingTime == null ? Maybe.undefined(MsgCode.MODIFIER_CASTING_TIME_NOT_DEFINED) : m.modifier.addsCastingTime.asTimeOrUndefined(MsgCode.MODIFIER_CASTING_TIME_NOT_A_TIME);
            ev.castingParts.add(new CastingPart(CastingPart.Kind.MODIFIER, null, adds));
            ct = TimeValue.add(ct, adds);
        }
        ev.casting = ct;

        // duration
        if (m.modifier != null) {
            Maybe<TimeValue> add = m.modifier.addsDuration == null ? Maybe.undefined(MsgCode.MODIFIER_DURATION_NOT_DEFINED) : m.modifier.addsDuration.asTimeOrUndefined(MsgCode.MODIFIER_DURATION_NOT_A_TIME);
            if (s.extraMp() > 0) {
                Maybe<TimeValue> per = m.modifier.durationPerExtraMp.asTimeOrUndefined(MsgCode.DURATION_PER_EXTRA_MP_NOT_A_TIME).isDefined()
                        ? m.modifier.durationPerExtraMp.times(s.extraMp()) : Maybe.undefined(MsgCode.DURATION_PER_EXTRA_MP_NOT_A_TIME);
                add = TimeValue.add(add, per);
            }
            ev.modDuration = add;
        } else {
            TimeValue d = m.duration;
            if (d == null || d.kind == TimeValue.Kind.UNDEFINED || missing.contains("duration")) ev.baseDuration = Maybe.undefined(MsgCode.DURATION_NOT_DEFINED);
            else if (d.kind == TimeValue.Kind.FORM_DEPENDENT) {
                Form f = chosenForm(ev, req);
                if (f == null) ev.baseDuration = Maybe.undefined(MsgCode.NO_FORM_CHOSEN);
                else if (f.duration == null || f.duration.kind == TimeValue.Kind.UNDEFINED || f.duration.kind == TimeValue.Kind.FORM_DEPENDENT)
                    ev.baseDuration = Maybe.undefined(MsgCode.FORM_DURATION_NOT_DEFINED, f.id);
                else ev.baseDuration = Maybe.of(f.duration);
            } else ev.baseDuration = Maybe.of(d);
        }
        return ev;
    }

    private Form chosenForm(Eval ev, SpellRequest req) {
        for (Input in : ev.m.inputs) if ("form".equals(in.type)) {
            JsonNode v = ev.sel.inputs().get(in.id);
            if (v != null && v.isTextual()) for (Form f : req.forms()) if (f.id.equals(v.asText())) return f;
        }
        return null;
    }

    /** Every listed requirement must be met by the character; a school the character has no entry for counts as tier 0. */
    private static void checkRequirements(Eval ev, SpellRequest req, List<Requirement> requires) {
        if (requires == null) return;
        for (Requirement r : requires) {
            if (r.school == null || r.tier == null) continue;
            int have = req.character().tiers.getOrDefault(r.school, 0);
            if (have < r.tier) ev.errors.add(Msg.of(MsgCode.ERR_TIER_BELOW, have, r.school, r.tier));
        }
    }

    /** How an input is named in a message: its label (what the player sees), or its id when it has none. */
    private static String name(Input in) { return in.label != null && !in.label.isBlank() ? in.label : in.id; }

    private void validateInputs(Eval ev, SpellRequest req) {
        Mosaic m = ev.m; Map<String, JsonNode> given = ev.sel.inputs();
        Set<String> ids = new HashSet<>();
        for (Input in : m.inputs) ids.add(in.id);
        for (String id : given.keySet()) if (!ids.contains(id)) ev.errors.add(Msg.of(MsgCode.ERR_UNKNOWN_INPUT, id));
        for (Input in : m.inputs) {
            JsonNode v = given.get(in.id);
            if (v == null || v.isNull()) {
                if (!"text".equals(in.type)) ev.errors.add(Msg.of(MsgCode.ERR_INPUT_MISSING, name(in)));
                continue;
            }
            switch (in.type == null ? "" : in.type) {
                case "choice" -> {
                    if (!v.isTextual() || in.values == null || !in.values.contains(v.asText())) ev.errors.add(Msg.of(MsgCode.ERR_INPUT_VALUE_NOT_ALLOWED, name(in)));
                    else if (in.requires != null) checkRequirements(ev, req, in.requires.get(v.asText()));   // what the chosen value needs
                }
                case "number" -> {
                    if (!v.isNumber() || v.decimalValue().signum() < 0) ev.errors.add(Msg.of(MsgCode.ERR_INPUT_NOT_A_NUMBER, name(in)));
                    else if (Boolean.TRUE.equals(in.integer) && v.decimalValue().stripTrailingZeros().scale() > 0) ev.errors.add(Msg.of(MsgCode.ERR_INPUT_NOT_WHOLE, name(in)));
                    else if (in.min != null && v.decimalValue().compareTo(in.min) < 0) ev.errors.add(Msg.of(MsgCode.ERR_INPUT_BELOW_MIN, name(in), in.min.stripTrailingZeros().toPlainString()));
                }
                case "text" -> { if (!v.isTextual()) ev.errors.add(Msg.of(MsgCode.ERR_INPUT_NOT_TEXT, name(in))); }
                case "form" -> {
                    Form f = null;
                    if (v.isTextual()) for (Form x : req.forms()) if (x.id.equals(v.asText())) f = x;
                    if (f == null) ev.errors.add(Msg.of(MsgCode.ERR_INPUT_UNKNOWN_FORM, name(in)));
                    else {
                        int have = m.school == null ? 0 : req.character().tiers.getOrDefault(m.school, 0);
                        if (f.tier == null) ev.errors.add(Msg.of(MsgCode.ERR_FORM_NO_TIER, f.id));
                        else if (have < f.tier) ev.errors.add(Msg.of(MsgCode.ERR_FORM_NEEDS_TIER, f.id, f.tier, m.school, have));
                    }
                }
                case "composition" -> {
                    if (!v.isArray() || v.isEmpty()) { ev.errors.add(Msg.of(MsgCode.ERR_COMPOSITION_EMPTY, name(in))); break; }
                    Set<String> seen = new HashSet<>();
                    for (JsonNode p : v) {
                        if (!GROUPS.contains(p.path("group").asText("")) || !p.path("massG").isNumber() || p.get("massG").decimalValue().signum() < 0)
                            ev.errors.add(Msg.of(MsgCode.ERR_COMPOSITION_PART_INVALID, name(in)));
                        // user decision: only a single row per material group
                        else if (!seen.add(p.get("group").asText()))
                            ev.errors.add(Msg.of(MsgCode.ERR_COMPOSITION_DUPLICATE_GROUP, name(in), p.get("group").asText()));
                    }
                }
                default -> ev.errors.add(Msg.of(MsgCode.ERR_INPUT_UNKNOWN_TYPE, name(in), in.type));
            }
        }
    }

    // ------------------------------------------------------------------ cost patterns

    private Maybe<Fraction> baseCost(Eval ev) {
        Cost c = ev.m.cost;
        if (c == null || c.pattern == null) return Maybe.undefined(MsgCode.NO_COST_DATA);
        switch (c.pattern) {
            case "flat":
                return c.baseMp == null ? Maybe.undefined(MsgCode.BASE_MP_UNDEFINED) : Maybe.of(Fraction.of(c.baseMp));
            case "perMass": return perMass(ev, c);
            case "compositionPerMass": return compositionPerMass(ev, c);
            case "compositionGrid":
                return Maybe.undefined(MsgCode.GRID_RATES_UNDEFINED);
            case "lookup": return lookup(ev, c);
            case "perChoice": return perChoice(ev, c);
            case "wrapper": return wrapperBase(ev, c);   // the part of the cost that does not depend on the other mosaics; the calculation adds factor x their MP
            case "linear": return linear(ev, c);
            case "undefined": return Maybe.undefined(MsgCode.COST_PATTERN_UNDEFINED);
            default: return Maybe.undefined(MsgCode.UNKNOWN_COST_PATTERN, c.pattern);
        }
    }

    /** The resistance of this cast: a kind that depends on a chosen element is resolved into the element that resists (the same one, or its opposite). */
    private static Maybe<Resistance> resolveResistance(Mosaic m, Selection s) {
        Resistance r = m.resistance;
        Resistance out = new Resistance();
        out.kind = r.kind;
        out.school = r.school;
        if (r.input != null) {
            JsonNode chosen = s.inputs().get(r.input);
            if (chosen == null || !chosen.isTextual()) return Maybe.undefined(MsgCode.RESISTANCE_INPUT_MISSING, r.input);
            out.element = chosen.asText();
            if (r.pairs != null) {
                out.element = r.pairs.get(chosen.asText());
                if (out.element == null) return Maybe.undefined(MsgCode.RESISTANCE_NO_PAIR, chosen.asText());
            }
            Input in = m.inputs.stream().filter(i -> r.input.equals(i.id)).findFirst().orElse(null);
            if (in != null && in.labels != null) out.elementLabel = in.labels.get(out.element);
        }
        return Maybe.of(out);
    }

    private Maybe<Fraction> perMass(Eval ev, Cost c) {
        if (c.rate == null || c.rate.mp == null || c.rate.massG == null || c.rate.massG.signum() <= 0) return Maybe.undefined(MsgCode.RATE_UNDEFINED);
        List<Input> nums = ev.m.inputs.stream().filter(i -> "number".equals(i.type) && !i.id.equals(c.multiplierInput)).toList();
        if (nums.size() != 1) return Maybe.undefined(MsgCode.PER_MASS_NEEDS_ONE_NUMBER);
        Input in = nums.get(0);
        BigDecimal factor;
        if ("g".equals(in.unit)) factor = BigDecimal.ONE;
        else if ("kg".equals(in.unit)) factor = BigDecimal.valueOf(1000);
        else return Maybe.undefined(MsgCode.WEIGHT_UNIT_NOT_G_OR_KG);
        Fraction massG = Fraction.of(ev.sel.inputs().get(in.id).decimalValue().multiply(factor));
        Fraction cost = blockCost(massG, c.rate.mp, c.rate.massG);
        if (c.multiplierInput != null) {   // the speed of a Forgás varázsjel: the total MP requirement times a whole number
            JsonNode n = ev.sel.inputs().get(c.multiplierInput);
            if (n == null || !n.isNumber()) return Maybe.undefined(MsgCode.LOOKUP_INPUT_MISSING);
            cost = cost.multiply(Fraction.of(n.decimalValue()));
        }
        return Maybe.of(cost);
    }

    /**
     * Mass is charged in whole blocks (user decision): a rate of "mp per massG" costs mp for every block of massG that is
     * started. 3 MP per 2 kg: up to 2 kg costs 3 MP, 2.1 kg costs 6 MP. No mass costs nothing. The result is always a whole
     * number of MP when mp is whole.
     */
    static Fraction blockCost(Fraction massG, BigDecimal mp, BigDecimal blockG) {
        Fraction blocks = massG.divide(Fraction.of(blockG));
        BigInteger started = blocks.signum() <= 0 ? BigInteger.ZERO
                : blocks.numerator().add(blocks.denominator()).subtract(BigInteger.ONE).divide(blocks.denominator());
        return Fraction.of(mp).multiply(Fraction.of(new BigDecimal(started)));
    }

    private Maybe<Fraction> compositionPerMass(Eval ev, Cost c) {
        if (c.rounding != null && !c.rounding.isNull()) return Maybe.undefined(MsgCode.ROUNDING_RULE_UNKNOWN);
        if (c.rates == null || c.rates.isNull()) return Maybe.undefined(MsgCode.RATES_UNDEFINED);
        Input in = ev.m.inputs.stream().filter(i -> "composition".equals(i.type)).findFirst().orElse(null);
        if (in == null) return Maybe.undefined(MsgCode.NO_COMPOSITION_INPUT);
        Fraction sum = Fraction.ZERO;
        for (JsonNode p : ev.sel.inputs().get(in.id)) {
            String g = p.get("group").asText();
            JsonNode r = c.rates.get(g);
            if (r == null || r.isNull()) { ev.errors.add(Msg.of(MsgCode.ERR_GROUP_NOT_AVAILABLE, g)); return Maybe.undefined(MsgCode.GROUP_NOT_AVAILABLE_REASON); }
            if (!r.path("mp").isNumber() || !r.path("massG").isNumber() || r.get("massG").decimalValue().signum() <= 0) return Maybe.undefined(MsgCode.GROUP_RATE_UNDEFINED, g);
            sum = sum.add(blockCost(Fraction.of(p.get("massG").decimalValue()), r.get("mp").decimalValue(), r.get("massG").decimalValue()));
        }
        return Maybe.of(sum);
    }

    private Maybe<Fraction> wrapperBase(Eval ev, Cost c) {
        if (c.baseMpByValue == null) return c.baseMp == null ? Maybe.undefined(MsgCode.BASE_MP_UNDEFINED) : Maybe.of(Fraction.of(c.baseMp));
        JsonNode v = c.input == null ? null : ev.sel.inputs().get(c.input);
        if (v == null || !v.isTextual()) return Maybe.undefined(MsgCode.LOOKUP_INPUT_MISSING);
        JsonNode mp = c.baseMpByValue.get(v.asText());
        return mp != null && mp.isNumber() ? Maybe.of(Fraction.of(mp.decimalValue())) : Maybe.undefined(MsgCode.VALUE_OUTSIDE_TABLE);
    }

    /** baseMp plus mpPerUnit for every unit of the input above freeUnits (the sphere of a Térgát varázsjel: 3 MP for 1 m, +1 MP per extra meter). */
    private Maybe<Fraction> linear(Eval ev, Cost c) {
        if (c.input == null || c.baseMp == null || c.mpPerUnit == null) return Maybe.undefined(MsgCode.LOOKUP_DATA_UNDEFINED);
        JsonNode v = ev.sel.inputs().get(c.input);
        if (v == null || !v.isNumber()) return Maybe.undefined(MsgCode.LOOKUP_INPUT_MISSING);
        BigDecimal extra = v.decimalValue().subtract(c.freeUnits == null ? BigDecimal.ZERO : c.freeUnits).max(BigDecimal.ZERO);
        return Maybe.of(Fraction.of(c.baseMp.add(extra.multiply(c.mpPerUnit))));
    }

    /** Does the wrapper accept this mosaic: its school (and subgroup, if the entry names one) is in the list. */
    private static boolean accepts(JsonNode wraps, Mosaic m) {
        for (JsonNode w : wraps)
            if (w.path("school").asText("").equals(m.school) && (!w.has("subgroup") || w.get("subgroup").asText("").equals(m.subgroup))) return true;
        return false;
    }

    private static String acceptedLabels(JsonNode wraps) {
        List<String> labels = new ArrayList<>();
        for (JsonNode w : wraps) labels.add(w.path("label").asText(w.path("school").asText("")));
        return String.join(", ", labels);
    }

    /** The MP of a mosaic at strength E (a wrapper that prices what it holds at a fixed E): the power step k that reaches E exactly. */
    private Maybe<Fraction> mpAtE(Eval o, SpellRequest req, BigDecimal e) {
        Power p = o.m.power;
        Msg why = Msg.of(MsgCode.WRAPPER_INNER_E_UNREACHABLE, o.m.name, e.stripTrailingZeros().toPlainString());
        if (p == null || p.baseE == null || p.step == null || p.step.e == null || p.step.e.signum() <= 0) return Maybe.undefined(why);
        BigDecimal diff = e.subtract(p.baseE);
        if (diff.signum() < 0 || diff.remainder(p.step.e).signum() != 0) return Maybe.undefined(why);
        Eval at = evaluate(o.sel.withK(diff.divideToIntegralValue(p.step.e).intValueExact()), req);
        return at.refused() ? Maybe.undefined(why) : at.mp;
    }

    /** MP by the chosen value of a choice input (for example the class of creature a circle keeps out). */
    private Maybe<Fraction> perChoice(Eval ev, Cost c) {
        if (c.input == null || c.mpByValue == null) return Maybe.undefined(MsgCode.LOOKUP_DATA_UNDEFINED);
        JsonNode v = ev.sel.inputs().get(c.input);
        if (v == null || !v.isTextual()) return Maybe.undefined(MsgCode.LOOKUP_INPUT_MISSING);
        JsonNode mp = c.mpByValue.get(v.asText());
        if (mp == null) return Maybe.undefined(MsgCode.VALUE_OUTSIDE_TABLE);
        return mp.isNumber() ? Maybe.of(Fraction.of(mp.decimalValue())) : Maybe.undefined(MsgCode.TABLE_MP_UNDEFINED);
    }

    private Maybe<Fraction> lookup(Eval ev, Cost c) {
        if (c.input == null || c.table == null) return Maybe.undefined(MsgCode.LOOKUP_DATA_UNDEFINED);
        JsonNode v = ev.sel.inputs().get(c.input);
        if (v == null || !v.isNumber()) return Maybe.undefined(MsgCode.LOOKUP_INPUT_MISSING);
        BigDecimal x = v.decimalValue();
        for (Cost.Row r : c.table) {
            boolean ge = r.fromM != null && x.compareTo(r.fromM) >= 0;
            boolean le = r.toM == null || x.compareTo(r.toM) <= 0;
            if (ge && le) return r.mp == null ? Maybe.undefined(MsgCode.TABLE_MP_UNDEFINED) : Maybe.of(Fraction.of(r.mp));
        }
        return Maybe.undefined(MsgCode.VALUE_OUTSIDE_TABLE);
    }
}
