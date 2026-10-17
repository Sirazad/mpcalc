package hu.mpcalc.engine.model;

import java.util.List;

/** inTrance: the caster is already in a trance, so drawings do not add the 10 minutes of the trance. */
/** viaZoneSign: the spell is cast through a Zóna varázsjel: +5 MP once and +1 kör of casting time. */
public record SpellRequest(List<Selection> selections, CharacterSheet character, List<Form> forms, boolean inTrance, boolean viaZoneSign) {
    public SpellRequest(List<Selection> selections, CharacterSheet character, List<Form> forms) { this(selections, character, forms, false, false); }
    public SpellRequest(List<Selection> selections, CharacterSheet character, List<Form> forms, boolean inTrance) { this(selections, character, forms, inTrance, false); }
}
