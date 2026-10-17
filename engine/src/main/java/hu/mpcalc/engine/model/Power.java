package hu.mpcalc.engine.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** E = baseE + k*step.e. step == null means power cannot be raised. infinite = true: the strength is infinite (it cannot be overpowered). */
public class Power {
    public String unit;
    public BigDecimal baseE;
    @JsonInclude(JsonInclude.Include.NON_NULL) public Boolean infinite;
    public Step step;
    public static class Step { public BigDecimal mp; public BigDecimal e; }
}
