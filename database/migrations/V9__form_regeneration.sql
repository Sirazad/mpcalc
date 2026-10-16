-- V9: regenerációs idő of the forms (user's form table, 2026-10-17), stored as text; nothing calculates with it.
-- "-" in the table is NULL. Gömb is not in the user's table (open question), so it stays NULL.
-- Each value is written only while the column is still empty, so an admin edit is never overwritten.
ALTER TABLE forms ADD COLUMN regeneration text;

UPDATE forms SET regeneration = '0,5 szegmens' WHERE id = 'fal' AND regeneration IS NULL;
UPDATE forms SET regeneration = '0,5 szegmens' WHERE id = 'szonyeg' AND regeneration IS NULL;
UPDATE forms SET regeneration = '5 kör' WHERE id = 'kard' AND regeneration IS NULL;
UPDATE forms SET regeneration = '5 kör' WHERE id = 'aura' AND regeneration IS NULL;
UPDATE forms SET regeneration = '0,5 szegmens' WHERE id = 'sator' AND regeneration IS NULL;
UPDATE forms SET regeneration = '0,5 szegmens' WHERE id = 'eso' AND regeneration IS NULL;
UPDATE forms SET regeneration = '0,5 szegmens' WHERE id = 'gyuru' AND regeneration IS NULL;
