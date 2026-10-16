package hu.mpcalc.backend.model;

import jakarta.persistence.*;

/** A Hungarian text that belongs to no mosaic, form or group (for example the line about the magical materials). */
@Entity
@Table(name = "reference_texts")
public class ReferenceText {
    @Id public String id;
    public String text;
    public String source;
}
