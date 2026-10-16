-- V3: audit log. One row per write, written by the DAO in the same transaction as the write.
CREATE TABLE audit_log (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at  timestamptz NOT NULL DEFAULT now(),   -- when
    player_id    bigint,        -- who; no foreign key, so the row outlives the player
    player_name  text,          -- who, as written at the time
    entity_type  text   NOT NULL,   -- e.g. 'mosaic', 'character', 'saved_spell'
    entity_id    text   NOT NULL,
    action       text   NOT NULL,
    request      jsonb,         -- the request that caused the write
    data_before  jsonb,         -- NULL for a create
    data_after   jsonb,         -- NULL for a delete
    CONSTRAINT audit_log_action_chk CHECK (action IN ('create', 'update', 'delete'))
);
CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id);
CREATE INDEX audit_log_occurred_idx ON audit_log (occurred_at);
CREATE INDEX audit_log_player_idx ON audit_log (player_id);

-- The log is append-only.
CREATE FUNCTION audit_log_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$;
CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();
