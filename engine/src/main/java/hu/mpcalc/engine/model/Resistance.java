package hu.mpcalc.engine.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * ME type (what can resist the spell): kind (none, school, concealment, oppositeElement, givenParaElement, elementalForce, ...).
 * In a mosaic definition a kind may name the choice input it depends on (input) and, for oppositeElement, the pairs table
 * (chosen value to the resisting value). In a calculation result the engine has resolved that into element: the element that resists.
 */
public class Resistance {
    public String kind;
    public String school;
    @JsonInclude(JsonInclude.Include.NON_NULL) public String input;               // definition only
    @JsonInclude(JsonInclude.Include.NON_NULL) public Map<String, String> pairs;  // definition only
    @JsonInclude(JsonInclude.Include.NON_NULL) public String element;             // result only
    @JsonInclude(JsonInclude.Include.NON_NULL) public String elementLabel;        // result only: the name of element, if the choice input has labels
    @Override public String toString() { return school != null ? kind + ":" + school : element != null ? kind + ":" + element : kind; }
}
