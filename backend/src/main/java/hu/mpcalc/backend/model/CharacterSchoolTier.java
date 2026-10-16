package hu.mpcalc.backend.model;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "character_school_tiers")
@IdClass(CharacterSchoolTier.Key.class)
public class CharacterSchoolTier {
    @Id @Column(name = "character_id") public Long characterId;
    @Id public String school;
    public Short tier;

    public record Key(Long characterId, String school) implements Serializable {}

    public CharacterSchoolTier() {}
    public CharacterSchoolTier(Long characterId, String school, int tier) {
        this.characterId = characterId; this.school = school; this.tier = (short) tier;
    }
}
