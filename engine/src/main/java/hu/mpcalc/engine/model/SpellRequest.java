package hu.mpcalc.engine.model;

import java.util.List;

public record SpellRequest(List<Selection> selections, CharacterSheet character, List<Form> forms) {}
