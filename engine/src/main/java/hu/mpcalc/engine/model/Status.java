package hu.mpcalc.engine.model;

import java.util.List;

/** state = complete | partial | incomplete; missing = names of undefined fields. */
public class Status {
    public String state;
    public List<String> missing = List.of();
}
