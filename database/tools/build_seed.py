"""One-off generator of the seed JSON files from the plan (Parts 2 to 10).
After generation the JSON files in ../seed are the source of truth; do not re-run this
after editing them by hand (it overwrites them). Run: .venv/bin/python tools/build_seed.py
"""
import json
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "seed")

# ---------- helpers ----------
def T(**amounts):
    return {"kind": "time", "amounts": amounts}

def cast(label="casting", **amounts):
    return {"label": label, "time": T(**amounts)}

UNDEF_CAST = lambda label="casting": {"label": label, "time": {"kind": "undefined"}}
DUR = lambda **a: {"kind": "time", "time": T(**a)}
ONEOFF = {"kind": "oneOff"}
FORMDEP = {"kind": "formDependent"}
UNDEF = {"kind": "undefined"}
ZONE = {"kind": "zone"}
SELF = {"kind": "self"}
TOUCH = {"kind": "touch"}

def res_school(s):
    return {"kind": "school", "school": s}

def power(base_e, step_mp=None, step_e=None, unit="E"):
    step = None if step_mp is None else {"mp": step_mp, "e": step_e}
    return {"unit": unit, "baseE": base_e, "step": step}

def flat(mp):
    return {"pattern": "flat", "baseMp": mp}

UNDEF_COST = {"pattern": "undefined"}

def status(missing, cost_missing=False):
    if cost_missing:
        state = "incomplete"
    else:
        state = "partial" if missing else "complete"
    return {"state": state, "missing": list(missing)}

def mosaic(id, name, school, tier, missing, cast_t, dur, rng, res, pw, cost, inputs=None,
           source="", subgroup=None, effect=None, modifier=None, options=None):
    cost_missing = cost["pattern"] == "undefined" or (cost["pattern"] == "compositionGrid" and cost.get("rates") is None)
    d = {"id": id, "name": name, "school": school}
    if subgroup:
        d["subgroup"] = subgroup
    d["tier"] = tier
    d["status"] = status(missing, cost_missing)
    d["castingTime"] = cast_t
    d["duration"] = dur
    d["range"] = rng
    d["resistance"] = res
    d["power"] = pw
    d["cost"] = cost
    if modifier is not None:
        d["modifier"] = modifier
    if options is not None:
        d["options"] = options
    d["inputs"] = inputs or []
    if effect:
        d["effect"] = effect
    d["source"] = source
    return d

def empty_row(id, name, school, tier, source, cast_label="casting", res=None, extra_missing=()):
    """Row with no usable data (only a name and maybe a tier)."""
    missing = ["cost", "castingTime", "duration", "range", "power"]
    if res is None:
        res = UNDEF
        missing.append("resistance")
    if tier is None:
        missing.insert(0, "tier")
    return mosaic(id, name, school, tier, missing, UNDEF_CAST(cast_label), UNDEF, UNDEF, res, None,
                  UNDEF_COST, source=source)

def write(name, data):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")

# ---------- Alapvarazslatok (Part 2) ----------
P2 = "Part 2"
alap = [
    mosaic("alap-furkeszes", "Fürkészés", "alapvarazslatok", 0, [], cast(kor=1), ONEOFF, ZONE,
           {"kind": "concealment"}, power(1, 1, 1), flat(7), source=P2),
    mosaic("alap-destrukcio", "Destrukció", "alapvarazslatok", 0, [], cast(sz=2), ONEOFF, ZONE,
           {"kind": "spellMp"}, power(1, 1, 1), flat(1), source=P2),
    # +E/MP "1/5 kg" is ambiguous (backlog): power step unknown, stored as fixed base E.
    mosaic("alap-magikussa-tetel", "Mágikussá tétel", "alapvarazslatok", 0, ["power"], cast(kor=1),
           DUR(perc=1), ZONE, {"kind": "none"}, power(1), flat(1), source=P2),
    mosaic("alap-magia-leplezese", "Mágia leplezése", "alapvarazslatok", 0, [], cast(kor=1),
           DUR(kor=1), ZONE, {"kind": "none"}, power(1, 1, 1), flat(1), source=P2),
]

