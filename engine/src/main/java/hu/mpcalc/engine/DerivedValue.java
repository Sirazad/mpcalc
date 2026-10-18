package hu.mpcalc.engine;

import java.math.BigDecimal;

/** One calculated value of a mosaic, see model.Derived: label and unit come from the definition, value from the engine. */
public record DerivedValue(String id, String label, String unit, Maybe<BigDecimal> value) {}
