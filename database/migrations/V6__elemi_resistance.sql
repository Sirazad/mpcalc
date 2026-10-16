-- V6: magic resistance (ME) of the Elemi mosaics (user decision 2026-10-16).
--   Őselem idézése: resisted by the OPPOSITE of the chosen element (tűz-víz, levegő-föld).
--   Paraelem idézése: the same para-element that was chosen.
--   Elemi erő idézése: resisted by Elemi erő (was SFÉ).
-- Each update applies only while the stored value is still the old one, so an admin edit is never overwritten.
UPDATE mosaics
SET definition = jsonb_set(definition, '{resistance}',
      '{"kind":"oppositeElement","input":"element","pairs":{"ostuz":"osviz","osviz":"ostuz","osfold":"oslevego","oslevego":"osfold"}}'::jsonb),
    updated_at = now()
WHERE id = 'anyagi-elemi-oselem-idezese' AND definition #> '{resistance}' = '{"kind":"givenElement"}'::jsonb;

UPDATE mosaics
SET definition = jsonb_set(definition, '{resistance}', '{"kind":"givenParaElement","input":"paraElement"}'::jsonb), updated_at = now()
WHERE id = 'anyagi-elemi-paraelem-idezese' AND definition #> '{resistance}' = '{"kind":"givenParaElement"}'::jsonb;

UPDATE mosaics
SET definition = jsonb_set(definition, '{resistance}', '{"kind":"elementalForce"}'::jsonb), updated_at = now()
WHERE id = 'anyagi-elemi-elemi-ero-idezese' AND definition #> '{resistance}' = '{"kind":"armorSfe"}'::jsonb;
