-- V10: Paraelem idézése costs 1 MP and +1 E per 1 MP (user decision 2026-10-17, after the table of the source; it was 2 MP).
-- Applies only while the old values (2 MP) are still stored, so an admin edit is never overwritten.
UPDATE mosaics
SET definition = jsonb_set(jsonb_set(definition, '{cost,baseMp}', '1'::jsonb), '{power,step,mp}', '1'::jsonb), updated_at = now()
WHERE id = 'anyagi-elemi-paraelem-idezese'
  AND definition #>> '{cost,baseMp}' = '2' AND definition #>> '{power,step,mp}' = '2';
