package hu.mpcalc.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;

@Entity
@Table(name = "mosaics")
public class MosaicEntity {
    @Id public String id;
    public String name;
    public String school;
    public String subgroup;
    public Short tier;
    @Column(name = "status_state") public String statusState;
    @Column(name = "cost_pattern") public String costPattern;
    public String source;
    /** The whole mosaic JSON (docs/mosaic-definition-format.md) as text. */
    @ColumnTransformer(read = "definition::text", write = "?::jsonb")
    @Column(columnDefinition = "jsonb")
    public String definition;
    @Column(name = "created_at", insertable = false, updatable = false) public Instant createdAt;
    @Column(name = "updated_at", insertable = false) public Instant updatedAt;
}
