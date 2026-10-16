# Mosaic definition format (draft for approval)

One JSON object per mosaic, stored in the mosaic table (JSONB). It is the single source for: the Angular input form, the backend validation, the rules engine, the chat tool input schemas and the admin editor. Source of the data: the plan file `/Users/Imola_Saraine_Lajos/.claude/plans/melodic-hatching-harp.md` (Parts 2 to 10). Terms: "mosaic" = one building block; "cost data" = everything that decides a mosaic's MP cost.

## 1. Fields of a mosaic

| Field | Meaning | Values |
|---|---|---|
| `id` | unique, ASCII | e.g. `anyagi-elemi-oselem-idezese` |
| `name` | display name (Hungarian) | e.g. `Őselem idézése` |
| `school` | school | `alapvarazslatok`, `anyagi`, `asztral`, `mental`, `ter`, `ido`, `szimpatikus`, `nekromancia` |
| `subgroup` | optional | `elemi`, `termeszetes` (Anyagi only) |
| `tier` | minimum tier (Fok.) in `school`, 0 to 5 (user decision). Alapvarázslatok mosaics have tier 0: every character meets it. A character with no entry for a school has tier 0 there. | number 0 to 5 |
| `status` | how complete the data is | see section 2 |
| `castingTime` | casting time, or meditation time (Mentál) | `{label, time}`; `label` is `casting` or `meditation` |
| `duration` | effect time | a time value, see section 3 |
| `range` | range | `zone`, `self`, `touch`, `pentagram`, `meters {value}`, `byInput`, `undefined` |
| `resistance` | ME type: what can resist it | `none`, `school {school}`, `concealment`, `spellMp`, `oppositeElement {input, pairs}`, `givenParaElement {input}`, `elementalForce`, `naturalMaterialMagic`, `lifeForce`, `armorSfe` (kept in the format, no mosaic uses it now), `undefined` (see "Resistance that depends on a chosen element" below) |
| `power` | strength (E, or Fp for pain points) | `{unit, baseE, step}` or `null` if the source gives no E. `step` = `{mp, e}` or `null` when the source has "-" (power cannot be raised) |
| `cost` | MP cost data | see section 4 |
| `modifier` | only for mosaics that add to the whole spell (extensions, Abszolút pont) | see section 5 |
| `options` | optional add-ons the player can tick | list of `{id, label, extraMp, extraCastingTime}` (Távolbalátás: move the window, +20 MP) |
| `inputs` | the boxes the page asks for | see section 6 |
| `effect` | text of what it does (shown in the spell panel) | string; language still to be decided |
| `source` | where in the plan it came from | e.g. `Part 10a` |

**Power rule (confirmed):** E = baseE + k × step.e, cost = baseMp + k × step.mp, k = 0, 1, 2, ...; only these steps exist. Base MP is mandatory; base E is the minimum.

## 2. Status

`status = {state, missing}`
- `complete`: every field is defined.
- `partial`: the cost is defined, but some other field is undefined (example: Teleport's casting time is blank; Távolbahatás's duration and E are `*`). The engine computes the cost and returns "undefined" for the missing outputs.
- `incomplete`: the cost is undefined (blank, "?", "Spec."). The engine refuses to calculate it and the page shows it as unavailable.
`missing` lists the field names.

## 3. Time values

`{ "kind": "time", "amounts": { "ora": 1, "kor": 2 } }` means 1 óra and 2 kör. Other kinds: `oneOff` (Egyszeri), `permanent` (maradandó), `formDependent` (depends on the chosen form), `undefined` (blank or "Spec." or `*`).
- Units: `sz` = 1 second, `kor` = 4 sz, `perc`, `ora`, `nap`, `ev`.
- Times are added unit by unit (1 óra 2 kör + 1 óra = 2 óra 2 kör) and are not normalised.
- For comparing, the engine converts to seconds: perc = 60, ora = 3600, nap = 86400 (assumes a day is 24 hours, to be confirmed). The length of `ev` is unknown, so it is not converted and compared only against other `ev` values.

