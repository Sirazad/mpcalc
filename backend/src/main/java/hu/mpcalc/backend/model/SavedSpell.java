package hu.mpcalc.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;

@Entity
@Table(name = "saved_spells")
public class SavedSpell {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "character_id") public Long characterId;
    public String name;
    @ColumnTransformer(read = "content::text", write = "?::jsonb")
    @Column(columnDefinition = "jsonb")
    public String content;
    @Column(name = "created_at", insertable = false, updatable = false) public Instant createdAt;
    @Column(name = "updated_at", insertable = false) public Instant updatedAt;
}
