package hu.mpcalc.engine.model;

import java.math.BigDecimal;
import java.util.Map;

/** Tier per school (no entry = no tier in that school) and current MP. */
public class CharacterSheet {
    public final Map<String, Integer> tiers;
    public final BigDecimal currentMp;
    public CharacterSheet(Map<String, Integer> tiers, BigDecimal currentMp) { this.tiers = Map.copyOf(tiers); this.currentMp = currentMp; }
}