# ---------- Termagia (Part 3) ----------
P3 = "Part 3"
TER = res_school("ter")
ter = [
    mosaic("ter-lokalizalas", "Lokalizálás", "ter", 2, [], cast(sz=1), ONEOFF, SELF, TER,
           power(1, 1, 1), flat(1), source=P3),
    mosaic("ter-tavolbahatas", "Távolbahatás", "ter", 2, ["duration", "power"], cast(sz=4), UNDEF,
           {"kind": "byInput"}, TER, None,
           {"pattern": "lookup", "input": "rangeM",
            "table": [{"fromM": 20, "toM": 39, "mp": 10}, {"fromM": 40, "toM": 99, "mp": 18},
                      {"fromM": 100, "toM": 999, "mp": 25}, {"fromM": 1000, "toM": 99000, "mp": 40},
                      {"fromM": 100000, "toM": None, "mp": 50}]},
           [{"id": "rangeM", "label": "Hatótáv", "type": "number", "unit": "m"}], source=P3),
    mosaic("ter-teleport", "Teleport", "ter", 3, ["castingTime"], UNDEF_CAST(), ONEOFF, ZONE, TER,
           power(1, 1, 1), {"pattern": "perMass", "rate": {"mp": 1, "massG": 3000}},
           [{"id": "massKg", "label": "Tömeg", "type": "number", "unit": "kg"}], source=P3),
    mosaic("ter-terkapu", "Térkapu", "ter", 3, [], cast(kor=1), DUR(perc=3), ZONE, TER,
           power(1, 1, 1), flat(68), source=P3),
    empty_row("ter-terzavaras", "Térzavarás", "ter", None, P3),
    mosaic("ter-tergomb", "Térgömb", "ter", 4, [], cast(kor=1), DUR(perc=3), ZONE, TER,
           power(1, 1, 1), flat(82), source=P3),
    mosaic("ter-sikkapu-anyagi", "Síkkapu – Anyagi", "ter", 4, [], cast(perc=1), DUR(kor=1), ZONE, TER,
           power(1, 1, 1), flat(150), source=P3),
    mosaic("ter-sikkapu-demoni", "Síkkapu – Démoni", "ter", 4, [], cast(perc=5), DUR(kor=1), ZONE, TER,
           power(1, 1, 1), flat(200), source=P3),
    mosaic("ter-abszolut-pont", "Abszolút pont", "ter", 4, ["duration", "range", "power"], cast(),
           UNDEF, UNDEF, TER, None, flat(20),
           modifier={"appliesToWholeSpell": True, "addsCastingTime": T(sz=2),
                     "addsDuration": {"kind": "undefined"}, "durationPerExtraMp": None},
           source=P3),
    mosaic("ter-idezes", "Idézés", "ter", 2, ["duration"], cast(sz=7), UNDEF, {"kind": "pentagram"}, TER,
           power(1, 1, 1), flat(15), source=P3),
    mosaic("ter-tavolbalatas", "Távolbalátás", "ter", 4, [], cast(kor=1), DUR(kor=1), ZONE, TER,
           power(1, 1, 1), flat(30),
           options=[{"id": "move", "label": "Az ablak mozgatása (max. 100 km/h)", "extraMp": 20,
                     "extraCastingTime": None}],
           effect="One-way transparent window, max 1 m x 1 m, to observe an area; sensed from the other side only as magic. Fixed unless the move option is taken.",
           source=P3),
]

# ---------- Mental (Part 6) ----------
P6 = "Part 6"
MENTAL = res_school("mental")
subjects = [  # id, name, tier override, {mode: (mp, baseE, numerator E, denominator MP)}
    ("beszed", "Beszéd", None, {"olvasas": (1, 2, 2, 1), "iras": (1, 1, 1, 1), "zavaras": (2, 3, 3, 2), "benitas": (1, 1, 1, 1)}),
    ("erzekeles", "Érzékelés", None, {"olvasas": (1, 1, 1, 1), "iras": (2, 1, 1, 2), "zavaras": (1, 1, 1, 1), "benitas": (2, 1, 1, 2)}),
    ("emlek", "Emlék", None, {"olvasas": (1, 2, 2, 1), "iras": (1, 1, 1, 1), "zavaras": (2, 1, 1, 2), "benitas": (3, 1, 1, 3)}),
    ("mozgas", "Mozgás", None, {"olvasas": (1, 2, 2, 1), "iras": (2, 1, 1, 2), "zavaras": (1, 1, 1, 1), "benitas": (3, 1, 1, 3)}),
    ("akarat", "Akarat", None, {"olvasas": (1, 1, 1, 1), "iras": (4, 1, 1, 4), "zavaras": (3, 1, 1, 3), "benitas": (5, 1, 1, 5)}),
    ("fajdalom", "Fájdalom", 4, {"olvasas": (1, 1, 1, 1), "iras": (4, 1, 1, 4), "zavaras": (3, 1, 1, 3), "benitas": (5, 1, 1, 5)}),
    ("igazsag", "Igazság", 4, {"olvasas": (1, 2, 2, 1), "iras": (2, 1, 1, 2), "zavaras": (1, 1, 1, 1), "benitas": (3, 1, 1, 3)}),
]
modes = [  # id, name, duration, tier, meaning
    ("olvasas", "Olvasás", DUR(perc=5), 3, "read: glimpse into someone's mind"),
    ("iras", "Írás", DUR(perc=1), 3, "write: put in something that was not there, e.g. a memory"),
    ("zavaras", "Zavarás", DUR(kor=3), 2, "disrupt: make it hard to focus; jumbled"),
    ("benitas", "Bénítás", DUR(kor=1), 2, "disable: remove a thought, memory, ability or feeling"),
]
subject_gloss = {"beszed": "speech", "erzekeles": "perception", "emlek": "memory", "mozgas": "movement",
                 "akarat": "will", "fajdalom": "pain", "igazsag": "truth"}
mental = []
for sid, sname, tier_over, vals in subjects:
    for mid, mname, dur, mtier, meaning in modes:
        mp, be, num, den = vals[mid]
        mental.append(mosaic(
            f"mental-{sid}-{mid}", f"{sname} – {mname}", "mental", tier_over or mtier, [],
            cast("meditation", sz=1), dur, {"kind": "meters", "value": 20}, MENTAL,
            power(be, den, num), flat(mp), effect=f"{subject_gloss[sid]}, {meaning}", source=P6))
