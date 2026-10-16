-- V2: players, characters, tiers per school, saved spells.

CREATE TABLE players (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            text    NOT NULL UNIQUE,      -- interim "pick your name" login
    google_subject  text    UNIQUE,               -- filled when Google login is added
    is_admin        boolean NOT NULL DEFAULT false,  -- admin role: may edit mosaic data
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE characters (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_id   bigint  NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    name        text    NOT NULL,
    max_mp      integer NOT NULL,
    current_mp  integer NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT characters_max_mp_chk CHECK (max_mp >= 0),
    CONSTRAINT characters_current_mp_chk CHECK (current_mp >= 0 AND current_mp <= max_mp),
    CONSTRAINT characters_player_name_uq UNIQUE (player_id, name)
);
CREATE INDEX characters_player_idx ON characters (player_id);

-- Tier of a character in a school. No row for a school means tier 0 there. Alapvarazslatok (always tier 0) has no row.
CREATE TABLE character_school_tiers (
    character_id  bigint   NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    school        text     NOT NULL REFERENCES schools (id),
    tier          smallint NOT NULL,
    PRIMARY KEY (character_id, school),
    CONSTRAINT character_school_tiers_tier_chk CHECK (tier BETWEEN 0 AND 5),
    CONSTRAINT character_school_tiers_school_chk CHECK (school <> 'alapvarazslatok')
);

CREATE TABLE saved_spells (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id  bigint  NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    name          text    NOT NULL,
    content       jsonb   NOT NULL,   -- mosaics with their inputs, chosen form, power
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT saved_spells_content_chk CHECK (jsonb_typeof(content) = 'object'),
    CONSTRAINT saved_spells_character_name_uq UNIQUE (character_id, name)
);
CREATE INDEX saved_spells_character_idx ON saved_spells (character_id);
