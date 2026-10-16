package hu.mpcalc.backend.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import hu.mpcalc.backend.engine.EngineJson;
import hu.mpcalc.backend.model.MosaicEntity;
import hu.mpcalc.backend.model.Player;
import hu.mpcalc.backend.service.CatalogService;
import hu.mpcalc.backend.service.CharacterService;
import hu.mpcalc.backend.service.MessageService;
import hu.mpcalc.backend.service.PlayerService;
import hu.mpcalc.backend.service.SpellSearch;
import hu.mpcalc.backend.service.SpellService;
import hu.mpcalc.backend.web.ApiException;
import hu.mpcalc.engine.MsgException;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The tools of the chat service. The chat service sets playerId and characterId from the verified login session (never the
 * model); the backend trusts it about them but still checks that the character belongs to the player. Every number comes from
 * the engine. Results are JSON text with the engine's messages already translated. A problem is returned as
 * {"error": text, "code": key} so that the model can ask the player instead of guessing.
 * The tool descriptions are English because they are read by the model, not by the player.
 */
@Component
public class MpcalcTools {
    public record SelectionArg(
            @McpToolParam(description = "Mosaic id, e.g. mental-beszed-iras", required = true) String mosaicId,
            @McpToolParam(description = "Values of the mosaic's inputs by input id (choice: string, number: number, form: form id, composition: list of {group: I|II|III, massG: grams})", required = false) Map<String, Object> inputs,
            @McpToolParam(description = "Power step k, 0 = base power. E = baseE + k * step E", required = false) Integer k,
            @McpToolParam(description = "Ticked option ids", required = false) List<String> options,
            @McpToolParam(description = "Extra MP spent on a time extension (whole number)", required = false) Integer extraMp) {}

    private final PlayerService players;
    private final CharacterService characters;
    private final CatalogService catalog;
    private final SpellService spells;
    private final SpellSearch search;
    private final MessageService messages;

    public MpcalcTools(PlayerService players, CharacterService characters, CatalogService catalog, SpellService spells,
                       SpellSearch search, MessageService messages) {
        this.players = players; this.characters = characters; this.catalog = catalog; this.spells = spells;
        this.search = search; this.messages = messages;
    }

    @McpTool(name = "list_available_mosaics", description = "List the mosaics (spell building blocks) the character may use, with their data: id, name, school, tier, "
            + "cost, power (E) steps, duration, casting time and inputs. Use it to find mosaic ids (for example the mental 'write' mosaics end in -iras). "
            + "Optionally filter by school id or by mosaic ids to keep the answer small.")
    public String listAvailableMosaics(
            @McpToolParam(description = "Player id (set by the chat service)", required = true) Long playerId,
            @McpToolParam(description = "Character id (set by the chat service)", required = true) Long characterId,
            @McpToolParam(description = "Only this school id, e.g. mental, anyagi, ido, ter, asztral, nekromancia, alapvarazslatok", required = false) String school,
            @McpToolParam(description = "Only these mosaic ids", required = false) List<String> mosaicIds) {
        return guard(() -> {
            Player p = players.byId(playerId);
            var c = characters.owned(p, characterId);
            ArrayNode out = EngineJson.mapper().createArrayNode();
            for (MosaicEntity m : catalog.available(c)) {
                if (school != null && !school.isBlank() && !school.equals(m.school)) continue;
                if (mosaicIds != null && !mosaicIds.isEmpty() && !mosaicIds.contains(m.id)) continue;
                out.add(EngineJson.parse(m.definition));
            }
            return out.toString();
        });
    }

