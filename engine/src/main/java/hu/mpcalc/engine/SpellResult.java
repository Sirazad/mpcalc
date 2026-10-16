package hu.mpcalc.engine;

import hu.mpcalc.engine.model.TimeValue;

import java.util.List;

/**
 * valid = no mosaic refused. remainingMp = current MP - total MP (may be negative), undefined if the total is undefined.
 * enoughMp = remainingMp defined and >= 0.
 */
public record SpellResult(boolean valid, List<MosaicResult> mosaics, Maybe<Fraction> totalMp, Maybe<TimeValue> castingTime,
                          Maybe<TimeValue> duration, Maybe<Fraction> remainingMp, boolean enoughMp,
                          List<Msg> warnings, List<Msg> errors) {}
