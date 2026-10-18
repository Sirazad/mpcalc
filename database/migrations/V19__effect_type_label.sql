-- V19: the choice that names the effect type of the circles and of the személyes aura is labelled "Hatás típusa", so it no longer clashes
-- with the "Hatás" (effect text) line of the spell panel (found in the browser walk-through, 2026-10-17).
-- Applies only while the old label "Hatás" is still stored.
UPDATE mosaics SET definition = jsonb_set(definition, '{inputs,0,label}', '"Hatás típusa"'::jsonb), updated_at = now()
WHERE id IN ('jel-varazskor-korbezaro', 'jel-varazskor-vedo', 'jel-varazsjel-szemelyes-aura') AND definition #>> '{inputs,0,label}' = 'Hatás';
