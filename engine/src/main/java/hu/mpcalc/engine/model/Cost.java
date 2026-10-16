package hu.mpcalc.engine.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

/** Cost data; which fields are used depends on pattern (flat, perMass, compositionPerMass, compositionGrid, lookup, undefined). */
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
    public static class Row { public BigDecimal fromM; public BigDecimal toM; public BigDecimal mp; }
}
