-- V26: links between notes (user, 2026-10-19): the ids of the notes a note refers to, joined with ", ". Declared in one note file
--   (`links:`); tools/build_notes.py adds the reverse link to the other note. The search returns the id and title of each linked note.
ALTER TABLE notes ADD COLUMN links text NOT NULL DEFAULT '';
