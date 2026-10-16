-- V15: chunk 5 of the Anyagi input (user's Hungarian text, approved 2026-10-17).
--   1. The examples of the material groups I, II, III in Hungarian.
--   2. A table for texts that belong to no mosaic, form or group, with the line "Mágikus anyagok: Mithrill, abbitacél, drágakövek.".
-- The group examples are replaced only while the old (English) examples are still stored, so an admin edit is never overwritten.
CREATE TABLE reference_texts (
    id      text PRIMARY KEY,
    text    text NOT NULL,
    source  text NOT NULL
);

INSERT INTO reference_texts (id, text, source) VALUES ('materials.magical', 'Mágikus anyagok: Mithrill, abbitacél, drágakövek.', 'Part 10b');

UPDATE material_groups SET examples = ARRAY['Minden más nem mágikus anyag.']::text[] WHERE id = 'I' AND examples = ARRAY['everything else non-magical']::text[];
UPDATE material_groups SET examples = ARRAY['Nemtelen fémek, mint réz, ón, vas stb…', 'Ércek, amikben 10%-nál nagyobb a tartalom.']::text[] WHERE id = 'II' AND examples = ARRAY['base metals (copper, tin, iron...)', 'ores with over 10% content']::text[];
UPDATE material_groups SET examples = ARRAY['Aryan, ezüst, platina és minden olyan ötvözet, amiben legalább 10%-ban megtalálhatóak.', 'Savak, lúgok, mérgek.']::text[] WHERE id = 'III' AND examples = ARRAY['aryan', 'silver', 'platinum', 'alloys with at least 10% of them', 'acids', 'bases', 'poisons']::text[];
