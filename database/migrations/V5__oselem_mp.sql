-- V5: Őselem idézése costs the same MP as Elemi erő idézése (user decision 2026-10-16): base 1 MP, +1 E per 1 MP (was 4 and 4).
-- Applies only while the stored values are still the old ones, so an admin edit of this mosaic is never overwritten.
UPDATE mosaics
SET definition = jsonb_set(jsonb_set(definition, '{cost,baseMp}', '1'::jsonb), '{power,step,mp}', '1'::jsonb),
    updated_at = now()
WHERE id = 'anyagi-elemi-oselem-idezese'
  AND definition #>> '{cost,baseMp}' = '4'
  AND definition #>> '{power,step,mp}' = '4';
