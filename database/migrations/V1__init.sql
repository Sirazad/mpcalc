-- V1: reference data. schools, mosaics, forms, material_groups.
-- Terms: "definition" = the whole mosaic JSON of docs/mosaic-definition-format.md.

CREATE TABLE schools (
    id         text PRIMARY KEY,
    name       text    NOT NULL,
    has_tiers  boolean NOT NULL
);

-- Part 1: 8 schools. Alapvarazslatok has no tiers. Szimpatikus has no mosaic data yet.
INSERT INTO schools (id, name, has_tiers) VALUES
    ('alapvarazslatok', 'Alapvarázslatok',  false),
    ('anyagi',          'Anyagi mágia',     true),
    ('asztral',         'Asztrál mágia',    true),
    ('mental',          'Mentál mágia',     true),
    ('ter',             'Térmágia',         true),
    ('ido',             'Időmágia',         true),
    ('szimpatikus',     'Szimpatikus mágia', true),
    ('nekromancia',     'Nekromancia',      true);

CREATE TABLE mosaics (
    id            text PRIMARY KEY,
    name          text     NOT NULL,
    school        text     NOT NULL REFERENCES schools (id),
    subgroup      text,
    -- minimum tier in the school, 0 to 5. Alapvarazslatok mosaics have tier 0 (user decision).
    -- NULL only (exception, see QUESTIONS.md) for an incomplete mosaic whose source gives no tier.
    tier          smallint,
    status_state  text     NOT NULL,
    cost_pattern  text     NOT NULL,
    source        text     NOT NULL,
    definition    jsonb    NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT mosaics_status_state_chk CHECK (status_state IN ('complete', 'partial', 'incomplete')),
    CONSTRAINT mosaics_subgroup_chk CHECK (
        (school = 'anyagi' AND subgroup IN ('elemi', 'termeszetes'))
        OR (school <> 'anyagi' AND subgroup IS NULL)),
    CONSTRAINT mosaics_tier_range_chk CHECK (tier IS NULL OR tier BETWEEN 0 AND 5),
    CONSTRAINT mosaics_tier_school_chk CHECK (
        (school = 'alapvarazslatok' AND tier = 0)
        OR (school <> 'alapvarazslatok' AND (tier IS NOT NULL OR status_state = 'incomplete'))),
    CONSTRAINT mosaics_cost_pattern_chk CHECK (
        cost_pattern IN ('flat', 'perMass', 'compositionPerMass', 'compositionGrid', 'lookup', 'undefined')),
    -- cost pattern "undefined" means the cost is not known: such a mosaic must be incomplete.
    CONSTRAINT mosaics_undefined_cost_chk CHECK (cost_pattern <> 'undefined' OR status_state = 'incomplete'),
    -- the indexed columns must agree with the definition
    CONSTRAINT mosaics_definition_chk CHECK (
        jsonb_typeof(definition) = 'object'
        AND definition ->> 'id' = id
        AND definition ->> 'name' = name
        AND definition ->> 'school' = school
        AND definition ->> 'subgroup' IS NOT DISTINCT FROM subgroup
        AND coalesce(definition -> 'tier', 'null'::jsonb) = coalesce(to_jsonb(tier), 'null'::jsonb)
        AND definition #>> '{status,state}' = status_state
        AND definition #>> '{cost,pattern}' = cost_pattern
        AND definition ->> 'source' = source)
);

CREATE INDEX mosaics_school_tier_idx ON mosaics (school, tier);
CREATE INDEX mosaics_status_state_idx ON mosaics (status_state);
CREATE INDEX mosaics_cost_pattern_idx ON mosaics (cost_pattern);
CREATE INDEX mosaics_definition_gin_idx ON mosaics USING gin (definition jsonb_path_ops);

-- Forms (Part 10a, 12). Not tied to a school: the tier is checked against the character's
-- tier in the school of the mosaic that uses the form.
CREATE TABLE forms (
    id        text PRIMARY KEY,
    name      text     NOT NULL,
    tier      smallint NOT NULL,
    -- {"kind":"oneOff"} or {"kind":"time","time":{"kind":"time","amounts":{...}}}
    duration  jsonb    NOT NULL,
    source    text     NOT NULL,
    CONSTRAINT forms_tier_chk CHECK (tier BETWEEN 0 AND 5),
    CONSTRAINT forms_duration_chk CHECK (duration ->> 'kind' IN ('oneOff', 'time'))
);

-- Material groups I, II, III (Part 10b).
CREATE TABLE material_groups (
    id        text PRIMARY KEY,
    name      text   NOT NULL,
    examples  text[] NOT NULL,
    source    text   NOT NULL,
    CONSTRAINT material_groups_id_chk CHECK (id IN ('I', 'II', 'III'))
);
