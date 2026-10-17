package hu.mpcalc.engine.model;

import java.util.List;

/** inTrance: the caster is already in a trance, so drawings do not add the 10 minutes of the trance. */
public record SpellRequest(List<Selection> selections, CharacterSheet character, List<Form> forms, boolean inTrance) {
    public SpellRequest(List<Selection> selections, CharacterSheet character, List<Form> forms) { this(selections, character, forms, false); }
}
