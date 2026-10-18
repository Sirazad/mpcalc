package hu.mpcalc.engine.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * A value the engine calculates from the player's inputs and the power steps and the page shows, but that is not a cost
 * (for example the capacity in kg of a Térkapu). value = the sum of the terms. A term is either
 * {input, perUnit}: the value of a number input times perUnit, {steps: true, perStep}: the number of power steps times perStep,
 * or {constant}: a fixed amount.
 */
public class Derived {
    public String id;
    public String label;
    public String unit;
    public List<Term> terms = List.of();

    public static class Term {
        public String input;
        public BigDecimal perUnit;
        public Boolean steps;
        public BigDecimal perStep;
        public BigDecimal constant;
    }
}