## 4. Cost patterns

| `pattern` | Used by | Fields |
|---|---|---|
| `flat` | most mosaics | `baseMp` |
| `perMass` | Teleport (1 MP per 3 kg weight) | `rate: {mp, massG}`; charged in started blocks, see below |
| `compositionPerMass` | the natural-material mosaics | `rates`: for group `I`, `II`, `III` a `{mp, massG}` or `null` (not available for that group); `rounding`: always `null` (not needed: mass is charged in whole blocks, see below) |
| `compositionGrid` | Légiesség | `rows` (groups I, II, III), `columns` (`I`, `I+II`, `I+II+III`), `rates` per cell: `{mp, massG}` or `null` (all `null` today) |
| `lookup` | Távolbahatás | `input` and `table` of ranges with an MP value |
| `undefined` | the incomplete mosaics | none |

`compositionPerMass` and `compositionGrid` take the player's **composition**: a list of parts, each with a material group and a mass. Each part is charged at its own group's rate and the part costs are summed (confirmed). **Mass is charged in whole blocks (user decision, 2026-10-12):** a rate `{mp, massG}` means `mp` for every block of `massG` that is started. 3 MP per 2 kg: any mass up to 2 kg costs 3 MP, 2.1 kg costs 6 MP. Cost of a part = `mp × ceil(mass / massG)`; no mass costs nothing. So the MP is always whole. **Only a single row per material group (user decision):** a composition has at most one row for each of the groups I, II and III, so the mass of a group is one part, charged in blocks; the engine refuses a composition that names a group twice.

## 5. Modifiers (extensions, Abszolút pont)

`modifier = { appliesToWholeSpell: true, addsCastingTime, addsDuration, durationPerExtraMp }`
- A modifier applies to **all** mosaics in the spell (confirmed). Its MP is added to the spell (a flat cost), and its casting time and duration are added to the totals.
- `durationPerExtraMp`: extra duration for each additional MP spent on the modifier (the source: "+1 óra, then +1 óra per 1 MP").

## 6. Inputs (the boxes)

Each input is `{id, label, type, ...}`. Types:
- `choice`: a drop-down with fixed `values` (element, para-element, pass-through level). Optional `labels` (value to the Hungarian name shown to the player) and `descriptions` (value to a Hungarian explanation, shown under the drop-down once the value is chosen); both may only use keys that are in `values`.
- `number`: a number with a unit (weight, range in meters).
- `form`: a drop-down of forms; only forms whose tier the character has in the mosaic's school are listed (confirmed). Present when the duration is `formDependent`.
- `composition`: repeating rows of material group + mass.
- `text`: free text (for example a creature type; not decided).
The power box is not listed here: the page generates it from `power` (E choices base, base + step, ...).

### Resistance that depends on a chosen element (user decision 2026-10-16)
- **`oppositeElement`** (Őselem idézése): resisted by the **opposite** of the chosen element. `input` names the choice input, `pairs` maps each value to the resisting one: tűz-víz (`ostuz` and `osviz`), levegő-föld (`oslevego` and `osfold`). The pairs must cover every value of the input and be symmetric (the seed validator checks it).
- **`givenParaElement`** (Paraelem idézése): resisted by the **same** para-element that was chosen (`input` names the choice input; hő, fagy, fény, sötét).
- **`armorSfe`** (Elemi erő idézése): resisted by SFÉ (the table of the source says so; the kind `elementalForce`, resisted by Elemi erő itself, was used between V6 and V7 and is still understood but no mosaic uses it).
- The engine resolves this per cast: the result's `resistance` then carries `element`, the element that resists, and `elementLabel`, its Hungarian name from the `labels` of the choice input when it has them (never `input` or `pairs`). If the choice is missing or has no pair, the resistance is "undefined" with a reason. Nothing is calculated from it yet (weakening at unequal E is not modelled).

## 7. Related data (separate tables, linked by key)
- **Forms:** `{id, name, tier, duration}`. Every form-dependent mosaic allows every form.
- **Material groups:** `{id, name, examples}` for `I`, `II`, `III`.

