-- V25: notes for the chat (user, 2026-10-19): Hungarian texts that are not used in any calculation, only given to the chat when a question
--   uses their words. The rows are written by the repeatable migration R__notes.sql from database/notes/*.md.
-- keywords is one text (the keywords joined with ", ") because to_tsvector over an array is not allowed in a generated column.
-- The search column weights the keywords (A), then the title (B), then the text (C), stemmed with the Hungarian configuration.
CREATE TABLE notes (
    id        text PRIMARY KEY,
    title     text NOT NULL,
    category  text NOT NULL,
    school    text,
    keywords  text NOT NULL DEFAULT '',
    text      text NOT NULL,
    source    text NOT NULL,
    search    tsvector GENERATED ALWAYS AS (
                  setweight(to_tsvector('hungarian', keywords), 'A') ||
                  setweight(to_tsvector('hungarian', title), 'B') ||
                  setweight(to_tsvector('hungarian', text), 'C')) STORED
);
CREATE INDEX notes_search_idx ON notes USING gin (search);
