package hu.mpcalc.engine;

import hu.mpcalc.engine.model.TimeValue;

import java.util.List;

/**
 * valid = no mosaic refused. remainingMp = current MP - total MP (may be negative), undefined if the total is undefined.
 * enoughMp = remainingMp defined and >= 0.
 * viaZoneSign: the spell was cast through a Zóna varázsjel (+5 MP and +1 kör are in the totals).
 * trance: needed = the spell has a drawing (casting time label drawing), which needs a trance of 10 minutes; applied = that time is in
 * castingTime, because the caster is not in a trance yet.
 */
public record SpellResult(boolean valid, List<MosaicResult> mosaics, Maybe<Fraction> totalMp, Maybe<TimeValue> castingTime,
                          Maybe<TimeValue> duration, Maybe<Fraction> remainingMp, boolean enoughMp,
                          List<Msg> warnings, List<Msg> errors, Trance trance, boolean viaZoneSign) {
    public record Trance(boolean needed, boolean applied) {}
}
