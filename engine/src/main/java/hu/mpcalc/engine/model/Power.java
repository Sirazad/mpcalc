package hu.mpcalc.engine.model;

import java.math.BigDecimal;

/** E = baseE + k*step.e. step == null means power cannot be raised. */
public class Power {
    public String unit;
    public BigDecimal baseE;
    public Step step;
    public static class Step { public BigDecimal mp; public BigDecimal e; }
}
