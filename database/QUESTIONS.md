# Open questions (phase 1: database and data)

Each entry: what the source says, what the seed/SQL does now (conservative), the question.

1. **Életerő irányítás and Életerő átalakítás (Nekromancia, Part 9)**: set aside as instructed; not in the seed. Casting time is "2 sz per 1 MP spent", power is +3 Fp per 1 MP; no format for it. Include later, and how?
2. **Fal duration**: table says 5 kör, description says 6 kör (Part 10a, backlog). Seed uses 5 kör (table); the user confirmed 5 kör "for now" on 2026-10-17. Open: is 6 kör from the description ever right?
3. **Tier of empty rows**: Térzavarás, Időzavarás and Asztrál lény idézése have no tier in the source. The format says tier is null only for Alapvarázslatok. Seed: tier `null`, status incomplete, `"tier"` in `status.missing`; the DB check allows `tier IS NULL` only for alapvarazslatok or for an incomplete mosaic. Alternative: leave the 3 rows out (98 mosaics, differs from Part 13). Which?
4. **Tier range**: format says "2 to 4 seen so far", Part 4 says 5 tiers per school. DB and validator accept 1 to 5. Restrict to 2 to 4?
5. **Légiesség cost pattern**: the task text says incomplete mosaics get pattern `undefined`, but the approved format example uses `compositionGrid` with `rates: null`. Seed follows the format example (status incomplete). Switch to `undefined`?
6. **Mágikussá tétel**: "+E/MP 1/5 kg" is ambiguous (backlog). Seed: base E 1, step `null` (fixed power), status partial, missing `power`. The engine must not read `step: null` here as "power capped"; the status warns.
7. **Teleport**: casting time blank (partial). Input is `massKg` (my choice of id/unit); the format has no `rounding` field for `perMass`. How does 1 MP per 3 kg round?
8. **Időzítés**: "max. 1 év" has no place in the format. Seed: modifier with +15 MP, +1 perc casting, `addsDuration` undefined, duration undefined, status partial (missing `duration`), the text in `effect`. Is it a modifier at all?
9. **Asztrál Kiterjesztés, Irányítás (Nekromancia)**: casting "+2 sz" stored as the mosaic's own casting time (2 sz), no `modifier` (the user has not said they apply to the whole spell). Lenyomat casting 2 k stored as such. Cost Spec., so all incomplete.
10. **Tükrözés (Asztrál)**: shifted source row. Seed keeps MP 2, E 1, +E/MP 1/2; casting time, duration, range, resistance undefined (partial).
11. **Élőholt taszítás**: cost "2 + spec.", range "Érintés + spec.". Seed: cost pattern undefined (the known 2 MP is not stored), range undefined, resistance none.
12. **Időfagyás**: cost "150 + Spec.": cost undefined, the known 150 not stored.
13. **Elementál idézés**: duration "Idézés" and range "**" have no format value: stored undefined (incomplete anyway). The user does not know what they mean either (2026-10-16); open until they find out. MP is "spec." in the source table.
14. **Abszolút pont**: flat 20 MP, modifier adds 2 sz casting time; E, duration, range are `*` (undefined); `addsDuration` undefined. Matches Part 13 (regular).
15. **Resistance of blank rows**: Időugrás, Időkapu, Árnyalás take the school-wide ME statement of Parts 7/8 (resisted by the school); blank Nekromancia summonings take their "-" (none); Képzettség irányítása and the three fully empty rows: undefined.
16. **Paraelem idézése inputs**: the plan does not say what the player chooses. Seed: a choice `ho, fagy, feny, setet` (the four para-elements of Part 10a) plus a form. Does it also need the primal element?
17. **Mental Fájdalom** uses unit E (source writes E); Fp only for the set-aside Életerő mosaics.
18. **`effect` text**: language undecided. Seed has English text from the plan only for mosaics where the plan describes the effect; the rest have no `effect`.
19. **Material groups**: examples are the plan's English words. "Magical" materials (mithril, abbitacél, gems) are not a group and are not stored.
20. **Forms have no school**: tier is checked against the character's tier in the school of the mosaic using the form (Part 12). Forms may cost MP: unknown, not stored.
21. **Current MP**: DB requires 0 <= current MP <= max MP. Part 11 says the page shows "goes negative", but a spell above current MP is not castable. Relax?
22. **Szimpatikus mágia**: no data; the school row exists in `schools`, no seed file.
23. **Language of names/labels**: names and input labels are Hungarian; ids ASCII.
24. **Paraelem idézése MP**: the user's table (2026-10-16) says MP 1, +E/MP 1/1. Resolved 2026-10-17: the user said Paraelem is 1; the data now stores MP 1, +E/MP 1/1 (V10).
25. **Elemi erő idézése ME**: stored as SFÉ (`armorSfe`) again on 2026-10-16 (V7), following the user's table; the text of the same source also says Elemi erő is weakened by magical weapon strikes. Earlier (V6) the user's rule was Elemi erő; the later answer (SFÉ) is the current one.
26. **Gömb**: the stored forms include Gömb (fok 4, 5 kör). It comes from the form table of the plan (Part 10a, written in the first session), not from the user's pasted form table of 2026-10-16 (11 forms, ending with Labda). Where it was taken from is unknown. Real form, or remove? No answer yet; its regenerációs idő is empty.
27. **Regenerációs idő of the forms**: stored as text (V9), from the user's form table; nothing calculates with it, the meaning is not defined yet (the user asked only to store it). "-" in the table is empty. The text uses the decimal comma (0,5 szegmens); the table wrote 0.5.
