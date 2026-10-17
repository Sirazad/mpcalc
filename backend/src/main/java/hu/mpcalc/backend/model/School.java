package hu.mpcalc.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "schools")
public class School {
    @Id public String id;
    public String name;
    @Column(name = "has_tiers") public boolean hasTiers;
    /** The lore and the main rules of the school (Hungarian, paragraphs separated by line breaks); null: none. */
    public String description;
}
