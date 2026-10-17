-- V16: chunk 6 of the Anyagi input (user's Hungarian text, approved 2026-10-17): Hatás texts of the Természetes anyagok mosaics,
-- and the general line "Lehet egyben kimondani varázslatot az érintkező tárgyakra." as a reference text.
-- A Hatás is written only while the mosaic has no effect text, or still has the old English one, so an admin edit is never overwritten.

UPDATE mosaics SET definition = jsonb_set(definition, '{effect}', '"A varázslattal egy tárgy FORMÁJÁT és SZÍNÉT változtathatja meg a varázsló. 1 Mp-ért az anyagcsoporttól függően 1 kg (I. csoport), 0,5 kg (II. csoport), vagy 1g (III. csoport) anyag formázható át. A varázslatban egyfajta súly- és anyagmegmaradási törvény érvényesül: azaz: pontosan a kiindulásival azonos súlyú és minőségű tárgy keletkezik. Ha kevesebb, akkor az új tárgy kialakításához fel nem használt anyag felesleg formájában megmarad. Az alapanyagnál nagyobb súlyú tárgy nem formázható.\n\nTöbb különálló tárgy is szolgáltathat alapanyagot a megformázni kívánt egyetlen holmihoz. Ilyenkor azok összeolvadnak. Ha eleve több különböző minőségű anyagból álló tárgyat kívánunk átformázni, akkor a végtermékben is érvényesül azok egymáshoz viszonyított aránya.\n\nHa a varázslat időtartama lejár, a tárgy visszaalakul eredeti formájára.\n\nPélda: sárga gömb helyett kék négyzet."'::jsonb), updated_at = now()
WHERE id = 'anyagi-term-atformazas' AND definition ->> 'effect' = 'Changes shape and colour; new object of exactly the same weight; reverts when the spell ends.';

UPDATE mosaics SET definition = jsonb_set(definition, '{effect}', '"A varázsló az anyag MINŐSÉGÉT változtathatja meg, de csak az adott anyagcsoporton belül. A tárgy halmazállapota, formája nem változik, színe is csak akkor, ha ezt az anyagminőség változása indokolttá teszi. Az átalakítás során az anyag térfogata mérvadó: pontosan ugyanakkora és ugyanolyan formájú marad, ám a súlya megváltozik. A varázslat 1 kg (I), 1/2 kg (II) vagy 1g (III) anyag átalakítására használható.\n\nSzigorú megkötés, hogy a varázsló csak olyan anyaggá képes bármit is átalakítani, amelynek összetételét ismeri. Pl. méreggé való átalakításhoz bírnia kell a Méregkeverés vagy az alkímia, netán a herbalizmus képzettséggel.\n\nA varázslattal élőlények is átalakíthatók bármilyen I. csoportba tartozó anyaggá (pl. kővé változtatás). Vagy éppen fordítva, sárból is gyúrható élőlény teste, ám az efféle teremtmény nem fogja magában hordozni az élet princípiumát, ezért nem számít élőlénynek csak homonkulusznak.\n\nHa a varázslat időtartama lejár, a tárgy visszanyeri eredeti minőségét.\n\nAz Mp igény kiszámításánál a kiindulási tárgy súlya a mérvadó, nem a keletkezetté.\n\nAnyagcsoporton belül, pl. kővé változtatás."'::jsonb), updated_at = now()
WHERE id = 'anyagi-term-atalakitas' AND definition ->> 'effect' = 'Changes quality within the group; state and shape stay; MP from the starting weight; reverts when the spell ends.';

UPDATE mosaics SET definition = jsonb_set(definition, '{effect}', '"Nem volt – van."'::jsonb), updated_at = now()
WHERE id = 'anyagi-term-teremtes' AND NOT (definition ? 'effect');

UPDATE mosaics SET definition = jsonb_set(definition, '{effect}', '"Volt – nincs."'::jsonb), updated_at = now()
WHERE id = 'anyagi-term-dezintegracio' AND NOT (definition ? 'effect');

UPDATE mosaics SET definition = jsonb_set(definition, '{effect}', '"Példa: macskát békává."'::jsonb), updated_at = now()
WHERE id = 'anyagi-term-atvaltoztatas' AND NOT (definition ? 'effect');

UPDATE mosaics SET definition = jsonb_set(definition, '{effect}', '"Lélek alapján újra alkotja."'::jsonb), updated_at = now()
WHERE id = 'anyagi-term-visszavaltoztatas' AND definition ->> 'effect' = 'Recreates from the soul.';

UPDATE mosaics SET definition = jsonb_set(definition, '{effect}', '"Példa: anyagcsoportok között – követ arannyá."'::jsonb), updated_at = now()
WHERE id = 'anyagi-term-transzmutacio' AND NOT (definition ? 'effect');

UPDATE mosaics SET definition = jsonb_set(definition, '{effect}', '"Az anyagtalanságnál a megemelhető súly az anyagtalanságnak megfelelő tömeg (az Mp-ből származó és nem az anyagtalanná tett tömeg) 10%."'::jsonb), updated_at = now()
WHERE id = 'anyagi-term-legiesseg' AND NOT (definition ? 'effect');

UPDATE mosaics SET definition = jsonb_set(definition, '{effect}', '"Mágikus anyagra is hat a láthatatlanság illetve a telekinézis."'::jsonb), updated_at = now()
WHERE id = 'anyagi-term-lathatatlansag' AND NOT (definition ? 'effect');

UPDATE mosaics SET definition = jsonb_set(definition, '{effect}', '"Mágikus anyagra is hat a láthatatlanság illetve a telekinézis."'::jsonb), updated_at = now()
WHERE id = 'anyagi-term-telekinezis' AND NOT (definition ? 'effect');

INSERT INTO reference_texts (id, text, source) VALUES ('materials.touching', 'Lehet egyben kimondani varázslatot az érintkező tárgyakra.', 'Part 10b');
