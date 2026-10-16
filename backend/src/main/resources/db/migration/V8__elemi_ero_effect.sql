-- V8: effect text of Elemi erő idézése (user's Hungarian source text, 2026-10-16).
-- Applies only while the mosaic has no effect text, so an admin edit is never overwritten.
UPDATE mosaics
SET definition = jsonb_set(definition, '{effect}', '"A mágikus energiákat felhasználva mozgatni vagy megállítani lehet tárgyakat."'::jsonb), updated_at = now()
WHERE id = 'anyagi-elemi-elemi-ero-idezese' AND NOT (definition ? 'effect');