    @McpTool(name = "calculate_spell", description = "Calculate a spell made of one or more mosaics for the character: total MP, casting time, duration, "
            + "E per mosaic, remaining MP and any errors or warnings. Always use this (or find_best_spell) for numbers; never compute MP yourself.")
    public String calculateSpell(
            @McpToolParam(description = "Player id (set by the chat service)", required = true) Long playerId,
            @McpToolParam(description = "Character id (set by the chat service)", required = true) Long characterId,
            @McpToolParam(description = "The mosaics of the spell", required = true) List<SelectionArg> selections) {
        return guard(() -> {
            Player p = players.byId(playerId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("characterId", characterId);
            body.put("selections", selections == null ? List.of() : selections.stream().map(MpcalcTools::selectionBody).toList());
            return spells.calculate(p, EngineJson.write(body));
        });
    }

    @McpTool(name = "find_best_spell", description = "Search the best spell for the character within an MP budget. Objective MAX_E = strongest (highest E), "
            + "MAX_DURATION = longest effect, MIN_CASTING_TIME = fastest. Optional limits: minE, minDurationSeconds, maxCastingSeconds. "
            + "Pass the candidate mosaic ids (find them with list_available_mosaics; if the player's wish matches several mosaics, ask which one or pass all of them). "
            + "The search tries every power step and, if allowed, one time extension mosaic (Varazslat nyujtas-...) with extra MP. "
            + "Returns the best option and the trade-off options (strongest versus longest) within the budget, each with selections ready for calculate_spell. "
            + "If the request is unclear (which mosaic, which objective, how long), ask the player instead of guessing. "
            + "It cannot compute how long an effect can be delayed (the Idozites mosaic has no defined delay length).")
    public String findBestSpell(
            @McpToolParam(description = "Player id (set by the chat service)", required = true) Long playerId,
            @McpToolParam(description = "Character id (set by the chat service)", required = true) Long characterId,
            @McpToolParam(description = "Candidate mosaic ids", required = true) List<String> candidateMosaicIds,
            @McpToolParam(description = "MAX_E (strongest), MAX_DURATION (longest) or MIN_CASTING_TIME (fastest)", required = true) SpellSearch.Objective objective,
            @McpToolParam(description = "Minimum E the spell must reach", required = false) BigDecimal minE,
            @McpToolParam(description = "Minimum effect duration in seconds (one hour = 3600)", required = false) Long minDurationSeconds,
            @McpToolParam(description = "Maximum casting time in seconds", required = false) Long maxCastingSeconds,
            @McpToolParam(description = "MP budget; default: the character's current MP", required = false) Integer mpBudget,
            @McpToolParam(description = "Values of the candidates' inputs by input id, if they have any", required = false) Map<String, Object> inputs,
            @McpToolParam(description = "Allow one time extension mosaic (default true)", required = false) Boolean allowExtension,
            @McpToolParam(description = "How many trade-off options to return (default 5)", required = false) Integer limit) {
        return guard(() -> {
            Player p = players.byId(playerId);
            Map<String, JsonNode> in = new LinkedHashMap<>();
            if (inputs != null) inputs.forEach((k, v) -> in.put(k, EngineJson.mapper().valueToTree(v)));
            var req = new SpellSearch.Request(candidateMosaicIds, objective, minE, minDurationSeconds, maxCastingSeconds, mpBudget, in,
                    allowExtension == null || allowExtension, limit == null ? 5 : limit);
            return EngineJson.write(search.search(p, characterId, req), messages::text);
        });
    }

    private static Map<String, Object> selectionBody(SelectionArg s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mosaicId", s.mosaicId());
        m.put("inputs", s.inputs() == null ? Map.of() : s.inputs());
        m.put("k", s.k() == null ? 0 : s.k());
        m.put("options", s.options() == null ? List.of() : s.options());
        m.put("extraMp", s.extraMp() == null ? 0 : s.extraMp());
        return m;
    }

    /** A refused request becomes a tool result the model can read, not a protocol error. */
    private String guard(Supplier<String> call) {
        try {
            return call.get();
        } catch (ApiException e) {
            return errorJson(e.key, messages.text(e.key, e.args));
        } catch (MsgException e) {
            return errorJson(e.msg.code(), messages.text(e.msg));
        }
    }

    private static String errorJson(String code, String text) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", text);
        body.put("code", code);
        return EngineJson.write(body);
    }
}