mental.append(mosaic(
    "mental-halo", "Háló", "mental", 4, ["cost"], cast("meditation", sz=1), DUR(kor=1),
    {"kind": "meters", "value": 20}, MENTAL, None, UNDEF_COST,
    effect="A net that links people's minds together; has no strength (+E/MP '-'); MP is '?' in the source.",
    source=P6))
mental.append(mosaic(
    "mental-mentalleny-idezese", "Mentállény idézése", "mental", 4, ["cost", "power"],
    cast("meditation", sz=1), DUR(kor=1), {"kind": "meters", "value": 20}, MENTAL, None, UNDEF_COST,
    effect="Summoning a mental being; power and cost depend on the type of creature pulled to the place.",
    source=P6))

# ---------- Asztral (Part 7) ----------
P7 = "Part 7"
AST = res_school("asztral")
def ast(id, name, tier, mp, be, num, den, c, d):
    return mosaic(id, name, "asztral", tier, [], c, d, ZONE, AST, power(be, den, num), flat(mp), source=P7)
asztral = [
    ast("asztral-szeretet-gyulolet", "Szeretet/Gyűlölet", 3, 1, 1, 1, 1, cast(sz=1), DUR(perc=1)),
    ast("asztral-batorsag-felelem", "Bátorság/Félelem", 2, 3, 2, 2, 3, cast(sz=1), DUR(perc=1)),
    ast("asztral-vagy-undor", "Vágy/Undor", 3, 1, 1, 1, 1, cast(sz=1), DUR(perc=1)),
    ast("asztral-nyugalom-ingerultseg", "Nyugalom/Ingerültség", 2, 1, 2, 2, 1, cast(sz=1), DUR(perc=1)),
    ast("asztral-huseg-hutlenseg", "Hűség/Hűtlenség", 2, 1, 2, 2, 1, cast(sz=1), DUR(perc=1)),
    ast("asztral-semlegesseg-elvakultsag", "Semlegesség/Elvakultság", 3, 1, 1, 1, 1, cast(sz=2), DUR(kor=2)),
    ast("asztral-elemzes", "Asztrál Elemzés", 4, 1, 3, 3, 1, cast(sz=1), ONEOFF),
    mosaic("asztral-arnyalas", "Árnyalás", "asztral", 4, ["cost", "castingTime", "duration", "range", "power"],
           UNDEF_CAST(), UNDEF, UNDEF, AST, None, UNDEF_COST, source=P7),
    mosaic("asztral-kiterjesztes", "Asztrál Kiterjesztés", "asztral", 4, ["cost", "duration", "power"],
           cast(sz=2), UNDEF, ZONE, AST, None, UNDEF_COST, source=P7),
    mosaic("asztral-lenyomat", "Lenyomat", "asztral", 4, ["cost", "duration", "power"],
           cast(kor=2), UNDEF, ZONE, AST, None, UNDEF_COST, source=P7),
    empty_row("asztral-leny-idezese", "Asztrál lény idézése", "asztral", None, P7),
    mosaic("asztral-tukrozes", "Tükrözés", "asztral", 4, ["castingTime", "duration", "range", "resistance"],
           UNDEF_CAST(), UNDEF, UNDEF, UNDEF, power(1, 2, 1), flat(2), source=P7),
]

# ---------- Idomagia (Part 8) ----------
P8 = "Part 8"
IDO = res_school("ido")
def ext(id, name, mp, add_cast, add_dur, per_mp, missing=(), effect=None):
    return mosaic(id, name, "ido", 2, list(missing), cast(), UNDEF, UNDEF, IDO, power(1, 1, 1), flat(mp),
                  modifier={"appliesToWholeSpell": True, "addsCastingTime": add_cast,
                            "addsDuration": add_dur, "durationPerExtraMp": per_mp},
                  effect=effect, source=P8)
ido = [
    ext("ido-nyujtas-kor", "Varázslat nyújtás-kör", 2, T(kor=1), T(kor=1), T(kor=1)),
    ext("ido-nyujtas-perc", "Varázslat nyújtás-perc", 10, T(kor=3), T(perc=1), T(perc=1)),
    ext("ido-nyujtas-ora", "Varázslat nyújtás-óra", 25, T(perc=5), T(ora=1), T(ora=1)),
    ext("ido-nyujtas-nap", "Varázslat nyújtás-nap", 45, T(perc=5), T(nap=1), T(nap=10)),
    ext("ido-nyujtas-ev", "Varázslat nyújtás-év", 75, T(ora=1), T(ev=1), T(ev=1)),
    ext("ido-nyujtas-orokkevalosag", "Varázslat nyújtás-örökkévalóság", 150, T(ora=12),
        {"kind": "permanent"}, None),
    # Időzítés: "max. 1 év" cannot be expressed in the format; duration kept undefined (see QUESTIONS).
    ext("ido-idozites", "Időzítés", 15, T(perc=1), {"kind": "undefined"}, None, missing=["duration"],
        effect="Timing/delay; source gives 'max. 1 év' as its duration."),
]
def slow(id, name, tier, c, d, source=P8):
    return mosaic(id, name, "ido", tier, ["cost"], cast(**c), DUR(**d), ZONE, IDO, power(1, 1, 1), UNDEF_COST,
                  source=source)
