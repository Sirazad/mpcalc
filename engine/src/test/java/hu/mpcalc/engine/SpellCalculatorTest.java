package hu.mpcalc.engine;

import com.fasterxml.jackson.databind.JsonNode;
import hu.mpcalc.engine.model.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpellCalculatorTest {
    private final SpellCalculator calc = new SpellCalculator();
    private List<Form> forms;

    SpellCalculatorTest() { forms = Json.parseForms(text("/forms.json")); }

    static String text(String res) {
        try (InputStream in = SpellCalculatorTest.class.getResourceAsStream(res)) { return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        catch (IOException e) { throw new RuntimeException(e); }
    }
    static Mosaic mosaic(String res) { return Json.parseMosaic(text(res)); }
    static JsonNode j(String json) { try { return Json.mapper().readTree(json); } catch (IOException e) { throw new RuntimeException(e); } }
    static CharacterSheet ch(int mp, Object... schoolTier) {
        Map<String, Integer> t = new java.util.HashMap<>();
        for (int i = 0; i < schoolTier.length; i += 2) t.put((String) schoolTier[i], (Integer) schoolTier[i + 1]);
        return new CharacterSheet(t, BigDecimal.valueOf(mp));
    }
    SpellResult run(CharacterSheet c, Selection... s) { return calc.calculate(new SpellRequest(List.of(s), c, forms)); }

    @Test void furkeszesAtE4Costs10() {
        SpellResult r = run(ch(20), Selection.of(mosaic("/furkeszes.json")).withK(3));
        assertTrue(r.valid());
        assertEquals(Fraction.of(10), r.totalMp().value());
        assertEquals(new BigDecimal("4"), r.mosaics().get(0).e().value());
        assertEquals("Test effect text", r.mosaics().get(0).effect().value());
        assertEquals("concealment", r.mosaics().get(0).resistance().value().toString());
        assertEquals(Fraction.of(10), r.remainingMp().value());
        assertTrue(r.enoughMp());
        assertEquals("1 kor", r.castingTime().value().toString());
        assertEquals(TimeValue.Kind.ONE_OFF, r.duration().value().kind);
        assertFalse(r.mosaics().get(0).mp().value().signum() < 0);
    }

    @Test void oselemAtE3Costs12() {
        Selection s = Selection.of(mosaic("/oselem.json")).withK(2)
                .withInputs(Map.of("element", j("\"ostuz\""), "form", j("\"test-small\"")));
        SpellResult r = run(ch(20, "anyagi", 2), s);
        assertTrue(r.valid(), r.errors().toString());
        assertEquals(Fraction.of(12), r.totalMp().value());
        assertEquals(new BigDecimal("3"), r.mosaics().get(0).e().value());
        assertEquals("3 kor", r.duration().value().toString());
    }

    @Test void beszedZavarasOnlyThreeEStepsPerTwoMp() {
        Mosaic m = mosaic("/beszed-zavaras.json");
        int[][] expect = {{0, 3, 2}, {1, 6, 4}, {2, 9, 6}};
        for (int[] x : expect) {
            SpellResult r = run(ch(20, "mental", 2), Selection.of(m).withK(x[0]));
            assertEquals(new BigDecimal(x[1]), r.mosaics().get(0).e().value());
            assertEquals(Fraction.of(x[2]), r.totalMp().value());
        }
        // E 4 or 5 does not exist: only k selects a step, a negative k is refused
        assertFalse(run(ch(20, "mental", 2), Selection.of(m).withK(-1)).valid());
    }

    @Test void atformazasSwordIsChargedInStartedBlocks() {
        Selection s = Selection.of(mosaic("/atformazas.json")).withInputs(Map.of("composition", j(
                "[{\"group\":\"II\",\"massG\":2000},{\"group\":\"I\",\"massG\":500},{\"group\":\"III\",\"massG\":20}]")));
        SpellResult r = run(ch(100, "anyagi", 2), s);
        assertTrue(r.valid(), r.errors().toString());
        // group II 2000 g = 4 blocks of 500 g = 4 MP; group I 500 g = 1 started block of 1000 g = 1 MP; group III 20 g = 20 blocks of 1 g = 20 MP
        assertEquals(Fraction.of(25), r.totalMp().value());
        assertTrue(r.warnings().isEmpty());
        assertFalse(r.mosaics().get(0).e().isDefined());
        assertEquals("naturalMaterialMagic", r.mosaics().get(0).resistance().value().toString());
    }

    @Test void whole_composition_has_no_rounding_warning() {
        Selection s = Selection.of(mosaic("/atformazas.json")).withInputs(Map.of("composition", j("[{\"group\":\"II\",\"massG\":2000}]")));
        SpellResult r = run(ch(100, "anyagi", 2), s);
        assertEquals(Fraction.of(4), r.totalMp().value());
        assertTrue(r.warnings().isEmpty());
    }

    @Test void extensionExample() {
        Selection pontos = Selection.of(mosaic("/pontos-ido.json"));
        Selection ext = Selection.of(mosaic("/nyujtas-ora.json"));
        SpellResult r = run(ch(40, "ido", 2), pontos, ext);
        assertTrue(r.valid(), r.errors().toString());
        assertEquals(Fraction.of(26), r.totalMp().value());
        assertEquals(Map.of("perc", 5L, "sz", 1L), r.castingTime().value().amounts.asMap());
        assertEquals("5 perc 1 sz", r.castingTime().value().toString());
        assertEquals("1 ora 1 kor", r.duration().value().toString());

        SpellResult r2 = run(ch(40, "ido", 2), pontos, ext.withExtraMp(14));
        assertEquals("15 ora 1 kor", r2.duration().value().toString());
        assertEquals(Fraction.of(40), r2.totalMp().value());
        assertEquals(Fraction.of(0), r2.remainingMp().value());
        assertTrue(r2.enoughMp());

        SpellResult r3 = run(ch(30, "ido", 2), pontos, ext.withExtraMp(14));
        assertEquals(Fraction.of(-10), r3.remainingMp().value());
        assertFalse(r3.enoughMp());
    }

    @Test void tierRefusal() {
        Selection s = Selection.of(mosaic("/beszed-zavaras.json"));
        SpellResult r = run(ch(20, "mental", 1), s);
        assertFalse(r.valid());
        assertTrue(r.mosaics().get(0).refused());
        assertFalse(r.totalMp().isDefined());
        assertFalse(run(ch(20), s).valid()); // no tier at all in the school
    }

    @Test void formTierRefusal() {
        Selection s = Selection.of(mosaic("/oselem.json")).withInputs(Map.of("element", j("\"ostuz\""), "form", j("\"test-big\"")));
        SpellResult r = run(ch(20, "anyagi", 2), s);
        assertFalse(r.valid());
        assertTrue(r.mosaics().get(0).refused());
        assertTrue(run(ch(20, "anyagi", 3), s).valid());
    }

    @Test void incompleteMosaicRefusedAndLegiessegCostUndefined() {
        Selection s = Selection.of(mosaic("/legiesseg.json")).withInputs(Map.of("composition", j("[{\"group\":\"I\",\"massG\":61000}]"), "passThrough", j("\"I+II\"")));
        SpellResult r = run(ch(100, "anyagi", 3), s);
        assertFalse(r.valid());
        assertTrue(r.mosaics().get(0).refused());
        assertFalse(r.mosaics().get(0).mp().isDefined());
        assertFalse(r.totalMp().isDefined());
        assertFalse(r.remainingMp().isDefined());
    }

    @Test void compositionGridWithRatesNullIsUndefinedEvenIfMarkedComplete() {
        Mosaic m = mosaic("/legiesseg.json");
        m.status.state = "complete"; m.status.missing = List.of();
        Selection s = Selection.of(m).withInputs(Map.of("composition", j("[{\"group\":\"I\",\"massG\":1000}]"), "passThrough", j("\"I\"")));
        SpellResult r = run(ch(100, "anyagi", 3), s);
        assertFalse(r.mosaics().get(0).mp().isDefined());
        assertFalse(r.totalMp().isDefined());
    }

    @Test void partialMosaicComputesCostButUndefinedOutputs() {
        Selection s = Selection.of(mosaic("/tavolbahatas.json")).withInputs(Map.of("rangeM", j("50")));
        SpellResult r = run(ch(100, "ter", 2), s);
        assertTrue(r.valid(), r.errors().toString());
        assertEquals(Fraction.of(18), r.totalMp().value());
        assertFalse(r.mosaics().get(0).duration().isDefined());
        assertFalse(r.mosaics().get(0).e().isDefined());
        assertFalse(r.duration().isDefined());
        assertEquals("4 sz", r.castingTime().value().toString());
        // gap between 99 km and 100 km is not covered
        Selection gap = Selection.of(mosaic("/tavolbahatas.json")).withInputs(Map.of("rangeM", j("99500")));
        assertFalse(run(ch(100, "ter", 2), gap).totalMp().isDefined());
        assertTrue(run(ch(100, "ter", 2), gap).valid());
    }

    @Test void perMassWithOptionAndUndefinedCastingTime() {
        Selection s = Selection.of(mosaic("/teleport-test.json")).withInputs(Map.of("weight", j("1")));
        SpellResult r = run(ch(100, "ter", 2), s);
        assertEquals(Fraction.of(1), r.totalMp().value());
        assertTrue(r.warnings().isEmpty());
        assertFalse(r.castingTime().isDefined());
        SpellResult r2 = run(ch(100, "ter", 2), s.withOptions(Set.of("window")));
        assertEquals(Fraction.of(21), r2.totalMp().value());
    }

    @Test void massIsChargedInWholeStartedBlocks() {
        // Teleport: 1 MP per 3 kg block. Anything up to the block costs the full block, the next gram starts the next one.
        for (String[] c : new String[][] {{"0.001", "1"}, {"1", "1"}, {"3", "1"}, {"3.001", "2"}, {"6", "2"}, {"6.5", "3"}}) {
            Selection s = Selection.of(mosaic("/teleport-test.json")).withInputs(Map.of("weight", j(c[0])));
            assertEquals(Fraction.of(Long.parseLong(c[1])), run(ch(100, "ter", 2), s).totalMp().value(), c[0] + " kg");
        }
        // Composition: every part is charged in blocks of its own group's rate (group I 1 MP per 1000 g, group II 1 MP per 500 g).
        for (String[] c : new String[][] {{"I", "1", "1"}, {"I", "1000", "1"}, {"I", "1001", "2"}, {"II", "500", "1"}, {"II", "501", "2"}, {"II", "2100", "5"}}) {
            Selection s = Selection.of(mosaic("/atformazas.json")).withInputs(Map.of("composition",
                    j("[{\"group\":\"" + c[0] + "\",\"massG\":" + c[1] + "}]")));
            SpellResult r = run(ch(100, "anyagi", 2), s);
            assertEquals(Fraction.of(Long.parseLong(c[2])), r.totalMp().value(), c[0] + " " + c[1] + " g");
            assertTrue(r.totalMp().value().isWhole());
        }
    }

    @Test void powerCannotBeRaisedWithoutStep() {
        assertFalse(run(ch(100, "anyagi", 2), Selection.of(mosaic("/atformazas.json")).withK(1)
                .withInputs(Map.of("composition", j("[{\"group\":\"I\",\"massG\":1000}]")))).valid());
    }

    @Test void timeComparisonInSecondsAndEv() {
        TimeAmounts a = TimeAmounts.of(Map.of("perc", 1L)), b = TimeAmounts.of(Map.of("sz", 60L));
        assertEquals(0, a.compareTo(b).value());
        assertEquals(1, TimeAmounts.of(Map.of("ora", 1L)).compareTo(a).value());
        assertFalse(TimeAmounts.of(Map.of("ev", 1L)).compareTo(a).isDefined());
        assertEquals(-1, TimeAmounts.of(Map.of("ev", 1L)).compareTo(TimeAmounts.of(Map.of("ev", 2L))).value());
    }

    @Test void onlyASingleRowPerMaterialGroup() {
        Selection twice = Selection.of(mosaic("/atformazas.json")).withInputs(Map.of("composition",
                j("[{\"group\":\"I\",\"massG\":300},{\"group\":\"I\",\"massG\":300}]")));
        SpellResult r = run(ch(100, "anyagi", 2), twice);
        assertFalse(r.valid());
        assertTrue(r.mosaics().get(0).errors().stream().anyMatch(e -> MsgCode.ERR_COMPOSITION_DUPLICATE_GROUP.key.equals(e.code()) && e.args().equals(List.of("composition", "I"))), r.mosaics().get(0).errors().toString());
        Selection oneEach = Selection.of(mosaic("/atformazas.json")).withInputs(Map.of("composition",
                j("[{\"group\":\"I\",\"massG\":300},{\"group\":\"II\",\"massG\":300}]")));
        assertTrue(run(ch(100, "anyagi", 2), oneEach).valid());
    }

    @Test void messagesAreCodesWithArgumentsNotText() {
        // refused mosaic: the error is a code with its arguments and the mosaic name as subject
        Selection s = Selection.of(mosaic("/oselem.json")).withInputs(Map.of("element", j("\"ostuz\""), "form", j("\"test-small\"")));
        SpellResult low = run(ch(20, "anyagi", 0), s);
        assertFalse(low.valid());
        Msg tier = low.errors().get(0);
        assertEquals(MsgCode.ERR_TIER_BELOW.key, tier.code());
        assertEquals(List.of(0, "anyagi", 2), tier.args());
        assertNotNull(tier.subject());
        // an undefined value carries a reason message
        SpellResult ok = run(ch(20, "anyagi", 2), Selection.of(mosaic("/atformazas.json"))
                .withInputs(Map.of("composition", j("[{\"group\":\"II\",\"massG\":500}]"))));
        assertEquals(MsgCode.NO_E.key, ok.mosaics().get(0).e().reason().code());
        // not enough MP is a warning code
        SpellResult poor = run(ch(0, "anyagi", 2), Selection.of(mosaic("/atformazas.json"))
                .withInputs(Map.of("composition", j("[{\"group\":\"II\",\"massG\":500}]"))));
        assertTrue(poor.warnings().stream().anyMatch(w -> MsgCode.WARN_NOT_ENOUGH_MP.key.equals(w.code())));
        assertFalse(poor.enoughMp());
    }

    // ---- Jelmágia (user, 2026-10-17): requirements in other schools, infinite E, cost by choice, wrapper, trance
    private static String errorCodes(SpellResult r) { return r.mosaics().get(0).errors().toString(); }

    @Test void aRequirementInAnotherSchoolIsChecked() {
        Selection s = Selection.of(mosaic("/jel-bebortonozo.json")).withInputs(Map.of("creatureClass", j("\"felhalott\"")));
        SpellResult no = run(ch(50, "jelmagia", 2), s);
        assertTrue(no.mosaics().get(0).refused());
        assertTrue(errorCodes(no).contains("nekromancia"), errorCodes(no));
        SpellResult yes = run(ch(50, "jelmagia", 2, "nekromancia", 2), s);
        assertTrue(yes.valid(), yes.errors().toString());
    }

    @Test void theMpOfACircleComesFromTheChosenCreatureClass() {
        for (Object[] c : new Object[][] {{"felhalott", 20}, {"kisertet", 30}, {"demon", 40}, {"demonur", 70}}) {
            SpellResult r = run(ch(100, "jelmagia", 2, "nekromancia", 2),
                    Selection.of(mosaic("/jel-bebortonozo.json")).withInputs(Map.of("creatureClass", j("\"" + c[0] + "\""))));
            assertEquals(Fraction.of((Integer) c[1]), r.totalMp().value(), (String) c[0]);
        }
        SpellResult bad = run(ch(100, "jelmagia", 2, "nekromancia", 2),
                Selection.of(mosaic("/jel-bebortonozo.json")).withInputs(Map.of("creatureClass", j("\"sarkany\""))));
        assertTrue(bad.mosaics().get(0).refused());
    }

    @Test void anInfinitePowerHasNoNumberAndIsFlagged() {
        SpellResult r = run(ch(100, "jelmagia", 2, "nekromancia", 2),
                Selection.of(mosaic("/jel-bebortonozo.json")).withInputs(Map.of("creatureClass", j("\"felhalott\""))));
        MosaicResult m = r.mosaics().get(0);
        assertTrue(m.infinitePower());
        assertFalse(m.e().isDefined());
        assertEquals(TimeValue.Kind.UNTIL_BROKEN, m.duration().value().kind, "until the lines are broken");
        assertEquals(TimeValue.Kind.UNTIL_BROKEN, r.duration().value().kind);
    }

    @Test void theChosenValueNeedsItsOwnSchoolAndTheCostIsThreeMpPerE() {
        Selection fire = Selection.of(mosaic("/jel-vedo.json")).withInputs(Map.of("effect", j("\"ostuz\""))).withK(2);
        SpellResult onlySign = run(ch(100, "jelmagia", 2), fire);
        assertTrue(onlySign.mosaics().get(0).refused(), "Őstűz needs Anyagi mágia 2. fok");
        SpellResult ok = run(ch(100, "jelmagia", 2, "anyagi", 2), fire);
        assertTrue(ok.valid(), ok.errors().toString());
        assertEquals(Fraction.of(9), ok.totalMp().value(), "E 3 at 3 MP per E");
        assertEquals(new BigDecimal("3"), ok.mosaics().get(0).e().value());
        assertTrue(run(ch(100, "jelmagia", 2, "asztral", 2), Selection.of(mosaic("/jel-vedo.json")).withInputs(Map.of("effect", j("\"asztral\"")))).valid());
    }

    @Test void aWrapperCostsItsBasePlusTheOtherMosaicsInAnyOrderAndIncludesTheirTime() {
        Selection wrapper = Selection.of(mosaic("/jel-vegrehajto.json"));
        Selection inner = Selection.of(mosaic("/furkeszes.json"));          // 7 MP, 1 kör
        SpellResult a = run(ch(100, "jelmagia", 3), wrapper, inner);
        SpellResult b = run(ch(100, "jelmagia", 3), inner, wrapper);
        assertEquals(Fraction.of(47), a.totalMp().value());
        assertEquals(Fraction.of(47), b.totalMp().value(), "the order does not matter");
        assertEquals(Fraction.of(40), run(ch(100, "jelmagia", 3), wrapper).totalMp().value(), "the wrapper alone shows what is left for the spell");
        // the wrapper's 30 perc includes the time of the inner mosaic; the trance of 10 perc is added once
        assertEquals("40 perc", a.castingTime().value().toString());
        MosaicResult innerResult = a.mosaics().get(1);
        assertTrue(innerResult.includedInWrapper());
        assertFalse(a.mosaics().get(0).includedInWrapper());
        assertEquals(TimeValue.Kind.UNTIL_BROKEN, a.duration().value().kind, "the duration of the wrapper, not of the inner mosaic");
    }

    @Test void theTranceIsAddedOnceUnlessTheCasterIsAlreadyInOne() {
        SpellRequest inTrance = new SpellRequest(List.of(Selection.of(mosaic("/jel-vedo.json")).withInputs(Map.of("effect", j("\"asztral\""))),
                Selection.of(mosaic("/jel-vedo.json")).withInputs(Map.of("effect", j("\"asztral\"")))), ch(100, "jelmagia", 2, "asztral", 2), forms, true);
        SpellResult yes = calc.calculate(inTrance);
        assertTrue(yes.trance().needed());
        assertFalse(yes.trance().applied());
        assertEquals("40 perc", yes.castingTime().value().toString(), "two drawings of 20 perc, no trance time");
        SpellResult no = calc.calculate(new SpellRequest(inTrance.selections(), inTrance.character(), forms, false));
        assertTrue(no.trance().applied());
        assertEquals("50 perc", no.castingTime().value().toString(), "+10 perc once, not per drawing");
        SpellResult plain = run(ch(20), Selection.of(mosaic("/furkeszes.json")));
        assertFalse(plain.trance().needed(), "a spell without a drawing needs no trance");
        assertEquals("1 kor", plain.castingTime().value().toString());
    }

    // ---- Jelmágia, the rest of the signs (user, 2026-10-17)
    private static JsonNode comp(int massG) { return j("[{\"group\":\"I\",\"massG\":" + massG + "}]"); }

    @Test void aValtozasSignCostsThreeTimesTheNaturalMaterialMosaicAndLastsUntilBroken() {
        Selection inner = Selection.of(mosaic("/atformazas.json")).withInputs(Map.of("composition", comp(30000)));   // 30 kg of group I: 30 MP
        SpellResult r = run(ch(200, "jelmagia", 2, "anyagi", 2), Selection.of(mosaic("/jel-valtozas.json")), inner);
        assertTrue(r.valid(), r.errors().toString());
        assertEquals(Fraction.of(90), r.totalMp().value(), "3 x 30 MP, not 4 x");
        assertEquals("15 perc", r.castingTime().value().toString(), "5 perc of the sign + the trance, not the 4 sz of the mosaic");
        assertEquals(TimeValue.Kind.UNTIL_BROKEN, r.duration().value().kind);
    }

    @Test void aValtozasSignRefusesAnyOtherKindOfMosaic() {
        SpellResult r = run(ch(200, "jelmagia", 2, "anyagi", 2), Selection.of(mosaic("/jel-valtozas.json")), Selection.of(mosaic("/furkeszes.json")));
        assertFalse(r.valid());
        assertTrue(r.mosaics().get(0).refused());
        assertTrue(errorCodes(r).contains("Természetes anyagi mágia"), errorCodes(r));
    }

    @Test void aWrapperBaseCanDependOnAChoice() {
        for (Object[] v : new Object[][] {{"touch", 27}, {"pass", 37}}) {
            SpellResult r = run(ch(200, "jelmagia", 2), Selection.of(mosaic("/jel-kisugarzo.json")).withInputs(Map.of("variant", j("\"" + v[0] + "\""))),
                    Selection.of(mosaic("/furkeszes.json")));
            assertEquals(Fraction.of((Integer) v[1]), r.totalMp().value(), (String) v[0]);
        }
    }

    @Test void aSymbolCostsWhatItHoldsAndKeepsItsDurationAndAcceptsOnlyItsSchool() {
        SpellResult r = run(ch(100, "jelmagia", 2, "asztral", 2), Selection.of(mosaic("/jel-szimbolum-asztral.json")), Selection.of(mosaic("/asztral-test.json")));
        assertTrue(r.valid(), r.errors().toString());
        assertEquals(Fraction.of(1), r.totalMp().value());
        assertEquals("40 perc", r.castingTime().value().toString(), "30 perc of the drawing + the trance");
        assertEquals("1 perc", r.duration().value().toString(), "the duration of the spell drawn in it");
        SpellResult wrong = run(ch(100, "jelmagia", 2), Selection.of(mosaic("/jel-szimbolum-asztral.json")), Selection.of(mosaic("/furkeszes.json")));
        assertTrue(wrong.mosaics().get(0).refused());
    }

    @Test void aBelyegPricesTheMosaicAtAFixedStrengthRegardlessOfTheChosenOne() {
        Selection stamp = Selection.of(mosaic("/jel-belyeg-asztral.json"));
        SpellResult r = run(ch(100, "jelmagia", 2, "asztral", 2), stamp, Selection.of(mosaic("/asztral-test.json")).withK(3));   // chosen E 4, priced at E 15
        assertTrue(r.valid(), r.errors().toString());
        assertEquals(Fraction.of(15), r.totalMp().value(), "1 MP + 14 steps of 1 MP");
        assertTrue(r.mosaics().get(0).infinitePower());
        // a mosaic whose strength cannot be raised cannot be priced at 15 E
        Mosaic fixed = Json.parseMosaic(text("/asztral-test.json").replace("\"step\": {\"mp\": 1, \"e\": 1}", "\"step\": null"));
        SpellResult no = run(ch(100, "jelmagia", 2, "asztral", 2), stamp, Selection.of(fixed));
        assertFalse(no.totalMp().isDefined());
    }

    @Test void theSphereOfATergatCostsOneMpPerExtraWholeMeter() {
        java.util.function.BiFunction<String, Integer, SpellResult> gate = (radius, k) ->
                run(ch(100, "jelmagia", 2, "ter", 2), Selection.of(mosaic("/jel-tergat.json")).withInputs(Map.of("radiusM", j(radius))).withK(k));
        assertEquals(Fraction.of(3), gate.apply("1", 0).totalMp().value());
        assertEquals(Fraction.of(7), gate.apply("5", 0).totalMp().value());
        assertEquals(Fraction.of(13), gate.apply("5", 2).totalMp().value(), "3 + 4 extra meters + 2 steps of 3");
        assertTrue(gate.apply("1.5", 0).mosaics().get(0).refused(), "only whole meters");
        assertTrue(gate.apply("0", 0).mosaics().get(0).refused(), "at least 1 m");
        // the message names the input by its label ("Sugár"), not by its id
        assertTrue(errorCodes(gate.apply("1.5", 0)).contains("Sugár"), errorCodes(gate.apply("1.5", 0)));
        assertFalse(errorCodes(gate.apply("1.5", 0)).contains("radiusM"));
    }

    @Test void theSpeedOfAForgasSignMultipliesTheWeightCost() {
        java.util.function.BiFunction<String, Integer, SpellResult> spin = (kg, speed) ->
                run(ch(100, "jelmagia", 2), Selection.of(mosaic("/jel-forgas.json")).withInputs(Map.of("weightKg", j(kg), "speed", j("" + speed))));
        assertEquals(Fraction.of(4), spin.apply("20", 1).totalMp().value());
        assertEquals(Fraction.of(12), spin.apply("20", 3).totalMp().value());
        assertEquals(Fraction.of(2), spin.apply("5.1", 1).totalMp().value(), "a started block of 5 kg is charged");
        assertTrue(spin.apply("20", 0).mosaics().get(0).refused());
    }

    @Test void castingThroughAZoneSignAddsFiveMpOnceAndOneKor() {
        SpellRequest base = new SpellRequest(List.of(Selection.of(mosaic("/furkeszes.json"))), ch(50), forms, false, false);
        SpellResult plain = calc.calculate(base);
        SpellResult zone = calc.calculate(new SpellRequest(base.selections(), base.character(), forms, false, true));
        assertEquals(Fraction.of(7), plain.totalMp().value());
        assertEquals(Fraction.of(12), zone.totalMp().value());
        assertEquals("2 kor", zone.castingTime().value().toString(), "1 kör + 1 kör");
        assertTrue(zone.viaZoneSign());
        assertFalse(plain.viaZoneSign());
        assertEquals(Fraction.of(38), zone.remainingMp().value(), "the remaining MP follows the total");
    }

    // ---- the casting time is shown as its parts (user, 2026-10-17)
    @Test void aMosaicWithoutExtrasHasOnlyItsOwnCastingTimePart() {
        SpellResult r = run(ch(20), Selection.of(mosaic("/furkeszes.json")));
        List<CastingPart> parts = r.mosaics().get(0).castingParts();
        assertEquals(1, parts.size());
        assertEquals(CastingPart.Kind.OWN, parts.get(0).kind());
        assertEquals("1 kor", parts.get(0).time().value().toString());
    }

    @Test void aTickedOptionAndAModifierAreOwnPartsAndTheyAddUpToTheCastingTime() {
        Mosaic withOption = Json.parseMosaic(text("/furkeszes.json").replace("\"inputs\": []",
                "\"options\": [{\"id\": \"slow\", \"label\": \"Lassabb\", \"extraMp\": 2, \"extraCastingTime\": {\"kind\": \"time\", \"amounts\": {\"kor\": 2}}}], \"inputs\": []"));
        SpellResult r = run(ch(20, "ido", 2), Selection.of(withOption).withOptions(java.util.Set.of("slow")), Selection.of(mosaic("/nyujtas-ora.json")));
        MosaicResult spell = r.mosaics().get(0), ext = r.mosaics().get(1);
        assertEquals(List.of(CastingPart.Kind.OWN, CastingPart.Kind.OPTION), spell.castingParts().stream().map(CastingPart::kind).toList());
        assertEquals("Lassabb", spell.castingParts().get(1).label());
        assertEquals("3 kor", spell.castingTime().value().toString(), "1 kör own + 2 kör option");
        assertEquals(List.of(CastingPart.Kind.OWN, CastingPart.Kind.MODIFIER), ext.castingParts().stream().map(CastingPart::kind).toList());
        assertEquals("5 perc", ext.castingParts().get(1).time().value().toString());
        TimeValue sum = TimeValue.time(TimeAmounts.zero());
        Maybe<TimeValue> total = Maybe.of(sum);
        for (MosaicResult m : r.mosaics()) for (CastingPart p : m.castingParts()) total = TimeValue.add(total, p.time());
        assertEquals(r.castingTime().value().toString(), total.value().toString(), "the parts add up to the total casting time");
    }

    @Test void aRefusedMosaicHasNoCastingParts() {
        SpellResult r = run(ch(20), Selection.of(mosaic("/oselem.json")));   // needs inputs, so it is refused
        assertTrue(r.mosaics().get(0).refused());
        assertTrue(r.mosaics().get(0).castingParts().isEmpty());
    }

    // ---- magic resistance of the Elemi mosaics (user decision 2026-10-16)
    @Test void oselemIsResistedByTheOppositeElement() {
        for (String[] pair : new String[][] {{"ostuz", "osviz"}, {"osviz", "ostuz"}, {"osfold", "oslevego"}, {"oslevego", "osfold"}}) {
            Selection s = Selection.of(mosaic("/oselem.json")).withInputs(Map.of("element", j("\"" + pair[0] + "\""), "form", j("\"test-small\"")));
            SpellResult r = run(ch(20, "anyagi", 2), s);
            assertTrue(r.valid(), r.errors().toString());
            Resistance res = r.mosaics().get(0).resistance().value();
            assertEquals("oppositeElement", res.kind, pair[0]);
            assertEquals(pair[1], res.element, pair[0] + " is resisted by " + pair[1]);
            assertEquals(java.util.Map.of("ostuz", "Ős tűz", "osviz", "Ős víz", "osfold", "Ős föld", "oslevego", "Ős levegő").get(pair[1]), res.elementLabel);
            assertNull(res.pairs, "the pairs table is part of the definition, not of the result");
            assertNull(res.input);
        }
    }

    @Test void paraelemIsResistedByTheSameParaElement() {
        for (String para : new String[] {"ho", "fagy", "feny", "setet"}) {
            Selection s = Selection.of(mosaic("/paraelem.json")).withInputs(Map.of("paraElement", j("\"" + para + "\""), "form", j("\"test-small\"")));
            SpellResult r = run(ch(20, "anyagi", 3), s);
            assertTrue(r.valid(), r.errors().toString());
            Resistance res = r.mosaics().get(0).resistance().value();
            assertEquals("givenParaElement", res.kind);
            assertEquals(para, res.element);
        }
    }

    @Test void elemiEroIsResistedBySfe() {
        SpellResult r = run(ch(20, "anyagi", 3), Selection.of(mosaic("/elemi-ero.json")).withInputs(Map.of("form", j("\"test-small\""))));
        assertTrue(r.valid(), r.errors().toString());
        Resistance res = r.mosaics().get(0).resistance().value();
        assertEquals("armorSfe", res.kind);
        assertNull(res.element);
    }

    @Test void aMissingPairMakesTheResistanceUndefinedWithAReasonAndDoesNotRefuseTheMosaic() {
        Mosaic broken = Json.parseMosaic(text("/oselem.json").replace("\"osfold\": \"oslevego\", ", ""));
        SpellResult r = run(ch(20, "anyagi", 2), Selection.of(broken).withInputs(Map.of("element", j("\"osfold\""), "form", j("\"test-small\""))));
        assertTrue(r.valid(), r.errors().toString());
        var res = r.mosaics().get(0).resistance();
        assertFalse(res.isDefined());
        assertEquals(MsgCode.RESISTANCE_NO_PAIR.key, res.reason().code());
        assertEquals(java.util.List.of("osfold"), res.reason().args());
    }
}
