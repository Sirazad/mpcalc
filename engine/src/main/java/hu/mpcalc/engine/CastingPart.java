package hu.mpcalc.engine;

import hu.mpcalc.engine.model.TimeValue;

/**
 * One part of the casting time of a mosaic, so the page can show how the time is put together.
 * OWN = the mosaic's own casting time; OPTION = the extra time of a ticked option (label = the option's label);
 * MODIFIER = the time a modifier adds to the spell. The parts add up to the casting time of the mosaic.
 */
public record CastingPart(Kind kind, String label, Maybe<TimeValue> time) {
    public enum Kind { OWN, OPTION, MODIFIER }
}
