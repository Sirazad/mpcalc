-- V13: descriptions of the forms (user's Hungarian text "Hat iskola forma", chunk 4, approved 2026-10-17), stored as text.
-- Only Nyíl, Kitörés, Szőnyeg and Kard have a text in the user's source; the others stay empty (NULL).
-- Each value is written only while the column is still empty, so an admin edit is never overwritten.
ALTER TABLE forms ADD COLUMN description text;

UPDATE forms SET description = 'Támadó varázslat. A varázsló ujjából egyetlenegy, a teremtett elemből való nyíl-lövedék tör elő (Zóna Varázsjel esetén a varázsjelből, Távolbahatásnál vagy szimpátiánál a semmiből). Ha eltalálja a megcélzott áldozatot, az elem Sp-jével egyenlő sebet okoz neki. (Természetesen csak akkor, ha az adott elem sebzi az illetőt.) Annak eldöntésére, hogy a varázsló eltalálja-e a kiszemelt célt, a harci rendszer célzás szabálya az irányadó.

A Nyíl CÉ-je 30, félre nem üthető, de sebzése ellen érvényesül a páncélok SFÉ-je. E-je és sebzése egyenlő a teremtett elem E-jével és Sp-jével.' WHERE id = 'nyil' AND description IS NULL;
UPDATE forms SET description = 'Támadó varázslat. A teremtett elemet a varázsló annyira összesűríti, hogy az végül szétrobban. A varázsló a zónáján belül bárhol meghatározhatja azt a pontot, de a bal kezéből elinduló varázslatnak egyenes vonalban el kell érnie addig a pontig. Amennyiben az elemnek megfelelő akadályba ütközne, akkor korábban robban fel. Tanácsos azonban figyelni arra, hogy az elég távol essen tőle, és a robbanás ne sebezze meg őt is!

A kitörés által érintett terület nagyságát a varázsló nem képes befolyásolni, az egyedül a szétrobbanó elem mennyiségétől, azaz E-jétől függ. A kitörés epicentruma körüli egy negyedméter sugarú körben az E egyenlő az elem E-jével, attól távolodva pedig negyedméterenként 1-gyel csökken, míg végül nullává enyészik. Ezzel meg is határoztuk az érintett terület nagyságát: E/4 méter sugarú kör, ahol az E a Kitöréssé formázott elem E-je.

A sebzés is ehhez hasonlóan alakul: középen maximális, majd az epicentrumtól távolodva az erősséggel együtt csökken.' WHERE id = 'kitores' AND description IS NULL;
UPDATE forms SET description = 'Védő és támadó varázslat. A varázsló a megteremtett elemet az előbbiekhez képest jóval nagyobb területen osztja el. Ezt csak egyenletesen teheti, és a terület formáját sem határozhatja meg, ez minden esetben egy szabályos kör. Ezt a kört nevezzük szőnyegnek.

A szőnyeg a föld szintjét 50 cm-es magasságban borítja be, és követi a terep egyenetlenségeit. A kör mérete a varázslótól függ, ám nem lehet kisebb egy negyed méternél, és nem lehet nagyobb, mint 0,25 × E méter. Középpontja a varázsló zónáján belül bárhova eshet.

A szőnyeg E-je a teremtett elem E-jének és a szőnyeg méretének függvénye. Átlagérték, amit úgy kaphatunk meg, hogy az elem E-jét elosztjuk a szőnyeg méterben számított sugarával (24 E elem – 6 méter/4 E vagy 3 méter/8 E, vagy 2 méter/12 E). Tört érték esetén lefelé kell kerekíteni.

A szőnyeg félkörönkénti sebzése a szőnyeg E-jével arányos, és nem a teremtett elemével.

A szőnyegbe lépő lények méretüktől függően vesztenek Sp-t félkörönként.

3 körig marad együtt, azután eloszlik.' WHERE id = 'szonyeg' AND description IS NULL;
UPDATE forms SET description = 'Támadó varázslat. A varázsló a megteremtett elemmel körbeveheti egy tetszés szerinti formájú, egykezes kard pengéjét. Az elem felveszi a fegyver formáját – legyen az tőr vagy hosszúkard –, akár az aura az ember alakját, akit övez. A fegyver a markolatánál bárki megfoghatja, ám a penge többszörösen sebez. Természetesen csak akkor, ha a Kardba formázott elem ártalmas a megtámadottra (pl. a tűzkard éget).

A kard E-je egyenlő a teremtett elem E-jével. Éppúgy viselkedik, akár egy közönséges fegyver: éppúgy harcolni, támadni szükséges vele – a harci rendszer szabályai szerint. Bárkinek átadható, forgatásához nem kell varázsló kasztúnak lenni. Sebzés csak sikeres támadás esetén éri az áldozatot. Ám ez nem csak annyi Sp, amennyi a fegyvereknél feltüntetett táblázatban szerepel, hanem még plusz annyi, amennyit a teremtett elem okoz. A túlütésre vonatkozó szabályok itt is igazak, és a páncélok SFÉ-je is érvényesül.

A kard TÉ-je és VÉ-je ugyanannyi, mint egy közönséges fegyveré, és természetesen mozgatható. 5 körig van jelen, addig használható, aztán nyomtalanul eltűnik a penge körül.' WHERE id = 'kard' AND description IS NULL;
