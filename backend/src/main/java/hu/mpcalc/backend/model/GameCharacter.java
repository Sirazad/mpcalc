package hu.mpcalc.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "characters")
public class GameCharacter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "player_id") public Long playerId;
    public String name;
    @Column(name = "max_mp") public int maxMp;
    @Column(name = "current_mp") public int currentMp;
    @Column(name = "created_at", insertable = false, updatable = false) public Instant createdAt;
    @Column(name = "updated_at", insertable = false) public Instant updatedAt;
}
