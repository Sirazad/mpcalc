-- V14: Kard and Aura can be moved, every other form is stationary (user, 2026-10-17); the Szőnyeg description gets the sentence about
-- opposing elements ("kel birokra", the user's reading of the unclear source text).
-- The description is replaced only while the old text (V13) is still stored, so an admin edit is never overwritten.
ALTER TABLE forms ADD COLUMN movable boolean NOT NULL DEFAULT false;
UPDATE forms SET movable = true WHERE id IN ('kard', 'aura');

UPDATE forms SET description = 'Védő és támadó varázslat. A varázsló a megteremtett elemet az előbbiekhez képest jóval nagyobb területen osztja el. Ezt csak egyenletesen teheti, és a terület formáját sem határozhatja meg, ez minden esetben egy szabályos kör. Ezt a kört nevezzük szőnyegnek.

A szőnyeg a föld szintjét 50 cm-es magasságban borítja be, és követi a terep egyenetlenségeit. A kör mérete a varázslótól függ, ám nem lehet kisebb egy negyed méternél, és nem lehet nagyobb, mint 0,25 × E méter. Középpontja a varázsló zónáján belül bárhova eshet.

A szőnyeg E-je a teremtett elem E-jének és a szőnyeg méretének függvénye. Átlagérték, amit úgy kaphatunk meg, hogy az elem E-jét elosztjuk a szőnyeg méterben számított sugarával (24 E elem – 6 méter/4 E vagy 3 méter/8 E, vagy 2 méter/12 E). Tört érték esetén lefelé kell kerekíteni.

A szőnyeg félkörönkénti sebzése a szőnyeg E-jével arányos, és nem a teremtett elemével.

A szőnyegbe lépő lények méretüktől függően vesztenek Sp-t félkörönként.

Ha a szőnyegbe ellentétes elem alkotta nyíl, kard, fal vagy aura kerül, azokkal nem az átlag E-je kel birokra, hanem a bele formázott elem összes E-je.

3 körig marad együtt, azután eloszlik.' WHERE id = 'szonyeg' AND description = 'Védő és támadó varázslat. A varázsló a megteremtett elemet az előbbiekhez képest jóval nagyobb területen osztja el. Ezt csak egyenletesen teheti, és a terület formáját sem határozhatja meg, ez minden esetben egy szabályos kör. Ezt a kört nevezzük szőnyegnek.

A szőnyeg a föld szintjét 50 cm-es magasságban borítja be, és követi a terep egyenetlenségeit. A kör mérete a varázslótól függ, ám nem lehet kisebb egy negyed méternél, és nem lehet nagyobb, mint 0,25 × E méter. Középpontja a varázsló zónáján belül bárhova eshet.

A szőnyeg E-je a teremtett elem E-jének és a szőnyeg méretének függvénye. Átlagérték, amit úgy kaphatunk meg, hogy az elem E-jét elosztjuk a szőnyeg méterben számított sugarával (24 E elem – 6 méter/4 E vagy 3 méter/8 E, vagy 2 méter/12 E). Tört érték esetén lefelé kell kerekíteni.

A szőnyeg félkörönkénti sebzése a szőnyeg E-jével arányos, és nem a teremtett elemével.

A szőnyegbe lépő lények méretüktől függően vesztenek Sp-t félkörönként.

3 körig marad együtt, azután eloszlik.';
