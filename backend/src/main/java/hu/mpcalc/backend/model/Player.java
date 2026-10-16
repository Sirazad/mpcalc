package hu.mpcalc.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "players")
public class Player {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String name;
    @Column(name = "google_subject") public String googleSubject;
    @Column(name = "is_admin") public boolean admin;
    @Column(name = "created_at", insertable = false, updatable = false) public Instant createdAt;
}
