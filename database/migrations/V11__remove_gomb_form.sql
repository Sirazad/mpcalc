-- V11: remove the form Gömb. It came from the plan's form table, not from the user's form table, which has 11 forms
-- (user decision 2026-10-17: "stick to the thing I copy pasted").
-- Deleted only while no saved spell uses it.
DELETE FROM forms
WHERE id = 'gomb'
  AND NOT EXISTS (SELECT 1 FROM saved_spells WHERE content::text LIKE '%"gomb"%');