ido += [
    slow("ido-lassitas", "Lassítás", 3, {"sz": 3}, {"kor": 1}),
    slow("ido-gyorsitas", "Gyorsítás", 3, {"sz": 3}, {"kor": 1}),
    slow("ido-lassitas-terulet", "Lassítás – Terület", 4, {"kor": 10}, {"kor": 1}),
    slow("ido-gyorsitas-terulet", "Gyorsítás – Terület", 4, {"kor": 10}, {"kor": 1}),
    empty_row("ido-idougras", "Időugrás", "ido", 3, P8, res=IDO),
    empty_row("ido-idokapu", "Időkapu", "ido", 4, P8, res=IDO),
    # Időfagyás: cost is "150 + Spec." -> undefined.
    slow("ido-idofagyas", "Időfagyás", 4, {"perc": 1}, {"kor": 1}),
    empty_row("ido-idozavaras", "Időzavarás", "ido", None, P8),
    mosaic("ido-pontos-ido", "Pontos idő", "ido", 2, [], cast(sz=1), DUR(kor=1), SELF, IDO,
           power(1, 1, 1), flat(1), source=P8),
]

# ---------- Nekromancia (Part 9) ----------
P9 = "Part 9"
LIFE = {"kind": "lifeForce"}
NONE = {"kind": "none"}
nekro = [
    mosaic("nekro-elohalott-taszitas", "Élőholt taszítás", "nekromancia", 2, ["cost", "range"], cast(kor=2),
           DUR(perc=1), UNDEF, NONE, power(1, 2, 1), UNDEF_COST, source=P9),
    mosaic("nekro-elohalott-erzekeles", "Élőholt érzékelés", "nekromancia", 2, [], cast(sz=3), DUR(kor=1),
           SELF, NONE, power(1), flat(2), source=P9),
    mosaic("nekro-negativ-energia", "Negatív energia", "nekromancia", 3, [], cast(sz=1), FORMDEP, ZONE, LIFE,
           power(1, 4, 1), flat(4), [{"id": "form", "label": "Forma", "type": "form"}], source=P9),
    empty_row("nekro-kepzettseg-iranyitasa", "Képzettség irányítása", "nekromancia", 4, P9),
    mosaic("nekro-tulajdonsag-iranyitasa", "Tulajdonság irányítása", "nekromancia", 4, [], cast(sz=3),
           DUR(kor=1), TOUCH, LIFE, power(1, 6, 1), flat(6), source=P9),
    mosaic("nekro-iranyitas", "Irányítás", "nekromancia", 4, ["cost", "duration", "range", "resistance", "power"],
           cast(sz=2), UNDEF, UNDEF, UNDEF, None, UNDEF_COST, source=P9),
    empty_row("nekro-szellemidezes", "Szellemidézés", "nekromancia", 3, P9, res=NONE),
    empty_row("nekro-elohalott-idezes", "Élőholt idézés", "nekromancia", 3, P9, res=NONE),
    empty_row("nekro-demonidezes", "Démonidézés", "nekromancia", 3, P9, res=NONE),
    mosaic("nekro-halott-mozgatasa", "Halott mozgatása", "nekromancia", 3, [], cast(perc=1), DUR(kor=1), ZONE,
           NONE, power(1), flat(30), source=P9),
    mosaic("nekro-lelek-bebortonzese", "Lélek bebörtönzése", "nekromancia", 2, [], cast(kor=1), DUR(kor=1), ZONE,
           NONE, power(1), flat(20), source=P9),
]

# ---------- Anyagi (Part 10) ----------
P10A, P10B = "Part 10a", "Part 10b"
def form_input():
    return {"id": "form", "label": "Forma", "type": "form"}
