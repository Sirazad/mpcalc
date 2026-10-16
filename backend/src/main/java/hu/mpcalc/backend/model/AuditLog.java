package hu.mpcalc.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;

/** Append-only (a database trigger refuses UPDATE and DELETE). */
@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "occurred_at", insertable = false, updatable = false) public Instant occurredAt;
    @Column(name = "player_id") public Long playerId;
    @Column(name = "player_name") public String playerName;
    @Column(name = "entity_type") public String entityType;
    @Column(name = "entity_id") public String entityId;
    public String action;
    @ColumnTransformer(read = "request::text", write = "?::jsonb") @Column(columnDefinition = "jsonb") public String request;
    @ColumnTransformer(read = "data_before::text", write = "?::jsonb") @Column(name = "data_before", columnDefinition = "jsonb") public String dataBefore;
    @ColumnTransformer(read = "data_after::text", write = "?::jsonb") @Column(name = "data_after", columnDefinition = "jsonb") public String dataAfter;
}