## 8. Examples

**Fürkészés** (basic, no tier):
```json
{ "id": "alap-furkeszes", "name": "Fürkészés", "school": "alapvarazslatok", "tier": 0,
  "status": {"state": "complete", "missing": []},
  "castingTime": {"label": "casting", "time": {"kind": "time", "amounts": {"kor": 1}}},
  "duration": {"kind": "oneOff"}, "range": {"kind": "zone"},
  "resistance": {"kind": "concealment"},
  "power": {"unit": "E", "baseE": 1, "step": {"mp": 1, "e": 1}},
  "cost": {"pattern": "flat", "baseMp": 7}, "inputs": [], "source": "Part 2" }
```
Casting at E 4: k = 3, cost = 7 + 3 × 1 = 10 MP.

**Őselem idézése** (uses a form):
```json
{ "id": "anyagi-elemi-oselem-idezese", "name": "Őselem idézése", "school": "anyagi", "subgroup": "elemi", "tier": 2,
  "status": {"state": "complete", "missing": []},
  "castingTime": {"label": "casting", "time": {"kind": "time", "amounts": {"sz": 1}}},
  "duration": {"kind": "formDependent"}, "range": {"kind": "zone"},
  "resistance": {"kind": "oppositeElement", "input": "element",
                 "pairs": {"ostuz": "osviz", "osviz": "ostuz", "osfold": "oslevego", "oslevego": "osfold"}},
  "power": {"unit": "E", "baseE": 1, "step": {"mp": 1, "e": 1}},
  "cost": {"pattern": "flat", "baseMp": 1},
  "inputs": [ {"id": "element", "type": "choice", "values": ["ostuz", "osviz", "osfold", "oslevego"]},
              {"id": "form", "type": "form"} ], "source": "Part 10a" }
```
E 3: k = 2, cost = 1 + 2 × 1 = 3 MP. (Changed 2026-10-16: it used to be base 4 MP and 4 MP per step; now it costs the same as Elemi erő idézése.)

**Beszéd – Zavarás** (Mentál, power in steps of 2 MP for 3 E):
```json
{ "id": "mental-beszed-zavaras", "name": "Beszéd – Zavarás", "school": "mental", "tier": 2,
  "status": {"state": "complete", "missing": []},
  "castingTime": {"label": "meditation", "time": {"kind": "time", "amounts": {"sz": 1}}},
  "duration": {"kind": "time", "time": {"kind": "time", "amounts": {"kor": 3}}},
  "range": {"kind": "meters", "value": 20}, "resistance": {"kind": "school", "school": "mental"},
  "power": {"unit": "E", "baseE": 3, "step": {"mp": 2, "e": 3}},
  "cost": {"pattern": "flat", "baseMp": 2}, "inputs": [], "source": "Part 6" }
```
Options: 2 MP for 3 E, 4 MP for 6 E, 6 MP for 9 E; nothing in between.

**Átformázás** (composition, per-mass rates, charged in started blocks):
```json
{ "id": "anyagi-term-atformazas", "name": "Átformázás", "school": "anyagi", "subgroup": "termeszetes", "tier": 2,
  "status": {"state": "complete", "missing": []},
  "castingTime": {"label": "casting", "time": {"kind": "time", "amounts": {"sz": 4}}},
  "duration": {"kind": "time", "time": {"kind": "time", "amounts": {"kor": 2}}},
  "range": {"kind": "zone"}, "resistance": {"kind": "naturalMaterialMagic"}, "power": null,
  "cost": {"pattern": "compositionPerMass", "rounding": null,
           "rates": { "I": {"mp": 1, "massG": 1000}, "II": {"mp": 1, "massG": 500}, "III": {"mp": 1, "massG": 1} }},
  "inputs": [ {"id": "composition", "type": "composition"} ], "source": "Part 10b" }
```
A sword of a 2 kg iron blade (group II: 2000 g = 4 blocks of 500 g = 4 MP), a 0.5 kg wooden hilt (group I: 500 g = 1 started block of 1000 g = 1 MP) and 20 g gold inlay (group III: 20 blocks of 1 g = 20 MP): 4 + 1 + 20 = 25 MP.