# Names and descriptions of the element choices (the user's Hungarian source text, Part 10a; obvious typos fixed).
ELEMENT_LABELS = {"ostuz": "Ős tűz", "osviz": "Ős víz", "osfold": "Ős föld", "oslevego": "Ős levegő"}
ELEMENT_DESCRIPTIONS = {
    "ostuz": "Önállóan létező anyag, így a természetes tűzzel ellentétben nem kell semmi, ami táplálja. A belsejében az erősségénél tizenötször nagyobb erősségű elemi hő van (kb. 80°+20°/tűz-E). A környezetbe szétsugárzott hő viszont már normális, és a fénye sem elemi fény (belül sem).",
    "osviz": "Teljesen tiszta átlátszó víz. Megidézésekor a környezet hőmérsékletét veszi fel (nem jön vele az elemi hideg paraelem).",
    "osfold": "Tökéletesen száraz, szürkés, porszerű anyag. Szemcséinek a mérete a homokéhoz hasonló. A normál levegőt is kiszorítja, így a légzés benne nehézkesebbé válik.",
    "oslevego": "Tiszta levegő. Belélegezhető. Nem kíséri hő hatás (környezet hőmérséklete) a megidézésekor. A füst, légimérgek és egyéb anyagokat kiszorítja onnan, ahol van.",
}
PARA_LABELS = {"ho": "Elemi hő", "fagy": "Elemi hideg", "feny": "Elemi fény", "setet": "Elemi sötétség"}
PARA_BASE_DESCRIPTIONS = {
    "ho": "A tűz és a föld paraeleme. Megidézve minden természetes élettelen anyagot egy szempillantás alatt felmelegít az erősségnek megfelelő hőmérsékletre. Ellentétes paraeleme az elemi hideg.",
    "fagy": "A levegő és a víz paraeleme. Megidézve minden természetes élettelen anyagot egy szempillantás alatt lehűt az erősségnek megfelelő hőmérsékletre. Ellentétes paraeleme az elemi hő.",
    "feny": "A tűz és a levegő paraeleme. Megidézve az adott területen minden pont olyan fényességű lesz, mint az elemi fény erőssége (így az árnyékok eltűnnek). Ellentétes paraeleme az elemi sötétség.",
    "setet": "A víz és a föld paraeleme. Megidézve az adott területen elnyeli a fényt, minden pontban olyan sötét lesz, mint ami az erősségnek megfelel. Ellentétes paraeleme az elemi fény.",
}
OSELEM_EFFECT = (
    "A varázsló őselemet teremt, amit a kívánt formába zár (formázás). Ez lehet a Hat Iskolaforma egyike, vagy – ha a varázsló bír efféle tudással – "
    "valamely Szabálytalan Elemi Forma. Azt, hogy a teremtett őselem meddig marad együtt, a formázás határozza meg. "
    "Az ellentétes őselemek gyengítik, akár ki is oltják egymást. A varázslók az őstüzet általában támadásra, az ősvizet általában védekezésre használják, "
    "míg a további két őselemhez egyéb céllal, vagy más létsíkok teremtményeivel szemben nyúlnak."
)
HO_TEXT = (
    "Hő teremtése: A varázsló elemi hőt teremt, és a kívánt formába önti. A hőmérséklet az adott formán belül eső területen E-nként 10 °C, "
    "függetlenül a természetes hőmérséklettől – akár sebez is, a formán túl ellenben egyáltalán nem érződik. "
    "A sebzés körönként 100 °C-nál 1 Sp (sebzés pont), és minden további teljes 20 °C-onként 1 Sp-vel nő."
)
FAGY_TEXT = (
    "Fagy teremtése: A varázsló elemi fagyot teremt, és a kívánt formába foglalja. Az azon belül eső területen hűvös van, fokozott erősítés során fagy jön létre. "
    "A hőmérséklet E-nként −10 °C, függetlenül a természetes hőmérséklettől. "
    "A sebzés körönként −40 °C-nál jelentkezik (1 Sp), és innen kezdve minden további teljes 20 °C-onként 1 Sp-vel nő."
)
FENY_LINES = [
    "1 E: gyertya lángja", "2 E: apró gyertya lángja", "3 E: olvasógyertya lángja", "4 E: templomi gyertya lángja", "5 E: mécses lángja",
    "6 E: fáklya lángja", "7 E: nagy fáklya lángja", "8 E: tábortűz fénye", "9 E: máglya fénye", "10 E: alkonyi fény",
    "11 E: napfény borús időben", "12 E: fátyolos napfény", "13 E: napsütés", "14 E: déli verőfény", "15+ E: vakító fény",
    "20+ E: nyitott szem esetén végleges vakság",
]
FENY_TEXT = (
    "Fény teremtése: A varázsló a formázás megszabta területen és alakban elemi fényt teremt. Amennyiben az eredeti fényesség kisebb, akkor ott nem látszik, "
    "de a területen mindenhol az adott fényesség lesz minimum, így például az árnyékok eltűnnek.\n\n"
    + "\n".join(FENY_LINES) + "\n\n"
    "Az elemi fény azt a területet, ahol érvényesül, a fenti táblázat szerint világítja meg. Ezt a természeti hatások nem befolyásolhatják, kizárólag az elemi sötét! "
    "Ha a természetes fény erőssége nagyobb a paraeleménél, akkor ez utóbbinak a jelenlétét észre sem vesszük."
)
SETET_LINES = [
    "1 E: alkonyat", "2 E: telihold", "3 E: fogyóhold", "4 E: újhold", "5 E: csillagos éjszaka", "6 E: borús éjszaka",
    "7 E: éjszaka kivilágítatlan szobában", "8 E: barlangban", "9 E: vaksötét", "10 E: kráni sötétség", "11+ E: ultra- és infralátás sem működik",
]
SETET_TEXT = (
    "Sötét teremtése: Hasonló a fény teremtéséhez, úgy szerencsés elképzelni, akár valami negatív, fekete fényt.\n\n"
    + "\n".join(SETET_LINES) + "\n\n"
    "A területen, ahol az elemi sötét érvényesül, a fenti táblázat mutatta fényviszonyok érvényesülnek, függetlenül a természetes fénytől. "
    "Az elemi sötétet csak az elemi fény világíthatja be, a lámpások és a fáklyák világa vagy akár a nap ehhez nem elegendő, fényük nem is látszik, csak a hőhatásuk érvényesül."
)
ELEMI_ERO_FIRST = "A mágikus energiákat felhasználva mozgatni vagy megállítani lehet tárgyakat."
ELEMI_ERO_EFFECT = (
    ELEMI_ERO_FIRST + "\n\n"
    "Elemi erő teremtése: A varázsló olyan mennyiségű elemi erőt teremt, amely képes megtartani 5 kg súlyú vagy ugyanekkora erővel nekifeszülő tárgyat; "
    "avagy semlegesíteni 1 Sp-t, akár egy vért SFÉ-jét. Ez utóbbi leginkább aurára való formázás esetén fordul elő.\n\n"
    "álló súly (-): 1 E elemi erő 5 kg-ot tart meg\nséta (×2): 2,5 kg\nfutás (×5): 1 kg\nvágta (×10): 0,5 kg\n\n"
    "A súlyváltozás megmutatja, hogy az adott sebességgel haladó tárgy súlya mennyivel számít nagyobbnak, amikor az elemi erőnek ütközik. "
    "Az „1 E elemi erő megtart” oszlop megadja, hogy mekkora súlyt képes megtartani 1 E elemi erő, ha az a megadott sebességgel ütközik neki. "
    "Ha a súly meghaladja az elemi erő megtartó képességét, az semmilyen hatást nem fejt ki ellene.\n\n"
    "Az elemi erőből teremtett nyíl annyi Sp-t (sebzés pontot) sebez, mint amennyi az E-je (ereje), vagyis amennyit a hasonló, valódi anyagból való fegyver is sebezne.\n"
    "Kitörés esetén az epicentrumban annyi Sp-t sebez, amennyi az E-je, kifelé távolodva negyedméterenként eggyel kevesebbet; "
    "nagy erősítés esetén feldönti a tárgyakat, embereket, kicsavarja a fákat, bokrokat.\n"
    "Az elemi erőből létrehozott szőnyeg E-nként 5 kg súlyt képes megtartani.\n"
    "A fal ugyanennyi súlyú nekifeszülő terhet tart távol, nem engedvén azt át magán."
)

