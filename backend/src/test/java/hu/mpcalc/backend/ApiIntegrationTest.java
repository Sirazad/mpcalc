package hu.mpcalc.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.mpcalc.backend.engine.EngineJson;
import hu.mpcalc.backend.model.MosaicEntity;
import hu.mpcalc.backend.repo.Repositories.MosaicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Runs against a real PostgreSQL (Flyway applies V1 to V4 on the first run). Database URL, user and password come from
 * MPCALC_IT_DB_URL / MPCALC_IT_DB_USER / MPCALC_IT_DB_PASSWORD; defaults: local server, database mpcalc_backend_it.
 * Test data uses unique names, so the test can be run again on the same database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${MPCALC_IT_DB_URL:jdbc:postgresql://localhost:5432/mpcalc_backend_it}",
        "spring.datasource.username=${MPCALC_IT_DB_USER:${user.name}}",
        "spring.datasource.password=${MPCALC_IT_DB_PASSWORD:}"})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired MosaicRepository mosaics;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Value("${server.servlet.session.cookie.path:}") String cookiePath;

    private static final String SPELL = "{\"characterId\":%d,\"selections\":[{\"mosaicId\":\"alap-furkeszes\",\"k\":%d}]}";

    private JsonNode json(MvcResult r) throws Exception { return EngineJson.parse(r.getResponse().getContentAsString()); }

    private MockMvc call() { return mvc; }

    private MvcResult send(MockHttpServletRequestBuilder b, MockHttpSession s, String body, int status) throws Exception {
        if (s != null) b.session(s);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body);
        return call().perform(b).andReturn();
    }

    private MockHttpSession login(String name) throws Exception {
        MockHttpSession s = new MockHttpSession();
        assertEquals(201, send(post("/players"), null, "{\"name\":\"" + name + "\"}", 201).getResponse().getStatus());
        assertEquals(200, send(post("/session"), s, "{\"name\":\"" + name + "\"}", 200).getResponse().getStatus());
        return s;
    }

    private long newCharacter(MockHttpSession s, int maxMp, String tiers) throws Exception {
        MvcResult r = send(post("/characters"), s, "{\"name\":\"Hős\",\"maxMp\":" + maxMp + ",\"tiers\":" + tiers + "}", 201);
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return json(r).get("id").asLong();
    }

    @Test
    void flywayLoadedTheSeed() {
        assertEquals(101, mosaics.count());
        assertEquals(11, jdbc.queryForObject("select count(*) from forms", Integer.class), "the 11 forms of the user's form table");
        assertEquals(3, jdbc.queryForObject("select count(*) from material_groups", Integer.class));
    }

    @Test
    void notLoggedInIsRefused() throws Exception {
        assertEquals(401, send(get("/characters"), null, null, 401).getResponse().getStatus());
        assertEquals(401, send(post("/calculate"), null, "{}", 401).getResponse().getStatus());
    }

    @Test
    void calculateThenCastLowersOnlyCurrentMp() throws Exception {
        String name = "t-" + UUID.randomUUID();
        MockHttpSession s = login(name);
        long id = newCharacter(s, 20, "{}");

        MvcResult calc = send(post("/calculate"), s, SPELL.formatted(id, 3), 200);
        assertEquals(200, calc.getResponse().getStatus(), calc.getResponse().getContentAsString());
        JsonNode c = json(calc);
        assertTrue(c.get("valid").asBoolean(), c.toString());
        assertEquals(10, c.at("/totalMp/value/numerator").asInt());
        assertEquals(1, c.at("/totalMp/value/denominator").asInt());
        assertEquals(20, json(send(get("/characters/" + id), s, null, 200)).get("currentMp").asInt(), "calculate must not spend MP");

        MvcResult cast1 = send(post("/cast"), s, SPELL.formatted(id, 3), 200);
        assertEquals(200, cast1.getResponse().getStatus(), cast1.getResponse().getContentAsString());
        assertEquals(10, json(cast1).get("currentMp").asInt());
        MvcResult cast2 = send(post("/cast"), s, SPELL.formatted(id, 3), 200);
        assertEquals(0, json(cast2).get("currentMp").asInt());

        // No MP left: calculate shows the overspend, cast still happens (chaos magic), MP stays 0 and the overspend is reported.
        JsonNode over = json(send(post("/calculate"), s, SPELL.formatted(id, 0), 200));
        assertEquals(7, over.at("/overspentMp/numerator").asInt());
        assertEquals("alapvarazslatok", over.at("/schools/0").asText());
        MvcResult cast3 = send(post("/cast"), s, SPELL.formatted(id, 0), 200);
        assertEquals(200, cast3.getResponse().getStatus(), cast3.getResponse().getContentAsString());
        JsonNode c3 = json(cast3);
        assertEquals(0, c3.get("currentMp").asInt());
        assertEquals(7, c3.get("overspentMp").asInt());
        assertEquals("alapvarazslatok", c3.at("/schools/0").asText());
        assertEquals(0, json(send(get("/characters/" + id), s, null, 200)).get("currentMp").asInt());
        assertEquals(0, json(cast1).get("overspentMp").asInt());
        assertEquals(0, json(cast1).get("schools").size(), "no schools listed when nothing is overspent");

        assertEquals(1, jdbc.queryForObject("select count(*) from audit_log where entity_type='character' and entity_id=? and action='create'",
                Integer.class, String.valueOf(id)));
        assertEquals(3, jdbc.queryForObject("select count(*) from audit_log where entity_type='character' and entity_id=? and action='update'",
                Integer.class, String.valueOf(id)));
    }

    @Test
    void tierLimitsAndPlayerIsolation() throws Exception {
        MockHttpSession a = login("a-" + UUID.randomUUID());
        MockHttpSession b = login("b-" + UUID.randomUUID());
        long idA = newCharacter(a, 20, "{}");

        MosaicEntity high = mosaics.findAll().stream().filter(m -> m.tier != null && m.tier >= 1 && !m.school.equals("alapvarazslatok")).findFirst().orElseThrow();
        JsonNode available = json(send(get("/characters/" + idA + "/mosaics"), a, null, 200)).get("mosaics");
        for (JsonNode m : available) assertNotEquals(high.id, m.get("id").asText(), "mosaic above the tier must not be offered");

        MvcResult above = send(post("/calculate"), a, "{\"characterId\":" + idA + ",\"selections\":[{\"mosaicId\":\"" + high.id + "\"}]}", 200);
        assertEquals(200, above.getResponse().getStatus());
        assertFalse(json(above).get("valid").asBoolean(), "engine must refuse a mosaic above the tier");

        assertEquals(404, send(get("/characters/" + idA), b, null, 404).getResponse().getStatus());
        assertEquals(404, send(post("/calculate"), b, SPELL.formatted(idA, 0), 404).getResponse().getStatus());
        assertEquals(404, send(post("/cast"), b, SPELL.formatted(idA, 0), 404).getResponse().getStatus());
    }

    @Test
    void savedSpellRoundTrip() throws Exception {
        MockHttpSession s = login("s-" + UUID.randomUUID());
        long id = newCharacter(s, 20, "{}");
        MvcResult saved = send(post("/characters/" + id + "/spells"), s, "{\"name\":\"Fürkészés\",\"content\":{\"selections\":[{\"mosaicId\":\"alap-furkeszes\",\"k\":1}]}}", 201);
        assertEquals(201, saved.getResponse().getStatus(), saved.getResponse().getContentAsString());
        long spellId = json(saved).get("id").asLong();
        JsonNode loaded = json(send(get("/spells/" + spellId), s, null, 200));
        assertEquals("Fürkészés", loaded.get("name").asText());
        assertEquals(1, loaded.at("/content/selections/0/k").asInt());
        assertEquals(204, send(delete("/spells/" + spellId), s, null, 204).getResponse().getStatus());
        assertEquals(404, send(get("/spells/" + spellId), s, null, 404).getResponse().getStatus());
    }

    @Test
    void adminEditNeedsAdminRoleAndIsAudited() throws Exception {
        String name = "adm-" + UUID.randomUUID();
        MockHttpSession s = login(name);
        MosaicEntity m = mosaics.findById("alap-furkeszes").orElseThrow();
        String original = m.definition;
        ObjectNode edited = (ObjectNode) EngineJson.parse(original);
        edited.put("effect", "test edit " + UUID.randomUUID());

        assertEquals(403, send(put("/admin/mosaics/alap-furkeszes"), s, edited.toString(), 403).getResponse().getStatus());

        jdbc.update("update players set is_admin = true where name = ?", name);
        try {
            MvcResult ok = send(put("/admin/mosaics/alap-furkeszes"), s, edited.toString(), 200);
            assertEquals(200, ok.getResponse().getStatus(), ok.getResponse().getContentAsString());
            assertEquals(edited.get("effect").asText(), EngineJson.parse(mosaics.findById("alap-furkeszes").orElseThrow().definition).get("effect").asText());
            assertEquals(1, jdbc.queryForObject("select count(*) from audit_log where entity_type='mosaic' and entity_id='alap-furkeszes' and player_name=? and data_before is not null and data_after is not null",
                    Integer.class, name));
            assertEquals(400, send(put("/admin/mosaics/alap-destrukcio"), s, edited.toString(), 400).getResponse().getStatus(), "id in the body must equal the path id");
        } finally {
            send(put("/admin/mosaics/alap-furkeszes"), s, original, 200);
        }
    }

    @Test
    void materialGroupsAreServed() throws Exception {
        MockHttpSession s = login("g-" + UUID.randomUUID());
        JsonNode groups = json(send(get("/material-groups"), s, null, 200));
        assertEquals(3, groups.size());
        assertEquals("I", groups.get(0).get("id").asText());
        assertTrue(groups.get(0).get("examples").size() > 0);
    }

    /** Builds the page's view of the world the way the front end does: from the backend's own responses. */
    private ObjectNode held(MockHttpSession s, long id, int maxMp, int currentMp, String tiers) throws Exception {
        ObjectNode h = EngineJson.mapper().createObjectNode();
        h.put("characterId", id);
        ObjectNode ch = h.putObject("character");
        ch.put("maxMp", maxMp); ch.put("currentMp", currentMp);
        ch.set("tiers", EngineJson.parse(tiers));
        h.set("mosaicVersions", json(send(get("/characters/" + id + "/mosaics"), s, null, 200)).get("versions"));
        ObjectNode forms = h.putObject("forms");
        for (JsonNode f : json(send(get("/forms"), s, null, 200))) forms.put(f.get("id").asText(), f.get("tier").asInt());
        h.set("selections", EngineJson.parse("[{\"mosaicId\":\"alap-furkeszes\",\"k\":3}]"));
        h.set("savedSpellIds", EngineJson.mapper().createArrayNode());
        return h;
    }

    private JsonNode validate(MockHttpSession s, ObjectNode held) throws Exception {
        MvcResult r = send(post("/validate"), s, held.toString(), 200);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return json(r);
    }

    private static java.util.Set<String> codes(JsonNode v) {
        java.util.Set<String> c = new java.util.TreeSet<>();
        v.get("differences").forEach(d -> c.add(d.get("code").asText()));
        return c;
    }

    @Test
    void validateReportsWhatIsOutOfSync() throws Exception {
        String name = "v-" + UUID.randomUUID();
        MockHttpSession s = login(name);
        long id = newCharacter(s, 20, "{\"anyagi\":2}");
        MvcResult saved = send(post("/characters/" + id + "/spells"), s, "{\"name\":\"x\",\"content\":{}}", 201);
        long spellId = json(saved).get("id").asLong();

        ObjectNode held = held(s, id, 20, 20, "{\"anyagi\":2}");
        held.putArray("savedSpellIds").add(spellId);
        ObjectNode total = held.putObject("displayedTotalMp");
        total.put("numerator", 10); total.put("denominator", 1);
        JsonNode ok = validate(s, held);
        assertTrue(ok.get("inSync").asBoolean(), ok.toString());
        assertEquals(10, ok.at("/result/totalMp/value/numerator").asInt());

        // a number shown on the page that the backend does not agree with
        total.put("numerator", 11);
        assertEquals(java.util.Set.of("TOTAL_DIFFERS"), codes(validate(s, held)));
        total.put("numerator", 10);

        // the page holds other MP and tiers than the database
        ((ObjectNode) held.get("character")).put("currentMp", 5);
        ((ObjectNode) held.get("character").get("tiers")).put("anyagi", 3);
        assertEquals(java.util.Set.of("CURRENT_MP_DIFFERS", "TIER_DIFFERS"), codes(validate(s, held)));
        ((ObjectNode) held.get("character")).put("currentMp", 20);
        ((ObjectNode) held.get("character").get("tiers")).put("anyagi", 2);

        // a saved spell removed from outside the page
        assertEquals(204, send(delete("/spells/" + spellId), s, null, 204).getResponse().getStatus());
        JsonNode gone = validate(s, held);
        assertEquals(java.util.Set.of("SAVED_SPELL_MISSING"), codes(gone));
        held.putArray("savedSpellIds");

        // the tier is lowered from outside the page: held mosaics are no longer available to the character
        assertEquals(200, send(put("/characters/" + id), s, "{\"tiers\":{\"anyagi\":0}}", 200).getResponse().getStatus());
        JsonNode lowered = validate(s, held);
        assertTrue(codes(lowered).contains("TIER_DIFFERS"));
        assertTrue(codes(lowered).contains("MOSAIC_NOT_AVAILABLE"), codes(lowered).toString());

        // another player cannot validate this character
        MockHttpSession other = login("o-" + UUID.randomUUID());
        assertEquals(404, send(post("/validate"), other, held.toString(), 404).getResponse().getStatus());
        assertEquals(401, send(post("/validate"), null, held.toString(), 401).getResponse().getStatus());
    }

    @Test
    void validateSeesAnEditedMosaic() throws Exception {
        String name = "ve-" + UUID.randomUUID();
        MockHttpSession s = login(name);
        long id = newCharacter(s, 20, "{}");
        ObjectNode held = held(s, id, 20, 20, "{}");
        assertTrue(validate(s, held).get("inSync").asBoolean());

        MosaicEntity m = mosaics.findById("alap-furkeszes").orElseThrow();
        String original = m.definition;
        ObjectNode edited = (ObjectNode) EngineJson.parse(original);
        edited.put("effect", "validate edit " + UUID.randomUUID());
        jdbc.update("update players set is_admin = true where name = ?", name);
        try {
            assertEquals(200, send(put("/admin/mosaics/alap-furkeszes"), s, edited.toString(), 200).getResponse().getStatus());
            JsonNode v = validate(s, held);
            assertFalse(v.get("inSync").asBoolean());
            assertEquals(java.util.Set.of("MOSAIC_CHANGED"), codes(v));
        } finally {
            send(put("/admin/mosaics/alap-furkeszes"), s, original, 200);
        }
    }

    @Test
    void massIsChargedInWholeBlocksAndSoTheCostIsAlwaysWhole() throws Exception {
        MockHttpSession s = login("m-" + UUID.randomUUID());
        long id = newCharacter(s, 100, "{\"anyagi\":2}");
        String body = "{\"characterId\":" + id + ",\"selections\":[{\"mosaicId\":\"anyagi-term-atformazas\",\"inputs\":{\"composition\":[%s]}}]}";
        // group I: 1 MP per 1000 g; group II: 1 MP per 500 g; group III: 1 MP per 1 g
        JsonNode half = json(send(post("/calculate"), s, body.formatted("{\"group\":\"I\",\"massG\":500}"), 200));
        assertEquals(1, half.at("/totalMp/value/numerator").asInt(), half.toString());
        assertEquals(1, half.at("/totalMp/value/denominator").asInt());
        assertEquals(0, half.get("warnings").size());
        JsonNode next = json(send(post("/calculate"), s, body.formatted("{\"group\":\"II\",\"massG\":501}"), 200));
        assertEquals(2, next.at("/totalMp/value/numerator").asInt(), "501 g of group II starts a second 500 g block");
        // only a single row per material group: two rows of the same group are refused
        JsonNode twice = json(send(post("/calculate"), s, body.formatted("{\"group\":\"I\",\"massG\":300},{\"group\":\"I\",\"massG\":300}"), 200));
        assertFalse(twice.get("valid").asBoolean(), twice.toString());
        // a spell with a fractional-looking mass can now be cast: whole MP, no refusal
        MvcResult cast = send(post("/cast"), s, body.formatted("{\"group\":\"I\",\"massG\":500},{\"group\":\"III\",\"massG\":20}"), 200);
        assertEquals(200, cast.getResponse().getStatus(), cast.getResponse().getContentAsString());
        assertEquals(79, json(cast).get("currentMp").asInt());
    }

    @Test
    void messagesComeInHungarianByDefaultAndInEnglishOnRequest() throws Exception {
        MockHttpSession s = login("l-" + UUID.randomUUID());
        long id = newCharacter(s, 100, "{\"anyagi\":2}");
        String twice = "{\"characterId\":" + id + ",\"selections\":[{\"mosaicId\":\"anyagi-term-atformazas\",\"inputs\":{\"composition\":"
                + "[{\"group\":\"I\",\"massG\":300},{\"group\":\"I\",\"massG\":300}]}}]}";

        // engine error inside a result: Hungarian by default, code and arguments kept
        JsonNode hu = json(send(post("/calculate"), s, twice, 200));
        JsonNode err = hu.at("/errors/0");
        assertEquals("engine.error.compositionDuplicateGroup", err.get("code").asText());
        assertEquals("composition", err.at("/args/0").asText());
        assertEquals("Átformázás", err.get("subject").asText());
        assertTrue(err.get("text").asText().contains("többször szerepel"), err.get("text").asText());
        assertFalse(err.get("text").asText().contains("appears"));

        // the same request with Accept-Language: en
        MvcResult enResult = call().perform(post("/calculate").session(s).header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON).content(twice)).andReturn();
        String en = json(enResult).at("/errors/0/text").asText();
        assertTrue(en.contains("appears more than once"), en);

        // a language that is not supported falls back to Hungarian
        MvcResult de = call().perform(post("/calculate").session(s).header("Accept-Language", "de")
                .contentType(MediaType.APPLICATION_JSON).content(twice)).andReturn();
        assertTrue(json(de).at("/errors/0/text").asText().contains("többször szerepel"));

        // an undefined value carries its reason as a message too (nested time kind is translated)
        JsonNode noE = json(send(post("/calculate"), s, "{\"characterId\":" + id + ",\"selections\":[{\"mosaicId\":\"anyagi-term-atformazas\",\"inputs\":{\"composition\":[{\"group\":\"I\",\"massG\":300}]}}]}", 200));
        assertEquals("Ehhez a mozaikhoz nincs erősség (E) megadva.", noE.at("/mosaics/0/e/reason/text").asText());

        // API errors: {"error": text, "code": key}
        MvcResult notFound = send(get("/characters/999999"), s, null, 404);
        assertEquals(404, notFound.getResponse().getStatus());
        assertEquals("A karakter nem található.", json(notFound).get("error").asText());
        assertEquals("api.notFound.character", json(notFound).get("code").asText());
        MvcResult notLoggedIn = send(get("/characters"), null, null, 401);
        assertEquals("Nem vagy bejelentkezve.", json(notLoggedIn).get("error").asText());
        MvcResult notLoggedInEn = call().perform(get("/characters").header("Accept-Language", "en")).andReturn();
        assertEquals("You are not logged in.", json(notLoggedInEn).get("error").asText());

        // a refused cast: Hungarian error plus the translated engine result
        MvcResult refused = send(post("/cast"), s, twice, 422);
        assertEquals(422, refused.getResponse().getStatus());
        JsonNode r = json(refused);
        assertEquals("A varázslat nem érvényes.", r.get("error").asText());
        assertTrue(r.at("/result/errors/0/text").asText().contains("többször szerepel"));

        // validate differences carry their text
        ObjectNode held = held(s, id, 100, 100, "{\"anyagi\":0}");
        JsonNode v = validate(s, held);
        assertFalse(v.get("inSync").asBoolean());
        for (JsonNode d : v.get("differences")) assertFalse(d.get("text").asText().isBlank(), d.toString());
    }

    @Test
    void wholeNumberFieldsRefuseDecimalsInsteadOfCuttingThem() throws Exception {
        MockHttpSession s = login("w-" + UUID.randomUUID());
        long id = newCharacter(s, 100, "{\"ido\":2}");
        String spell = "{\"characterId\":" + id + ",\"selections\":[{\"mosaicId\":\"ido-nyujtas-ora\",%s}]}";
        // extra MP and power step: a whole number is accepted, a decimal is refused with a Hungarian message
        assertEquals(200, send(post("/calculate"), s, spell.formatted("\"extraMp\":2"), 200).getResponse().getStatus());
        MvcResult dec = send(post("/calculate"), s, spell.formatted("\"extraMp\":1.5"), 400);
        assertEquals(400, dec.getResponse().getStatus(), dec.getResponse().getContentAsString());
        assertEquals("api.bad.notWholeNumber", json(dec).get("code").asText());
        assertTrue(json(dec).get("error").asText().contains("egész szám"), json(dec).get("error").asText());
        assertEquals(400, send(post("/calculate"), s, spell.formatted("\"k\":0.5"), 400).getResponse().getStatus());
        assertEquals(400, send(post("/cast"), s, spell.formatted("\"extraMp\":1.5"), 400).getResponse().getStatus());
        // the character: MP and tiers are whole numbers too
        MvcResult badBody = send(post("/characters"), s, "{\"name\":\"X\",\"maxMp\":20.5}", 400);
        assertEquals(400, badBody.getResponse().getStatus());
        assertEquals("api.bad.unreadableBody", json(badBody).get("code").asText());
        assertTrue(json(badBody).get("error").asText().contains("tizedes tört"), json(badBody).get("error").asText());
        assertEquals(400, send(put("/characters/" + id), s, "{\"currentMp\":10.5}", 400).getResponse().getStatus());
        assertEquals(400, send(put("/characters/" + id), s, "{\"tiers\":{\"anyagi\":2.5}}", 400).getResponse().getStatus());
        assertEquals(100, json(send(get("/characters/" + id), s, null, 200)).get("maxMp").asInt());
    }

    @Test
    void theLoginCookieIsSentToTheChatServiceToo() {
        // With the default the cookie is limited to the context path /api and the browser would never send it to /chat/...,
        // so the chat service could not tell who is asking.
        assertEquals("/", cookiePath);
    }

    @Test
    void materialGroupsHaveHungarianExamplesAndTheMagicalMaterialsLineIsServed() throws Exception {
        MockHttpSession s = login("g-" + UUID.randomUUID());
        java.util.Map<String, JsonNode> byId = new java.util.HashMap<>();
        for (JsonNode g : json(send(get("/material-groups"), s, null, 200))) byId.put(g.get("id").asText(), g);
        assertEquals("Minden más nem mágikus anyag.", byId.get("I").get("examples").get(0).asText());
        assertEquals(2, byId.get("II").get("examples").size());
        assertTrue(byId.get("III").get("examples").get(0).asText().startsWith("Aryan, ezüst, platina"));
        JsonNode texts = json(send(get("/texts"), s, null, 200));
        assertEquals("Mágikus anyagok: Mithrill, abbitacél, drágakövek.", texts.get("materials.magical").asText());
        assertEquals("Lehet egyben kimondani varázslatot az érintkező tárgyakra.", texts.get("materials.touching").asText());
        assertEquals(401, send(get("/texts"), null, null, 401).getResponse().getStatus(), "texts need a login like the other reference data");
    }

    @Test
    void naturalMaterialMosaicsHaveTheirHungarianEffectText() throws Exception {
        MockHttpSession s = login("n-" + UUID.randomUUID());
        long id = newCharacter(s, 50, "{\"anyagi\":4}");
        String body = "{\"characterId\":" + id + ",\"selections\":[{\"mosaicId\":\"%s\",\"inputs\":{\"composition\":[{\"group\":\"I\",\"massG\":1000}]}}]}";
        java.util.Map<String, String> expectedStart = java.util.Map.of(
                "anyagi-term-atformazas", "A varázslattal egy tárgy FORMÁJÁT és SZÍNÉT változtathatja meg a varázsló.",
                "anyagi-term-atalakitas", "A varázsló az anyag MINŐSÉGÉT változtathatja meg",
                "anyagi-term-teremtes", "Nem volt – van.",
                "anyagi-term-visszavaltoztatas", "Lélek alapján újra alkotja.",
                "anyagi-term-telekinezis", "Mágikus anyagra is hat a láthatatlanság illetve a telekinézis.");
        for (var e : expectedStart.entrySet()) {
            JsonNode r = json(send(post("/calculate"), s, body.formatted(e.getKey()), 200));
            assertTrue(r.at("/mosaics/0/effect/value").asText().startsWith(e.getValue()), e.getKey() + ": " + r.at("/mosaics/0/effect"));
        }
        JsonNode none = json(send(post("/calculate"), s, body.formatted("anyagi-term-halmazallapotvaltas"), 200));
        assertFalse(none.at("/mosaics/0/effect/defined").asBoolean(), "Halmazállapotváltás has no text in the source");
    }

    @Test
    void formsCarryTheirRegenerationTimeAsText() throws Exception {
        MockHttpSession s = login("f-" + UUID.randomUUID());
        java.util.Map<String, JsonNode> byId = new java.util.HashMap<>();
        for (JsonNode f : json(send(get("/forms"), s, null, 200))) byId.put(f.get("id").asText(), f);
        assertEquals("0,5 szegmens", byId.get("fal").get("regeneration").asText());
        assertEquals("5 k\u00f6r", byId.get("kard").get("regeneration").asText());
        assertTrue(byId.get("nyil").get("regeneration").isNull(), "\"-\" in the form table is null");
        assertTrue(byId.get("nyil").get("description").asText().startsWith("Támadó varázslat. A varázsló ujjából"), "Nyíl has its description");
        assertTrue(byId.get("kitores").get("description").asText().contains("E/4 méter sugarú kör"), "Kitörés radius E/4 (user decision 2026-10-17)");
        assertTrue(byId.get("szonyeg").get("description").asText().contains("félkörönkénti sebzése"), "Szőnyeg damage per félkör for now");
        assertTrue(byId.get("fal").get("description").isNull(), "Fal has no description in the source");
        for (String id : byId.keySet())
            assertEquals(id.equals("kard") || id.equals("aura"), byId.get(id).get("movable").asBoolean(), id + ": only Kard and Aura are movable");
        assertTrue(byId.get("szonyeg").get("description").asText().contains("kel birokra"), "the opposing-element sentence of the Szőnyeg");
        assertEquals(5, byId.get("fal").at("/duration/time/amounts/kor").asInt(), "Fal stays 5 k\u00f6r (user decision 2026-10-17)");
    }

    @Test
    void elemiMosaicsAreResistedAsTheRulesSay() throws Exception {
        MockHttpSession s = login("e-" + UUID.randomUUID());
        long id = newCharacter(s, 50, "{\"anyagi\":3}");
        String body = "{\"characterId\":" + id + ",\"selections\":[{\"mosaicId\":\"%s\",\"inputs\":{%s}}]}";
        // Őselem idézése: the opposite element resists (tűz-víz, levegő-föld)
        for (String[] pair : new String[][] {{"ostuz", "osviz"}, {"osviz", "ostuz"}, {"osfold", "oslevego"}, {"oslevego", "osfold"}}) {
            JsonNode r = json(send(post("/calculate"), s, body.formatted("anyagi-elemi-oselem-idezese", "\"element\":\"" + pair[0] + "\",\"form\":\"nyil\""), 200));
            assertTrue(r.get("valid").asBoolean(), r.toString());
            assertEquals("oppositeElement", r.at("/mosaics/0/resistance/value/kind").asText());
            assertEquals(pair[1], r.at("/mosaics/0/resistance/value/element").asText(), pair[0]);
            assertEquals(java.util.Map.of("ostuz", "Ős tűz", "osviz", "Ős víz", "osfold", "Ős föld", "oslevego", "Ős levegő").get(pair[1]),
                    r.at("/mosaics/0/resistance/value/elementLabel").asText(), "the Hungarian name of the resisting element");
            assertTrue(r.at("/mosaics/0/resistance/value/pairs").isMissingNode(), "the pairs table stays in the definition");
        }
        // Paraelem idézése: the same para-element
        for (String para : new String[] {"ho", "fagy", "feny", "setet"}) {
            JsonNode r = json(send(post("/calculate"), s, body.formatted("anyagi-elemi-paraelem-idezese", "\"paraElement\":\"" + para + "\",\"form\":\"nyil\""), 200));
            assertEquals("givenParaElement", r.at("/mosaics/0/resistance/value/kind").asText());
            assertEquals(para, r.at("/mosaics/0/resistance/value/element").asText());
            assertEquals(java.util.Map.of("ho", "Elemi hő", "fagy", "Elemi hideg", "feny", "Elemi fény", "setet", "Elemi sötétség").get(para),
                    r.at("/mosaics/0/resistance/value/elementLabel").asText());
        }
        // Elemi erő idézése: resisted by SFÉ (user decision 2026-10-16, after the table of the source)
        JsonNode ero = json(send(post("/calculate"), s, body.formatted("anyagi-elemi-elemi-ero-idezese", "\"form\":\"nyil\""), 200));
        assertEquals("armorSfe", ero.at("/mosaics/0/resistance/value/kind").asText());
        String eroEffect = ero.at("/mosaics/0/effect/value").asText();
        assertTrue(eroEffect.startsWith("A mágikus energiákat felhasználva"), eroEffect);
        assertTrue(eroEffect.contains("Az elemi erőből létrehozott szőnyeg E-nként 5 kg súlyt képes megtartani."), "the user's sentence");
        JsonNode oselem = json(send(post("/calculate"), s, body.formatted("anyagi-elemi-oselem-idezese", "\"element\":\"ostuz\",\"form\":\"nyil\""), 200));
        assertTrue(oselem.at("/mosaics/0/effect/value").asText().startsWith("A varázsló őselemet teremt"));
    }
}