**Varázslat nyújtás-óra** (modifier):
```json
{ "id": "ido-nyujtas-ora", "name": "Varázslat nyújtás-óra", "school": "ido", "tier": 2,
  "status": {"state": "complete", "missing": []},
  "castingTime": {"label": "casting", "time": {"kind": "time", "amounts": {}}},
  "duration": {"kind": "undefined"}, "range": {"kind": "undefined"},
  "resistance": {"kind": "school", "school": "ido"},
  "power": {"unit": "E", "baseE": 1, "step": {"mp": 1, "e": 1}},
  "cost": {"pattern": "flat", "baseMp": 25},
  "modifier": {"appliesToWholeSpell": true,
               "addsCastingTime": {"kind": "time", "amounts": {"perc": 5}},
               "addsDuration": {"kind": "time", "amounts": {"ora": 1}},
               "durationPerExtraMp": {"kind": "time", "amounts": {"ora": 1}}},
  "inputs": [], "source": "Part 8" }
```

**Távolbahatás** (lookup; partial: duration and E are `*`):
```json
{ "id": "ter-tavolbahatas", "name": "Távolbahatás", "school": "ter", "tier": 2,
  "status": {"state": "partial", "missing": ["duration", "power"]},
  "castingTime": {"label": "casting", "time": {"kind": "time", "amounts": {"sz": 4}}},
  "duration": {"kind": "undefined"}, "range": {"kind": "byInput"},
  "resistance": {"kind": "school", "school": "ter"}, "power": null,
  "cost": {"pattern": "lookup", "input": "rangeM",
           "table": [ {"fromM": 20, "toM": 39, "mp": 10}, {"fromM": 40, "toM": 99, "mp": 18},
                      {"fromM": 100, "toM": 999, "mp": 25}, {"fromM": 1000, "toM": 99000, "mp": 40},
                      {"fromM": 100000, "toM": null, "mp": 50} ]},
  "inputs": [ {"id": "rangeM", "type": "number", "unit": "m"} ], "source": "Part 3" }
```
The source covers 1000 m to 99 km and from 100 km; values between 99 km and 100 km are not covered, and the engine returns "undefined" for them.

**Légiesség** (grid; incomplete, rates not defined):
```json
{ "id": "anyagi-term-legiesseg", "name": "Légiesség", "school": "anyagi", "subgroup": "termeszetes", "tier": 3,
  "status": {"state": "incomplete", "missing": ["cost"]},
  "castingTime": {"label": "casting", "time": {"kind": "time", "amounts": {"sz": 2}}},
  "duration": {"kind": "time", "time": {"kind": "time", "amounts": {"perc": 1}}},
  "range": {"kind": "zone"}, "resistance": {"kind": "naturalMaterialMagic"}, "power": null,
  "cost": {"pattern": "compositionGrid", "rows": ["I", "II", "III"], "columns": ["I", "I+II", "I+II+III"], "rates": null},
  "inputs": [ {"id": "composition", "type": "composition"},
              {"id": "passThrough", "type": "choice", "values": ["I", "I+II", "I+II+III"]} ], "source": "Part 10b" }
```

## 9. Open points that touch the format
- Rounding of per-mass costs: decided, mass is charged in whole started blocks (section 4); `rounding` stays `null`.
- What E means for the extension mosaics (the source gives E 1 and +E/MP 1/1 beside the duration scaling).
- Whether forms cost MP (not in the format yet).
- Language of the `effect` text: Hungarian (user decision); the stored English texts are to be replaced, see `database/i18n/hu-texts.template.json`. `choice` inputs will get an optional `valueLabels` (id to Hungarian label) so that ids such as `ostuz` stay stable while the page shows the label.
- The Életerő mosaics (Fp, casting time per MP) are set aside and not in the format.
- Length of `ev` and whether `nap` is 24 hours.