PARA_DESCRIPTIONS = {k: v + "\n\n" + {"ho": HO_TEXT, "fagy": FAGY_TEXT, "feny": FENY_TEXT, "setet": SETET_TEXT}[k]
                    for k, v in PARA_BASE_DESCRIPTIONS.items()}
elemi = [
    mosaic("anyagi-elemi-oselem-idezese", "Őselem idézése", "anyagi", 2, [], cast(sz=1), FORMDEP, ZONE,
           {"kind": "oppositeElement", "input": "element", "pairs": {"ostuz": "osviz", "osviz": "ostuz", "osfold": "oslevego", "oslevego": "osfold"}},
           power(1, 1, 1), flat(1),
           [{"id": "element", "label": "Őselem", "type": "choice", "values": ["ostuz", "osviz", "osfold", "oslevego"],
             "labels": ELEMENT_LABELS, "descriptions": ELEMENT_DESCRIPTIONS},
            form_input()], subgroup="elemi", source=P10A, effect=OSELEM_EFFECT),
    mosaic("anyagi-elemi-paraelem-idezese", "Paraelem idézése", "anyagi", 3, [], cast(sz=1), FORMDEP, ZONE,
           {"kind": "givenParaElement", "input": "paraElement"}, power(1, 1, 1), flat(1),
           [{"id": "paraElement", "label": "Paraelem", "type": "choice", "values": ["ho", "fagy", "feny", "setet"],
             "labels": PARA_LABELS, "descriptions": PARA_DESCRIPTIONS},
            form_input()], subgroup="elemi", source=P10A),
    mosaic("anyagi-elemi-elemi-ero-idezese", "Elemi erő idézése", "anyagi", 3, [], cast(sz=1), FORMDEP, ZONE,
           {"kind": "armorSfe"}, power(1, 1, 1), flat(1), [form_input()], subgroup="elemi", source=P10A,
           effect=ELEMI_ERO_EFFECT),
    mosaic("anyagi-elemi-elemental-idezes", "Elementál idézés", "anyagi", 4, ["cost", "duration", "range"],
           cast(sz=1), UNDEF, UNDEF, NONE, power(1), UNDEF_COST, subgroup="elemi", source=P10A),
    mosaic("anyagi-elemi-elemi-vedelem", "Elemi védelem", "anyagi", 4, [], cast(sz=1), FORMDEP, ZONE, NONE,
           power(1, 10, 1), flat(10), [form_input()], subgroup="elemi", source=P10A),
]
NAT = {"kind": "naturalMaterialMagic"}
KG, HKG, G = 1000, 500, 1
def rates(i, ii, iii):
    def r(x):
        return None if x is None else {"mp": x[0], "massG": x[1]}
    return {"I": r(i), "II": r(ii), "III": r(iii)}
def nat(id, name, tier, c, d, rt, effect=None, source=P10B):
    return mosaic(id, name, "anyagi", tier, [], c, d, ZONE, NAT, None,
                  {"pattern": "compositionPerMass", "rounding": None, "rates": rt},
                  [{"id": "composition", "label": "Összetétel", "type": "composition"}],
                  subgroup="termeszetes", effect=effect, source=source)
