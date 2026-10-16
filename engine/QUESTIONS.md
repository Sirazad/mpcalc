# Open questions (engine). Conservative option implemented is given for each.

1. Spell duration with several non-modifier mosaics: no combination rule in the sources. Implemented: total duration is defined only if all mosaics have the same effective duration (compared in seconds); otherwise "undefined". Per-mosaic durations are always returned.
2. Duration of oneOff or permanent mosaics plus a modifier's added duration, and a modifier whose addsDuration is permanent (Varazslat nyujtas-orokkevalosag): not defined. Implemented: "undefined".
3. Extra MP on a modifier (durationPerExtraMp) and power step k are separate inputs; both add MP. What E means for extensions is open (format doc, section 9).
4. Casting time summed with meditation time (Mental): sum is returned with a warning; the sources do not say how to mix them.
5. perMass (Teleport): the format does not name the weight input. Implemented: the single number input of the mosaic; its unit must be "g" or "kg", otherwise cost is undefined.
6. Composition mass unit: assumed grams (key "massG" in each part), because rates are given per massG.
7. compositionPerMass with a "rounding" value other than null: the format does not define its values. Implemented: cost undefined.
8. compositionGrid: the layout of "rates" (3x3) is not defined and all rates are null. Implemented: cost always undefined.
9. A group whose rate is null (e.g. group II for Atvaltoztatas): the composition is refused as an input error.
10. Form-dependent mosaic in the basic school (no tiers): a character has no tier there, so any form is refused. Is that intended?
11. Form with no chosen form on a form-dependent mosaic: the "form" input is required, so the mosaic is refused.
12. Whole-MP rule ("only whole MP can be spent"): per-mass costs are now charged in whole started blocks (user decision), so the mass patterns always give whole MP. If a total were still non-whole (no pattern does that today), it is returned exactly with the warning "rounding not defined"; the backend refuses to cast it.
13. Duration JSON shape: the format examples wrap time durations ({"kind":"time","time":{...}}) while section 3 and the modifier fields use {"kind":"time","amounts":{...}}. The parser accepts both. Please fix one shape.
14. nap = 24 h and the length of ev are still unconfirmed (format doc, section 9); ev is never converted.
15. Unknown JSON fields in a definition are ignored, not rejected.
16. Several rows of the same material group: decided by the user, only a single row per group is allowed; the engine refuses a composition that names a group more than once.
17. Messages: the engine returns message codes with arguments (`Msg`, `MsgCode`), not text. The application that uses the engine translates them (the backend: `messages.properties` Hungarian, `messages_en.properties` English). Every new `MsgCode` needs a key in both files; the backend test `MessageKeysTest` fails when one is missing.
18. Időzítés (`ido-idozites`, delay): OPEN, the user will come back to it. Source row: `+15 MP | +1 p | max. 1 év` (columns: extra MP, extra casting time, extra duration). Read by column it gives no delay length; the user's reading is "15 MP = +1 minute of delay, at most 1 year in total", which still leaves open how the delay grows with more MP and whether +1 p is also casting time. The engine returns the duration as undefined until the rule is given.
