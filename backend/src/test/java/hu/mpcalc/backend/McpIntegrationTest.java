package hu.mpcalc.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.mpcalc.backend.engine.EngineJson;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** The MCP server of the chat service, called over HTTP (JSON-RPC) exactly as the chat service will call it. */
@SpringBootTest(properties = {
        "spring.datasource.url=${MPCALC_IT_DB_URL:jdbc:postgresql://localhost:5432/mpcalc_backend_it}",
        "spring.datasource.username=${MPCALC_IT_DB_USER:${user.name}}",
        "spring.datasource.password=${MPCALC_IT_DB_PASSWORD:}",
        "mpcalc.mcp-api-key=test-key-for-the-mcp-tests"})
@AutoConfigureMockMvc
class McpIntegrationTest {
    static final String KEY = "test-key-for-the-mcp-tests";
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    // ---- helpers
    private static JsonNode json(String s) { return EngineJson.parse(s); }

    private JsonNode body(MvcResult r) throws Exception {
        String s = r.getResponse().getContentAsString();
        // a streamable HTTP server may answer with JSON or with one server-sent event
        if (s.startsWith("event") || s.startsWith("data")) {
            for (String line : s.split("\n")) if (line.startsWith("data:")) return json(line.substring(5).trim());
        }
        return json(s);
    }

    private MvcResult rpc(String method, ObjectNode params, String key, String language) throws Exception {
        ObjectNode req = EngineJson.mapper().createObjectNode();
        req.put("jsonrpc", "2.0"); req.put("id", 1); req.put("method", method);
        if (params != null) req.set("params", params);
        MockHttpServletRequestBuilder b = post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(req.toString());
        if (key != null) b.header("X-API-Key", key);
        if (language != null) b.header("Accept-Language", language);
        MvcResult r = mvc.perform(b).andReturn();
        if (r.getRequest().isAsyncStarted()) r = mvc.perform(asyncDispatch(r)).andReturn();
        return r;
    }

    /** Calls a tool and returns the JSON text the tool answered with. */
    private JsonNode tool(String name, ObjectNode args, String language) throws Exception {
        ObjectNode params = EngineJson.mapper().createObjectNode();
        params.put("name", name);
        params.set("arguments", args);
        MvcResult r = rpc("tools/call", params, KEY, language);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode res = body(r).get("result");
        assertNotNull(res, body(r).toString());
        assertFalse(res.path("isError").asBoolean(false), res.toString());
        return json(res.at("/content/0/text").asText());
    }

    private ObjectNode obj() { return EngineJson.mapper().createObjectNode(); }

    // ---- security
    @Test
    void theMcpEndpointNeedsTheApiKey() throws Exception {
        MvcResult none = rpc("tools/list", null, null, null);
        assertEquals(401, none.getResponse().getStatus());
        assertEquals("api.mcp.keyRequired", json(none.getResponse().getContentAsString()).get("code").asText());
        assertTrue(json(none.getResponse().getContentAsString()).get("error").asText().contains("API-kulcs"));
        assertEquals(401, rpc("tools/list", null, "wrong-key", null).getResponse().getStatus());
        assertEquals("The MCP call needs a valid API key.",
                json(rpc("tools/list", null, "wrong-key", "en").getResponse().getContentAsString()).get("error").asText());
    }

    @Test
    void theToolsAreListedWithTheirSchemas() throws Exception {
        MvcResult r = rpc("tools/list", null, KEY, null);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode tools = body(r).at("/result/tools");
        Set<String> names = new TreeSet<>();
        tools.forEach(t -> names.add(t.get("name").asText()));
        assertEquals(Set.of("calculate_spell", "find_best_spell", "find_notes", "list_available_mosaics"), names);
        for (JsonNode t : tools) {
            assertFalse(t.get("description").asText().isBlank());
            assertTrue(t.at("/inputSchema/properties/playerId").isObject(), t.toString());
            assertTrue(t.at("/inputSchema/properties/characterId").isObject(), t.toString());
        }
    }

    // ---- players and characters created through the page's REST API
    record Hero(long playerId, long characterId, MockHttpSession session) {}