natural = [
    nat("anyagi-term-atformazas", "Átformázás", 2, cast(sz=4), DUR(kor=2), rates((1, KG), (1, HKG), (1, G)),
        "Changes shape and colour; new object of exactly the same weight; reverts when the spell ends."),
    nat("anyagi-term-atalakitas", "Átalakítás", 2, cast(sz=6), DUR(kor=2), rates((1, KG), (1, HKG), (1, G)),
        "Changes quality within the group; state and shape stay; MP from the starting weight; reverts when the spell ends."),
    nat("anyagi-term-teremtes", "Teremtés", 3, cast(sz=3), ONEOFF, rates((5, KG), (5, HKG), (5, G))),
    nat("anyagi-term-dezintegracio", "Dezintegráció", 2, cast(kor=1), ONEOFF, rates((3, KG), (3, HKG), (3, G))),
    nat("anyagi-term-atvaltoztatas", "Átváltoztatás", 3, cast(kor=2), DUR(kor=2), rates((2, KG), None, None)),
    nat("anyagi-term-visszavaltoztatas", "Visszaváltoztatás", 3, cast(kor=2), DUR(kor=2), rates((1, KG), None, None),
        "Recreates from the soul."),
    nat("anyagi-term-transzmutacio", "Transzmutáció", 3, cast(kor=10), DUR(kor=2), rates((2, KG), (2, HKG), (2, G))),
    mosaic("anyagi-term-legiesseg", "Légiesség", "anyagi", 3, ["cost"], cast(sz=2), DUR(perc=1), ZONE, NAT, None,
           {"pattern": "compositionGrid", "rows": ["I", "II", "III"], "columns": ["I", "I+II", "I+II+III"],
            "rates": None},
           [{"id": "composition", "label": "Összetétel", "type": "composition"},
            {"id": "passThrough", "label": "Áthatolás", "type": "choice", "values": ["I", "I+II", "I+II+III"]}],
           subgroup="termeszetes", source=P10B),
    nat("anyagi-term-lathatatlansag", "Láthatatlanság", 3, cast(sz=1), DUR(kor=1),
        rates((1, 10 * KG), (1, 5 * KG), (1, 10 * G))),
    nat("anyagi-term-telekinezis", "Telekinézis", 2, cast(sz=2), DUR(kor=2),
        rates((1, 5 * KG), (1, 5 * KG), (1, 5 * KG))),
    nat("anyagi-term-homerseklet-valtoztatas", "Hőmérséklet változtatás", 4, cast(sz=4), DUR(perc=1),
        rates((1, 10 * KG), (1, 5 * KG), (1, 10 * G))),
    nat("anyagi-term-halmazallapotvaltas", "Halmazállapotváltás", 4, cast(sz=6), DUR(perc=1),
        rates((1, KG), (1, HKG), (1, G))),
]
anyagi = elemi + natural

