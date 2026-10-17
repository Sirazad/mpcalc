package hu.mpcalc.engine.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** One mosaic definition, exactly the approved JSON format. */
public class Mosaic {
    public String id;
    public String name;
    public String school;
    public String subgroup;
    public Integer tier;
    /** Extra requirements besides the tier in the own school: tiers needed in other schools (all of them). */
    public List<Requirement> requires = List.of();
    public Status status;
    public CastingTime castingTime;
    public TimeValue duration;
    public JsonNode range;
    public Resistance resistance;
    public Power power;
    public Cost cost;
    public Modifier modifier;
    public List<Option> options = List.of();
    public List<Input> inputs = List.of();
    public String effect;
    public String source;
}
