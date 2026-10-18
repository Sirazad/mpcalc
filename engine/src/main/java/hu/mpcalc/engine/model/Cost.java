package hu.mpcalc.engine.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

/** Cost data; which fields are used depends on pattern (flat, perMass, compositionPerMass, compositionGrid, lookup, perChoice, wrapper, undefined). */
public class Cost {
    public String pattern;
    public BigDecimal baseMp;
    public Rate rate;
    /** compositionPerMass: group -> Rate or null. compositionGrid: kept raw. */
    public JsonNode rates;
    public JsonNode rounding;
    public List<String> rows;
    public List<String> columns;
    public String input;
    public List<Row> table;
    /** perChoice: the MP of every value of the choice input `input`. */
    public JsonNode mpByValue;
    /** wrapper: the MP is baseMp plus factor times the MP of the other mosaics of the spell. */
    public BigDecimal factor;
    /** wrapper: the base MP per value of the choice input `input` (instead of one baseMp). */
    public JsonNode baseMpByValue;
    /** wrapper: the mosaics it accepts, a list of {school, subgroup (optional), label}; none listed means any mosaic. */
    public JsonNode wraps;
    /** wrapper: the wrapped mosaics keep their own durations (a symbol lasts as long as the spell drawn in it). */
    public Boolean keepsDuration;
    /** wrapper: the wrapped mosaics are priced at this strength E (a bélyeg costs what the spell costs at 15 or 20 E). */
    public BigDecimal innerE;
    /** linear: MP = baseMp + mpPerUnit for every unit of the number input `input` above freeUnits. */
    public BigDecimal freeUnits;
    public BigDecimal mpPerUnit;
    /** flat and perMass: a whole-number input the cost is multiplied by (the speed of a Forgás varázsjel, the size of a Térkapu). */
    public String multiplierInput;
    public static class Row { public BigDecimal fromM; public BigDecimal toM; public BigDecimal mp; }
}