# ---------- forms (Part 10a) ----------
# Descriptions of the forms (user's Hungarian text "Hat iskola forma", chunk 4, approved 2026-10-17; typos corrected; "Időtartam" lines left out)
FORM_DESCRIPTIONS = {
    "nyil": (
        "Támadó varázslat. A varázsló ujjából egyetlenegy, a teremtett elemből való nyíl-lövedék tör elő (Zóna Varázsjel esetén a varázsjelből, "
        "Távolbahatásnál vagy szimpátiánál a semmiből). Ha eltalálja a megcélzott áldozatot, az elem Sp-jével egyenlő sebet okoz neki. "
        "(Természetesen csak akkor, ha az adott elem sebzi az illetőt.) Annak eldöntésére, hogy a varázsló eltalálja-e a kiszemelt célt, "
        "a harci rendszer célzás szabálya az irányadó.\n\n"
        "A Nyíl CÉ-je 30, félre nem üthető, de sebzése ellen érvényesül a páncélok SFÉ-je. E-je és sebzése egyenlő a teremtett elem E-jével és Sp-jével."
    ),
    "kard": (
        "Támadó varázslat. A varázsló a megteremtett elemmel körbeveheti egy tetszés szerinti formájú, egykezes kard pengéjét. "
        "Az elem felveszi a fegyver formáját – legyen az tőr vagy hosszúkard –, akár az aura az ember alakját, akit övez. "
        "A fegyver a markolatánál bárki megfoghatja, ám a penge többszörösen sebez. Természetesen csak akkor, ha a Kardba formázott elem ártalmas a megtámadottra "
        "(pl. a tűzkard éget).\n\n"
        "A kard E-je egyenlő a teremtett elem E-jével. Éppúgy viselkedik, akár egy közönséges fegyver: éppúgy harcolni, támadni szükséges vele – "
        "a harci rendszer szabályai szerint. Bárkinek átadható, forgatásához nem kell varázsló kasztúnak lenni. Sebzés csak sikeres támadás esetén éri az áldozatot. "
        "Ám ez nem csak annyi Sp, amennyi a fegyvereknél feltüntetett táblázatban szerepel, hanem még plusz annyi, amennyit a teremtett elem okoz. "
        "A túlütésre vonatkozó szabályok itt is igazak, és a páncélok SFÉ-je is érvényesül.\n\n"
        "A kard TÉ-je és VÉ-je ugyanannyi, mint egy közönséges fegyveré, és természetesen mozgatható. "
        "5 körig van jelen, addig használható, aztán nyomtalanul eltűnik a penge körül."
    ),
    "kitores": (
        "Támadó varázslat. A teremtett elemet a varázsló annyira összesűríti, hogy az végül szétrobban. A varázsló a zónáján belül bárhol meghatározhatja azt a pontot, "
        "de a bal kezéből elinduló varázslatnak egyenes vonalban el kell érnie addig a pontig. Amennyiben az elemnek megfelelő akadályba ütközne, akkor korábban robban fel. "
        "Tanácsos azonban figyelni arra, hogy az elég távol essen tőle, és a robbanás ne sebezze meg őt is!\n\n"
        "A kitörés által érintett terület nagyságát a varázsló nem képes befolyásolni, az egyedül a szétrobbanó elem mennyiségétől, azaz E-jétől függ. "
        "A kitörés epicentruma körüli egy negyedméter sugarú körben az E egyenlő az elem E-jével, attól távolodva pedig negyedméterenként 1-gyel csökken, "
        "míg végül nullává enyészik. Ezzel meg is határoztuk az érintett terület nagyságát: E/4 méter sugarú kör, ahol az E a Kitöréssé formázott elem E-je.\n\n"
        "A sebzés is ehhez hasonlóan alakul: középen maximális, majd az epicentrumtól távolodva az erősséggel együtt csökken."
    ),
    "szonyeg": (
        "Védő és támadó varázslat. A varázsló a megteremtett elemet az előbbiekhez képest jóval nagyobb területen osztja el. "
        "Ezt csak egyenletesen teheti, és a terület formáját sem határozhatja meg, ez minden esetben egy szabályos kör. Ezt a kört nevezzük szőnyegnek.\n\n"
        "A szőnyeg a föld szintjét 50 cm-es magasságban borítja be, és követi a terep egyenetlenségeit. "
        "A kör mérete a varázslótól függ, ám nem lehet kisebb egy negyed méternél, és nem lehet nagyobb, mint 0,25 × E méter. "
        "Középpontja a varázsló zónáján belül bárhova eshet.\n\n"
        "A szőnyeg E-je a teremtett elem E-jének és a szőnyeg méretének függvénye. Átlagérték, amit úgy kaphatunk meg, hogy az elem E-jét elosztjuk a szőnyeg "
        "méterben számított sugarával (24 E elem – 6 méter/4 E vagy 3 méter/8 E, vagy 2 méter/12 E). Tört érték esetén lefelé kell kerekíteni.\n\n"
        "A szőnyeg félkörönkénti sebzése a szőnyeg E-jével arányos, és nem a teremtett elemével.\n\n"
        "A szőnyegbe lépő lények méretüktől függően vesztenek Sp-t félkörönként.\n\n"
        "3 körig marad együtt, azután eloszlik."
    ),
}
def form(id, name, tier, dur, regen=None):
    # regen: the regenerációs idő of the user's form table as text ("-" in the table is None); nothing calculates with it
    return {"id": id, "name": name, "tier": tier, "duration": dur, "regeneration": regen,
            "description": FORM_DESCRIPTIONS.get(id), "source": P10A}
forms = [
    form("nyil", "Nyíl", 2, ONEOFF), form("kitores", "Kitörés", 2, ONEOFF),
    form("fal", "Fal", 2, DUR(kor=5), "0,5 szegmens"), form("szonyeg", "Szőnyeg", 2, DUR(kor=3), "0,5 szegmens"),
    form("kard", "Kard", 2, DUR(kor=5), "5 kör"), form("aura", "Aura", 2, DUR(kor=2), "5 kör"),
    form("sator", "Sátor", 3, DUR(kor=3), "0,5 szegmens"), form("kupola", "Kupola", 3, DUR(kor=3)),
    form("eso", "Eső", 3, DUR(kor=3), "0,5 szegmens"),
    form("gyuru", "Gyűrű", 4, DUR(kor=3), "0,5 szegmens"),
    form("labda", "Labda", 4, ONEOFF),
]
groups = [
    {"id": "I", "name": "I. csoport", "examples": ["everything else non-magical"], "source": P10B},
    {"id": "II", "name": "II. csoport",
     "examples": ["base metals (copper, tin, iron...)", "ores with over 10% content"], "source": P10B},
    {"id": "III", "name": "III. csoport",
     "examples": ["aryan", "silver", "platinum", "alloys with at least 10% of them",
                  "acids", "bases", "poisons"], "source": P10B},
]

for fname, data in [("alapvarazslatok.json", alap), ("ter.json", ter), ("mental.json", mental),
                    ("asztral.json", asztral), ("ido.json", ido), ("nekromancia.json", nekro),
                    ("anyagi.json", anyagi), ("forms.json", forms), ("material_groups.json", groups)]:
    write(fname, data)
print("written", sum(len(x) for x in [alap, ter, mental, asztral, ido, nekro, anyagi]), "mosaics")
