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
    public String source;
}
