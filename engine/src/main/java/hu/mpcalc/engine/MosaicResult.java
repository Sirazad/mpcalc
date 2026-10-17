package hu.mpcalc.engine;

import hu.mpcalc.engine.model.Resistance;
import hu.mpcalc.engine.model.TimeValue;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result for one selected mosaic. refused = true: validation failed, every output is undefined.
 * duration = effective duration including the spell's modifiers. For a modifier, castingTime includes what it adds.
 * castingParts = the parts the castingTime is put together from (empty for a refused mosaic).
 * infinitePower = the strength is infinite (e is then undefined). includedInWrapper = the spell has a wrapper mosaic (for example
 * a Végrehajtó varázskör): the casting time and the duration of this mosaic are part of the wrapper's and are not added to the spell's.
 */
public record MosaicResult(String mosaicId, String name, boolean refused, List<Msg> errors, List<Msg> warnings,
                           Maybe<Fraction> mp, Maybe<BigDecimal> e, String powerUnit, Maybe<String> effect,
                           Maybe<Resistance> resistance, Maybe<TimeValue> castingTime, Maybe<TimeValue> duration,
                           List<CastingPart> castingParts, boolean infinitePower, boolean includedInWrapper) {}
