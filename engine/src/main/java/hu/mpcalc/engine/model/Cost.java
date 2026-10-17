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
    public static class Row { public BigDecimal fromM; public BigDecimal toM; public BigDecimal mp; }
}
