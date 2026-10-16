package hu.mpcalc.engine.model;

/** A form: its own tier (checked against the character's tier in the school of the mosaic that uses it) and its duration. */
public class Form {
    public String id;
    public String name;
    public Integer tier;
    public TimeValue duration;
}