    private Hero hero(String tiers, int maxMp) throws Exception {
        String name = "mcp-" + UUID.randomUUID();
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/players").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}")).andReturn();
        JsonNode player = json(mvc.perform(post("/session").session(s).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andReturn().getResponse().getContentAsString());
        JsonNode ch = json(mvc.perform(post("/characters").session(s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hős\",\"maxMp\":" + maxMp + ",\"tiers\":" + tiers + "}")).andReturn().getResponse().getContentAsString());
        return new Hero(player.get("id").asLong(), ch.get("id").asLong(), s);
    }

    private ObjectNode args(Hero h) { return obj().put("playerId", h.playerId()).put("characterId", h.characterId()); }

    private ObjectNode selection(String mosaicId, int k, int extraMp) {
        return obj().put("mosaicId", mosaicId).put("k", k).put("extraMp", extraMp);
    }

    @Test
    void calculateSpellGivesTheSameAnswerAsTheRestApi() throws Exception {
        Hero h = hero("{}", 20);
        ObjectNode a = args(h);
        a.putArray("selections").add(selection("alap-furkeszes", 3, 0));
        JsonNode viaMcp = tool("calculate_spell", a, null);
        String body = "{\"characterId\":" + h.characterId() + ",\"selections\":[{\"mosaicId\":\"alap-furkeszes\",\"k\":3}]}";
        JsonNode viaRest = json(mvc.perform(post("/calculate").session(h.session()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString());
        assertEquals(10, viaMcp.at("/totalMp/value/numerator").asInt());
        assertEquals(viaRest.get("totalMp"), viaMcp.get("totalMp"));
        assertEquals(viaRest.get("mosaics").get(0).get("e"), viaMcp.get("mosaics").get(0).get("e"));
    }

    @Test
    void listAvailableMosaicsRespectsTheTierAndTheFilters() throws Exception {
        Hero h = hero("{\"ido\":2}", 50);
        JsonNode all = tool("list_available_mosaics", args(h), null);
        java.util.List<String> ids = new java.util.ArrayList<>();
        all.forEach(m -> ids.add(m.get("id").asText()));
        assertTrue(ids.contains("alap-furkeszes") && ids.contains("ido-nyujtas-ora"), ids.toString());
        assertFalse(ids.contains("mental-beszed-iras"), "needs mental tier 3");
        JsonNode oneSchool = tool("list_available_mosaics", args(h).put("school", "ido"), null);
        oneSchool.forEach(m -> assertEquals("ido", m.get("school").asText()));
        ObjectNode byId = args(h);
        byId.putArray("mosaicIds").add("alap-furkeszes");
        assertEquals(1, tool("list_available_mosaics", byId, null).size());
    }

    @Test
    void anotherPlayersCharacterIsRefusedInTheLanguageOfTheRequest() throws Exception {
        Hero mine = hero("{}", 20);
        Hero other = hero("{}", 20);
        ObjectNode a = obj().put("playerId", other.playerId()).put("characterId", mine.characterId());
        a.putArray("selections").add(selection("alap-furkeszes", 0, 0));
        JsonNode hu = tool("calculate_spell", a, null);
        assertEquals("api.notFound.character", hu.get("code").asText());
        assertEquals("A karakter nem található.", hu.get("error").asText());
        JsonNode en = tool("calculate_spell", a, "en");
        assertEquals("The character was not found.", en.get("error").asText());
    }

    // ---- the search (the questions of the user)
    @Test
    void strongestMentalWriteForAtLeastAnHour() throws Exception {
        Hero h = hero("{\"mental\":3,\"ido\":2}", 100);
        ObjectNode a = args(h);
        a.putArray("candidateMosaicIds").add("mental-beszed-iras");
        a.put("objective", "MAX_E").put("minDurationSeconds", 3600);
        JsonNode found = tool("find_best_spell", a, null);
        JsonNode best = found.get("best");
        assertFalse(best.isNull(), found.toString());
        assertTrue(best.get("durationSeconds").asLong() >= 3600, best.toString());
        assertTrue(best.at("/totalMp/numerator").asInt() <= 100);
        assertFalse(best.get("extensionId").isNull(), "a 1-minute spell needs a time extension to reach an hour");
        // the options are ready for calculate_spell, and the engine confirms the numbers
        ObjectNode check = args(h);
        ArrayNode sel = check.putArray("selections");
        best.get("selections").forEach(sel::add);
        JsonNode calc = tool("calculate_spell", check, null);
        assertTrue(calc.get("valid").asBoolean(), calc.toString());
        assertEquals(best.at("/totalMp/numerator").asInt(), calc.at("/totalMp/value/numerator").asInt());
        assertEquals(best.get("e").decimalValue().compareTo(calc.at("/mosaics/0/e/value").decimalValue()), 0);
        // the trade-off set: stronger options last longer less; none dominates another
        JsonNode pareto = found.get("pareto");
        assertTrue(pareto.size() >= 1);
        for (int i = 1; i < pareto.size(); i++) {
            assertTrue(pareto.get(i - 1).get("e").decimalValue().compareTo(pareto.get(i).get("e").decimalValue()) > 0);
            assertTrue(pareto.get(i - 1).get("durationSeconds").asLong() < pareto.get(i).get("durationSeconds").asLong());
        }
    }

    @Test
    void longestEffectWithAtLeastEOneHundredIsNotPossibleWithTheBudgetAndSaysSo() throws Exception {
        Hero h = hero("{\"mental\":3,\"ido\":2}", 30);
        ObjectNode a = args(h);
        a.putArray("candidateMosaicIds").add("mental-beszed-iras");
        a.put("objective", "MAX_DURATION").put("minE", 100);
        JsonNode found = tool("find_best_spell", a, null);
        assertTrue(found.get("best").isNull());
        assertEquals("api.mcp.noOption", found.at("/reason/code").asText());
        assertTrue(found.at("/reason/text").asText().contains("Nincs olyan változat"));
    }

    @Test
    void unclearOrWrongRequestsComeBackAsErrorsTheModelCanReadAndAskAbout() throws Exception {
        Hero h = hero("{\"mental\":3}", 50);
        // an argument the model left out entirely is refused by the tool schema (the model reads the protocol error)
        ObjectNode missing = args(h);
        missing.put("objective", "MAX_E");
        ObjectNode params = obj();
        params.put("name", "find_best_spell");
        params.set("arguments", missing);
        JsonNode refused = body(rpc("tools/call", params, KEY, null)).get("result");
        assertTrue(refused.path("isError").asBoolean(false), refused.toString());
        assertTrue(refused.at("/content/0/text").asText().contains("candidateMosaicIds"));
        // an empty list reaches the tool, which answers with a message to ask the player about
        ObjectNode none = args(h);
        none.putArray("candidateMosaicIds");
        none.put("objective", "MAX_E");
        assertEquals("api.mcp.noCandidates", tool("find_best_spell", none, null).get("code").asText());
        ObjectNode unknown = args(h);
        unknown.putArray("candidateMosaicIds").add("nincs-ilyen");
        unknown.put("objective", "MAX_E");
        JsonNode e = tool("find_best_spell", unknown, null);
        assertEquals("api.mcp.candidateNotAvailable", e.get("code").asText());
        assertTrue(e.get("error").asText().contains("nincs-ilyen"));
        ObjectNode big = args(h);
        big.putArray("candidateMosaicIds").add("mental-beszed-iras");
        big.put("objective", "MAX_E").put("mpBudget", 5000);
        assertEquals("api.mcp.budgetRange", tool("find_best_spell", big, null).get("code").asText());
    }

    @Test
    void theDelayMosaicHasNoDefinedLengthSoItCannotBeSearchedAsABaseSpell() throws Exception {
        Hero h = hero("{\"ido\":2,\"mental\":3}", 60);
        ObjectNode a = args(h);
        a.putArray("candidateMosaicIds").add("ido-idozites");
        a.put("objective", "MAX_DURATION");
        JsonNode found = tool("find_best_spell", a, null);
        assertTrue(found.get("best").isNull());
        assertEquals("ido-idozites", found.at("/skipped/0/mosaicId").asText());
        // and calculating it shows the engine's own reason for the undefined duration
        ObjectNode c = args(h);
        c.putArray("selections").add(selection("ido-idozites", 0, 0));
        JsonNode calc = tool("calculate_spell", c, null);
        assertFalse(calc.at("/duration/defined").asBoolean());
    }

    // ---- notes (stage 1: keyword search over the notes of database/notes)
    private JsonNode notes(Hero h, String query) throws Exception {
        return tool("find_notes", args(h).put("query", query), null).get("notes");
    }

    @Test
    void aNoteIsFoundByTheWordsOfAQuestionInAnyWordForm() throws Exception {
        Hero h = hero("{}", 20);
        // the questions a player may ask; each must find the Mentál-fonál note (the stored question set grows with the notes)
        for (String q : new String[] {"Mi az a mentál-fonál?", "Hogyan szakad el a mentálfonál?", "Hogyan lehet megszakítani a mentál fonalat?",
                "Mit lát a mentálszem?", "Mi a tárgyszimpátia?", "Mit jelent a szimpatikus kapcsolat?", "Mi az a fonal?"}) {
            JsonNode found = notes(h, q);
            assertEquals(1, found.size(), q);
            assertEquals("mental-fonal", found.get(0).get("id").asText(), q);
        }
        JsonNode note = notes(h, "mentál-fonál").get(0);
        assertEquals("Mentál-fonál", note.get("title").asText());
        assertEquals("magic", note.get("category").asText());
        assertEquals("mental", note.get("school").asText());
        assertEquals("Első Törvénykönyv, mentálmágia fejezet", note.get("source").asText());
        assertTrue(note.get("text").asText().startsWith("Írás vagy olvasás típusú mentáloperáció során"));
        assertTrue(note.get("text").asText().contains("erősségét"), "the corrected text is stored");
    }

    @Test
    void aQuestionWithoutAKeywordOfANoteGetsNoNote() throws Exception {
        Hero h = hero("{}", 20);
        // words that are only in the text of the note (varázskör, áldozat, írás) or in no note do not find it
        for (String q : new String[] {"Mennyibe kerül a Fürkészés?", "Hogyan működik a Térkapu?", "Mennyi MP a Beszéd – Írás mozaik?",
                "Mi az a varázskör?", "Mit tud a varázsló az áldozat központjáról?", "", "  ?! "}) {
            assertEquals(0, notes(h, q).size(), "'" + q + "'");
        }
    }

    @Test
    void theNoteCategoryFilterAndTheLimitWork() throws Exception {
        Hero h = hero("{}", 20);
        assertEquals(1, tool("find_notes", args(h).put("query", "mentál-fonál").put("category", "magic"), null).get("notes").size());
        assertEquals(0, tool("find_notes", args(h).put("query", "mentál-fonál").put("category", "world"), null).get("notes").size());
        assertEquals(1, tool("find_notes", args(h).put("query", "mentál-fonál").put("limit", 0), null).get("notes").size(), "a limit below 1 still gives one");
    }

    @Test
    void theQueryNeverReachesTheSearchSyntax() throws Exception {
        Hero h = hero("{}", 20);
        for (String q : new String[] {"fonál & !(x)", "'; drop table notes; --", "fonál:* | <->", "\\"}) notes(h, q);
        assertEquals(1, notes(h, "mentál-fonál").size(), "the table is untouched");
    }

    @Test
    void aNoteComesWithTheIdAndTitleOfTheNotesItIsLinkedTo() throws Exception {
        Hero h = hero("{}", 20);
        // two test notes that link to each other, one link to a note that is gone; removed again at the end
        String ins = "INSERT INTO notes (id, title, category, keywords, links, text, source) VALUES (?, ?, 'magic', ?, ?, 'szöveg', 'forrás')";
        try {
            jdbc.update(ins, "zz-teszt-egy", "Zzteszt Egy", "zzteszt egy", "zz-teszt-ketto, zz-nincs-ilyen");
            jdbc.update(ins, "zz-teszt-ketto", "Zzteszt Kettő", "zzteszt ketto", "zz-teszt-egy");
            JsonNode one = notes(h, "zzteszt egy").get(0);
            assertEquals("zz-teszt-egy", one.get("id").asText());
            assertEquals(1, one.get("links").size(), "the link to a missing note is left out");
            assertEquals("zz-teszt-ketto", one.at("/links/0/id").asText());
            assertEquals("Zzteszt Kettő", one.at("/links/0/title").asText());
            assertEquals(0, notes(h, "mentál-fonál").get(0).get("links").size(), "a note without links has an empty list");
            // the linked note is read by searching for its title
            assertEquals("zz-teszt-ketto", notes(h, "Zzteszt Kettő").get(0).get("id").asText());
        } finally {
            jdbc.update("DELETE FROM notes WHERE id LIKE 'zz-teszt-%'");
        }
        assertEquals(1, notes(h, "mentál-fonál").size());
    }

    @Test
    void findNotesNeedsAKnownPlayer() throws Exception {
        ObjectNode a = obj().put("playerId", 999_999_999L).put("characterId", 1).put("query", "mentál-fonál");
        assertTrue(tool("find_notes", a, null).has("error"));
    }
}
