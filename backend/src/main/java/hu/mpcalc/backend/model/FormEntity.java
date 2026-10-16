package hu.mpcalc.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "forms")
public class FormEntity {
    @Id public String id;
    public String name;
    public Short tier;
    @ColumnTransformer(read = "duration::text", write = "?::jsonb")
    @Column(columnDefinition = "jsonb")
    public String duration;
    /** Regenerációs idő of the form table as text (null: none); shown to the player, nothing calculates with it. */
    public String regeneration;
    /** Hungarian description of the form (line breaks separate paragraphs); null: none. */
    public String description;
    public String source;
}
