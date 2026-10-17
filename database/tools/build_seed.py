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
NAT_ATFORMAZAS = (
    "A varázslattal egy tárgy FORMÁJÁT és SZÍNÉT változtathatja meg a varázsló. 1 Mp-ért az anyagcsoporttól függően 1 kg (I. csoport), 0,5 kg (II. csoport), "
    "vagy 1g (III. csoport) anyag formázható át. A varázslatban egyfajta súly- és anyagmegmaradási törvény érvényesül: azaz: pontosan a kiindulásival azonos súlyú "
    "és minőségű tárgy keletkezik. Ha kevesebb, akkor az új tárgy kialakításához fel nem használt anyag felesleg formájában megmarad. "
    "Az alapanyagnál nagyobb súlyú tárgy nem formázható.\n\n"
    "Több különálló tárgy is szolgáltathat alapanyagot a megformázni kívánt egyetlen holmihoz. Ilyenkor azok összeolvadnak. "
    "Ha eleve több különböző minőségű anyagból álló tárgyat kívánunk átformázni, akkor a végtermékben is érvényesül azok egymáshoz viszonyított aránya.\n\n"
    "Ha a varázslat időtartama lejár, a tárgy visszaalakul eredeti formájára.\n\n"
    "Példa: sárga gömb helyett kék négyzet."
)
NAT_ATALAKITAS = (
    "A varázsló az anyag MINŐSÉGÉT változtathatja meg, de csak az adott anyagcsoporton belül. A tárgy halmazállapota, formája nem változik, színe is csak akkor, "
    "ha ezt az anyagminőség változása indokolttá teszi. Az átalakítás során az anyag térfogata mérvadó: pontosan ugyanakkora és ugyanolyan formájú marad, "
    "ám a súlya megváltozik. A varázslat 1 kg (I), 1/2 kg (II) vagy 1g (III) anyag átalakítására használható.\n\n"
    "Szigorú megkötés, hogy a varázsló csak olyan anyaggá képes bármit is átalakítani, amelynek összetételét ismeri. "
    "Pl. méreggé való átalakításhoz bírnia kell a Méregkeverés vagy az alkímia, netán a herbalizmus képzettséggel.\n\n"
    "A varázslattal élőlények is átalakíthatók bármilyen I. csoportba tartozó anyaggá (pl. kővé változtatás). Vagy éppen fordítva, sárból is gyúrható élőlény teste, "
    "ám az efféle teremtmény nem fogja magában hordozni az élet princípiumát, ezért nem számít élőlénynek csak homonkulusznak.\n\n"
    "Ha a varázslat időtartama lejár, a tárgy visszanyeri eredeti minőségét.\n\n"
    "Az Mp igény kiszámításánál a kiindulási tárgy súlya a mérvadó, nem a keletkezetté.\n\n"
    "Anyagcsoporton belül, pl. kővé változtatás."
)
NAT_LEGIESSEG = (
    "Az anyagtalanságnál a megemelhető súly az anyagtalanságnak megfelelő tömeg (az Mp-ből származó és nem az anyagtalanná tett tömeg) 10%."
)
NAT_MAGICAL_MATERIAL_HAT = "Mágikus anyagra is hat a láthatatlanság illetve a telekinézis."
natural = [
    nat("anyagi-term-atformazas", "Átformázás", 2, cast(sz=4), DUR(kor=2), rates((1, KG), (1, HKG), (1, G)),
        NAT_ATFORMAZAS),
    nat("anyagi-term-atalakitas", "Átalakítás", 2, cast(sz=6), DUR(kor=2), rates((1, KG), (1, HKG), (1, G)),
        NAT_ATALAKITAS),
    nat("anyagi-term-teremtes", "Teremtés", 3, cast(sz=3), ONEOFF, rates((5, KG), (5, HKG), (5, G)), "Nem volt – van."),
    nat("anyagi-term-dezintegracio", "Dezintegráció", 2, cast(kor=1), ONEOFF, rates((3, KG), (3, HKG), (3, G)), "Volt – nincs."),
    nat("anyagi-term-atvaltoztatas", "Átváltoztatás", 3, cast(kor=2), DUR(kor=2), rates((2, KG), None, None), "Példa: macskát békává."),
    nat("anyagi-term-visszavaltoztatas", "Visszaváltoztatás", 3, cast(kor=2), DUR(kor=2), rates((1, KG), None, None),
        "Lélek alapján újra alkotja."),
    nat("anyagi-term-transzmutacio", "Transzmutáció", 3, cast(kor=10), DUR(kor=2), rates((2, KG), (2, HKG), (2, G)),
        "Példa: anyagcsoportok között – követ arannyá."),
    mosaic("anyagi-term-legiesseg", "Légiesség", "anyagi", 3, ["cost"], cast(sz=2), DUR(perc=1), ZONE, NAT, None,
           {"pattern": "compositionGrid", "rows": ["I", "II", "III"], "columns": ["I", "I+II", "I+II+III"],
            "rates": None},
           [{"id": "composition", "label": "Összetétel", "type": "composition"},
            {"id": "passThrough", "label": "Áthatolás", "type": "choice", "values": ["I", "I+II", "I+II+III"]}],
           subgroup="termeszetes", effect=NAT_LEGIESSEG, source=P10B),
    nat("anyagi-term-lathatatlansag", "Láthatatlanság", 3, cast(sz=1), DUR(kor=1),
        rates((1, 10 * KG), (1, 5 * KG), (1, 10 * G)), NAT_MAGICAL_MATERIAL_HAT),
    nat("anyagi-term-telekinezis", "Telekinézis", 2, cast(sz=2), DUR(kor=2),
        rates((1, 5 * KG), (1, 5 * KG), (1, 5 * KG)), NAT_MAGICAL_MATERIAL_HAT),
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
        "Ha a szőnyegbe ellentétes elem alkotta nyíl, kard, fal vagy aura kerül, azokkal nem az átlag E-je kel birokra, hanem a bele formázott elem összes E-je.\n\n"
        "3 körig marad együtt, azután eloszlik."
    ),
}
# Kard and Aura can be moved, every other form is stationary (user, 2026-10-17)
MOVABLE_FORMS = {"kard", "aura"}
def form(id, name, tier, dur, regen=None):
    # regen: the regenerációs idő of the user's form table as text ("-" in the table is None); nothing calculates with it
    return {"id": id, "name": name, "tier": tier, "duration": dur, "regeneration": regen,
            "description": FORM_DESCRIPTIONS.get(id), "movable": id in MOVABLE_FORMS, "source": P10A}
forms = [
    form("nyil", "Nyíl", 2, ONEOFF), form("kitores", "Kitörés", 2, ONEOFF),
    form("fal", "Fal", 2, DUR(kor=5), "0,5 szegmens"), form("szonyeg", "Szőnyeg", 2, DUR(kor=3), "0,5 szegmens"),
    form("kard", "Kard", 2, DUR(kor=5), "5 kör"), form("aura", "Aura", 2, DUR(kor=2), "5 kör"),
    form("sator", "Sátor", 3, DUR(kor=3), "0,5 szegmens"), form("kupola", "Kupola", 3, DUR(kor=3)),
    form("eso", "Eső", 3, DUR(kor=3), "0,5 szegmens"),
    form("gyuru", "Gyűrű", 4, DUR(kor=3), "0,5 szegmens"),
    form("labda", "Labda", 4, ONEOFF),
]
# ---------- Jelmágia (Part 14): school text and the five varázskörök (user's Hungarian text, approved 2026-10-17; typos corrected) ----------
P14 = "Part 14"
JEL_SCHOOL_DESCRIPTION = (
    "A világegyetem törvényszerűségeit nem csak szavakkal, hanem bizonyos különös vonalvezetésű jelekkel is meg lehet fogalmazni. "
    "Ezek a jelek képesek arra, hogy rabul ejtsék a mágikus energiákat, hogy átalakítsák, kisugározzák őket, akár a varázsló elméje. "
    "Általában nem többek, mint egyszerű végrehajtók, ám némelyikük a varázsló segítőjeként is közreműködik a varázslatok során, akár holmi egyszerű inas. "
    "A Jelmágia nagymesterei szinte minden már megismert mágikus hatást képesek rajzolatok formájában leírni.\n\n"
    "A mágia ezen formájának valaha élt legnagyobb ismerőjéről, Moress Nobren mágusról az a legenda terjedt el, hogy egy alkalommal fél esztendős munkával "
    "egy egész hatalmas terem padlóját, falait és mennyezetét teleróva olyan mágikus jelrendszert alkotott, amely önálló gondolkodásra is képes volt. "
    "Egyetlen kötetbe összefoglalt életművéből, a Magica Graphicából idézünk:\n\n"
    "A jelek mágikus formulák, a mágikus energia anyagba rögzített megnyilvánulásai. Meghatározó tulajdonságuk nem az anyag, amelyre írják őket, hanem a forma, "
    "a rajzolat vonalvezetése. A jelek tehát, bár nem bírnak anyagi minőséggel, mégis az anyag egy változatát jelentik, hiszen a forma, a külalak egyedül az "
    "Anyagi Világban meghatározó attribútum…\n\n"
    "… Az eltérő – legalábbis az eltérőnek tekinthető – formák, így rajzolatok száma végtelen, végtelen tehát a Jelmágia jeleinek száma is. "
    "Ezzel együtt végtelen az így megidézhető mágikus módszerek és hatások sokasága is.\n\n"
    "…A rajzolatok uralmat adnak a Világegyetem felett – nem különben és nem hitványabbak a mágikus igéknél és holmiknál. "
    "Talán csak annyiban különböznek, hogy bizonyos mágikus operációkhoz már elfeledtük a szavakat, míg a jelek reánk maradtak az elődöktől. "
    "Kutatjuk tartamukat, igyekszünk szétboncolni őket, hogy beléjük lássunk, egészen a vázat adó csontokig, akár a gyógyító teszi az emberi testtel. "
    "Állítom, semmit sem tudunk még alapvető titkairól…\n\n"
    "…A mágikus szempontból tökéletes rajzolatot a varázsló csak transzban képes megalkotni. Mivelhogy a Jelmágia nem pusztán rajzolatból áll, hanem abból a "
    "transzban végrehajtott műveletből is, amelynek során a varázsló lelkének lenyomatát, egy lényéből kipattanó szikrát is elhelyez a vonalak között.\n"
    "Ez az a parányi szikra, amelytől az életre kél …\n\n"
    "A jelmágia a mágikus tartamú rajzolatokat 5 csoportba sorolja: varázskörök, varázsjelek, szimbólumok, bélyegek, pentagrammák.\n\n"
    "Fő szabályok\n"
    "Minden rajzolat elkészítéséhez szükséges a transz, ami tíz perces meditációt és 1 pszi pont felhasználását igényli.\n"
    "Szimbólummágia 2. fok szükséges a felrajzolásukhoz, illetve egyes esetekben adott magasmágia ismerete.\n"
    "Minden rajzolat elveszíti mágikus tartalmát, ha vonalai megsérülnek.\n"
    "A jelmágiára – kevés kivétellel – nem alkalmazható Időmágia, Térmágia és Szimpatikus mágia!"
)

CIRCLE_TABLE = (
    "Hatás – Magasmágia – Mp/E:\n"
    "Őstűz, Ősvíz, Ősföld, Őslevegő – Anyagi mágia 2. fok – 3\n"
    "Elemi erő – Anyagi mágia 2. fok – 3\n"
    "Természetes anyagi mágia – Anyagi mágia 2. fok – 3\n"
    "Asztrálmágia – Asztrálmágia 2. fok – 3\n"
    "Térmágia – Térmágia 2. fok – 3\n"
    "Időmágia – Időmágia 2. fok – 3\n"
    "Mentálmágia – Mentálmágia 2. fok – 3\n"
    "Szimpatikus mágia – Szimpatikus mágia 2. fok – 3"
)
CREATURE_TABLE = (
    "Lény hovatartozása – Mp:\n"
    "félhalottak – 20\n"
    "kísértetek és lelkek – 30\n"
    "alsó- és közrendű démonok – 40\n"
    "démonurak és -hercegek – 70"
)
ONE_MOSAIC = ("A varázsköröknek nem kell a teljes varázslat E-jével megbirkóznia, irányulhatnak annak egyetlen mozaikja ellen is. "
              "Ilyenkor a körön {where} csak az adott mozaik nem érvényesül, a varázslatba foglalt többi igen.")

JEL_BEBORTONOZO = (
    "A bebörtönzésre használatos varázskör a bezárt lényt megakadályozza abban, hogy a belső körívből kitörjön.\n\n"
    "A bebörtönző varázskörök bizonyos lényeknek a belső körbe történő bezárására használatosak. Ez azt jelenti, hogy az adott lény képtelen átlépni a belső kör vonalát, "
    "és kijutni azon belülről. Egyébként minden szempontból hasonlóak a Kirekesztő Varázskörökhöz, az Mp igényük meghatározásában az ott említett táblázat irányadó.\n\n"
    "A varázskör az összes jelmágikus rajzolathoz hasonlóan addig fejti ki hatását, amíg vonalai sértetlenek. Maga a kiszemelt lény képtelen arra, hogy megtörje a varázskör vonalait. "
    "Még mágikus úton sem: ez irányú varázslatai létre sem jönnek. Természetesen szép szóval, fenyegetéssel, Asztrál vagy Mentál mágiával rávehet erre bárkit.\n\n"
    + CREATURE_TABLE
)
JEL_KIREKESZTO = (
    "A kirekesztő varázskör lehetetlenné teszi, hogy a teremtmény, aki ellen alkották, a külső körív vonalát átlépje.\n\n"
    "A kirekesztő varázskörök arra használatosak, hogy bizonyos nem természeti eredetű lényeket visszatartsanak a külső köríven belüli területekről. "
    "Ez úgy történik, hogy az adott lény a mágikus hatások miatt képtelen átlépni a körívet, ám varázslatai átjutnak azon. "
    "Az, hogy egy efféle varázskör mennyi Mp-be kerül, attól függ, hogy milyen lény távoltartására készült. Erről a következő táblázat tudósít.\n\n"
    + CREATURE_TABLE + "\n\n"
    "A kirekesztő varázskör az összes jelmágikus rajzolathoz hasonlóan addig fejti ki hatását, amíg vonalai sértetlenek. Maga a kiszemelt lény képtelen arra, hogy megtörje a varázskör vonalait. "
    "Még mágikus úton sem: ez irányú varázslatai létre sem jönnek. Természetesen szép szóval, fenyegetéssel, Asztrál vagy Mentál mágiával rávehet erre bárkit."
)
JEL_KORBEZARO = (
    "A körbezáró varázskör a hatást éppen hogy a belső kör vonalán belülre korlátozza.\n\n"
    "A körbezáró varázskörök a védő varázskörökhöz hasonlóan működnek, azzal a különbséggel, hogy a hatást, ami ellen készítették őket, nem a külső körön kívülre korlátozzák, "
    "hanem bezárják a belső körön belülre – megakadályozva, hogy azon túl is érvényesüljön. A hatásból annyi jut ki, amennyi a védő varázskörnél említett módon ott belülre kerülhet. "
    "Azaz: a kijutó E egyenlő a hatás E-je mínusz a varázskör E-je.\n\n"
    "Környezeti hatások erőssége minden esetben 15 E-nek számít.\n\n"
    "Arról, hogy milyen típusú körbezáró varázskörök léteznek és ezek 1 E-s erősítéséhez mennyi Mp szükséges, a Védő varázsköröknél ismertetett táblázat tudósít.\n\n"
    + CIRCLE_TABLE + "\n\n" + ONE_MOSAIC.format(where="kívül")
)
JEL_VEDO = (
    "A védő varázskör bizonyos mágikus hatásoknak vet gátat, azaz a megjelölt hatás nem juthat be a külső köríven belülre.\n\n"
    "A védő varázskörök arra használatosak, hogy egy adott mágikus hatástól megvédjék a varázskör külső körén belül eső területet (csak varázslatokat gyengítenek).\n\n"
    "Mágikus hatás esetén: a védett területen, ha a varázskör E-je nagyobb a mágikus hatás E-jénél, a hatás nem érvényesül. Ha a varázskör E-je kisebb, akkor a hatásból csak annyi jut át "
    "a védő varázskörön, amennyivel E-je több, mint a varázsköré (bejutott E = hatás E – varázskör E). Ebben az esetben a védett területen belül az adott hatás csak bejutott E-jének függvényében érvényesül.\n\n"
    "Környezeti hatások erőssége minden esetben 15 E-nek számít.\n\n"
    "Arról, hogy milyen típusú varázskörök léteznek, és ezek 1 E-s erősítéséhez mennyi Mp szükséges, a következő táblázat tudósít:\n\n"
    + CIRCLE_TABLE + "\n\n" + ONE_MOSAIC.format(where="belül")
)
JEL_VEGREHAJTO = (
    "A végrehajtó varázskörbe bármilyen mozaikokból felépített varázslat beleírható, és csak akkor aktivizálódik, ha valaki a varázskörbe lép. "
    "Fontos megkötés, hogy a varázslat – bármekkora is legyen eredeti hatóterülete – csak a varázskör belső körívén belül eső területre, illetve az ott tartózkodókra hat, "
    "ott azonban válogatás nélkül mindenkire.\n\n"
    "A varázskör felrajzolása 40 mp, plusz még annyi, amennyibe a beleírt varázslat kerül. A beleírt varázslatot csak egyszer képes végrehajtani, "
    "de a kimerült varázskör annyi mannapontért, amennyi a beleírt varázslat mp igénye, újra tölthető. "
    "Ha vonalai megtörnek, a varázslat kiszabadul és kifejti hatását, de a rajzolat többé nem használható."
)

UNTIL_BROKEN = {"kind": "untilBroken"}
DRAWING_RANGE = {"kind": "drawing"}
CREATURE_CLASSES = {"felhalott": ("Félhalottak", 20), "kisertet": ("Kísértetek és lelkek", 30),
                    "demon": ("Alsó- és közrendű démonok", 40), "demonur": ("Démonurak és -hercegek", 70)}
EFFECT_TYPES = {  # id: (label, [(school, tier)])
    "ostuz": ("Ős tűz", [("anyagi", 2)]), "osviz": ("Ős víz", [("anyagi", 2)]), "osfold": ("Ős föld", [("anyagi", 2)]),
    "oslevego": ("Ős levegő", [("anyagi", 2)]), "elemiero": ("Elemi erő", [("anyagi", 2)]),
    "termeszetesanyagi": ("Természetes anyagi mágia", [("anyagi", 2)]), "asztral": ("Asztrálmágia", [("asztral", 2)]),
    "ter": ("Térmágia", [("ter", 2)]), "ido": ("Időmágia", [("ido", 2)]), "mental": ("Mentálmágia", [("mental", 2)]),
    "szimpatikus": ("Szimpatikus mágia", [("szimpatikus", 2)]),
}

def creature_input():
    return {"id": "creatureClass", "label": "Lény hovatartozása", "type": "choice", "values": list(CREATURE_CLASSES),
            "labels": {k: v[0] for k, v in CREATURE_CLASSES.items()}}

def effect_type_input():
    return {"id": "effectType", "label": "Hatás típusa", "type": "choice", "values": list(EFFECT_TYPES),
            "labels": {k: v[0] for k, v in EFFECT_TYPES.items()},
            "requires": {k: [{"school": s, "tier": t} for s, t in v[1]] for k, v in EFFECT_TYPES.items()}}

def circle(id, name, tier, minutes, effect, pw, cost, inputs, requires=None):
    d = mosaic(id, name, "jelmagia", tier, [], cast("drawing", perc=minutes), UNTIL_BROKEN, DRAWING_RANGE, {"kind": "none"},
               pw, cost, inputs, subgroup="varazskorok", effect=effect, source=P14)
    if requires:
        d["requires"] = requires
    return d

INFINITE = {"unit": "E", "baseE": None, "step": None, "infinite": True}
BY_CLASS = {"pattern": "perChoice", "input": "creatureClass", "mpByValue": {k: v[1] for k, v in CREATURE_CLASSES.items()}}
jel = [
    circle("jel-varazskor-bebortonozo", "Bebörtönöző varázskör", 2, 25, JEL_BEBORTONOZO, INFINITE, BY_CLASS, [creature_input()],
           [{"school": "nekromancia", "tier": 2}]),
    circle("jel-varazskor-kirekeszto", "Kirekesztő varázskör", 2, 25, JEL_KIREKESZTO, INFINITE, BY_CLASS, [creature_input()],
           [{"school": "nekromancia", "tier": 2}]),
    circle("jel-varazskor-korbezaro", "Körbezáró varázskör", 2, 20, JEL_KORBEZARO, power(1, 3, 1), flat(3), [effect_type_input()]),
    circle("jel-varazskor-vedo", "Védő varázskör", 2, 20, JEL_VEDO, power(1, 3, 1), flat(3), [effect_type_input()]),
    # wrapper: 40 MP plus the MP of the other mosaics of the spell; its 30 perc include their time
    circle("jel-varazskor-vegrehajto", "Végrehajtó varázskör", 3, 30, JEL_VEGREHAJTO, None,
           {"pattern": "wrapper", "baseMp": 40, "factor": 1}, []),
]
# ---------- Jelmágia, the signs, symbols, stamps and pentagrams (lines 6 to 27; user's Hungarian text, approved 2026-10-17; typos corrected) ----------
SIGN = "Soha nem nagyobb egy ezüstpénznél."
SYMBOL_INTRO = "Embermagasságnál nagyobb mágikus jel vagy felirat."
STAMP_BLOCK = ("Néhány centiméter átmérőjű jel, a megbélyegzett testén. Hatásukat arra fejtik ki, akinek a testén a varázsló elhelyezi őket. "
               "Az alany egészen addig áldozata a bélyeg hordozta mágiának, ameddig az el nem távolíttatik a testéről. "
               "A bélyegek oly erősen sugározzák a beléjük foglalt mágikus hatást, hogy velük szemben mágiaellenállásnak helye nincs.")

T_VALTOZAS_VJ = SIGN + "\n\n" + (
    "Minden a Természeti Anyag Mágiájának fejezete alá tartozó hatás leírható Változás Varázsjellel. A varázsjelet arra a tárgyra kell rajzolni, amelyet a varázsjellel a varázsló megváltoztatni kíván, "
    "hatása ellen nem véd a Személyes Aura sem. Az adott hatás nem ér véget a mozaiknál feltüntetett időtartam lejártával, hanem mindaddig érvényesül, ameddig a tárgyon a varázsjel töretlen. "
    "A Változás Varázsjel megrajzolása háromszor annyi Mp-be kerül, mint a hasonló varázslat mozaik, ám erőssége ugyanannyi marad.")
T_ZONA_VJ = SIGN + "\n\n" + (
    "A zóna varázsjel körüli 20 láb sugarú gömb a varázsló zónájának számít, akárcsak a teste körüli hasonló méretű terület. Vagyis, ahol ezt a varázsjelet a varázsló elhelyezi, ott újabb Zóna jön létre, "
    "amelyen belül majdnem úgy varázsolhat távolbahatás nélkül is, ahogyan azt a teste körüli zónában teheti. A különbség csupán annyi, hogy a zóna varázsjelen keresztül végrehajtott varázslatok "
    "varázslási ideje minden esetben +1 körrel nő, s az eredeti mannapont igényéhez +5 mp járul.\n\n"
    "A varázslók éppen ezért gyakran helyeznek el zóna varázsjelet olyan helyeken, ahová később varázsolni kívánnak. Nem egyszer apróbb tárgyakra írják, és azokat szolgákkal vitetik el a világ azon "
    "távoli pontjaira, ahová mágikus befolyásukat kiterjeszteni készülnek.\n\n"
    "A Zóna varázsjel másik felmérhetetlenül hasznos tulajdonsága, hogy lehetővé teszi a varázsló számára, hogy arra a helyre teleportáljon, vagy kaput nyisson, ahol a jel található.\n\n"
    "Minden zóna varázsjel egyéni, azaz minden varázslónak saját jele van – ami az avatott szem fel is ismerheti. Minden varázsló csak saját zóna varázsjelét használhatja távolba varázslásra és térmágiára.\n\n"
    "Hátránya, hogy a tárgy szimpátia viszonyban áll a varázslóval, így ellenséges szándékú varázsló kezében veszélyessé lehet.")
T_SZEMELYES_AURA_VJ = SIGN + "\n\n" + (
    "A tárgy, amelyen a varázsló elhelyezi a varázsjelet, Személyes Aurájának védelme alá kerül. Ez azt jelenti, hogy attól kezdve az aura védelme az adott tárgyra is kiterjed. "
    "Természetesen a varázsló nem csak saját, hanem valaki más Személyes Aurájának is részévé tehet ily módon bármely holmit, ehhez az illetőnek magának kell a varázsjelbe mágikus rúnákkal beleírnia a nevét. "
    "Ami persze okozhat némi nehézséget, hiszen a rúnák pontos leírását egyedül a varázslók tanulják. Nem varázsló karakternek ahhoz, hogy saját nevét rúnákkal képes legyen hibátlanul leírni, "
    "3 hétig minden nap legalább két órát kell gyakorolnia.")
T_MAGIKUS_AURA_VJ = SIGN + "\n\n" + (
    "A tárgy, amelyre a varázsló felírja a Mágikus Aura Varázsjelét, attól kezdve mágikusnak számít, miáltal nem hat rá a Természeti Anyag Mágiája. "
    "Rúnamágiával készített varázstárgyaknál a Mágikus Aura Varázsjel terjeszti ki a rúna mágikus hatását az adott tárgyra. (ld. Varázstárgyak)")
T_LELEKCSAPDA_VJ = SIGN + "\n\nLásd: Nekromancia (kell hozzá Nekromancia 2. fok)."
T_KISUGARZO_VJ = SIGN + "\n\n" + (
    "A végrehajtó varázskör ikertestvére; hatása és működése is ahhoz hasonló. Két válfaja ismeretes: az elsőnél a beleírt varázslat arra hat, aki a varázsjelet megérinti, a másodiknál a varázsjel előtt "
    "karnyújtásnyira (1 láb) távolságon belül elhaladókra – válogatás nélkül. A hatás megnyilvánulása és az érintettek elleni hatékonysága a varázsjelbe írt varázslat természetétől és erősségétől függ "
    "(pl. a kicsapó tűznyíl nem feltétlenül találja el az áldozatot).\n\n"
    "Az első változat 20 mp, plusz amibe a varázslat kerül, a második 10 mp-vel több.")
T_SZIMPATIA_VJ = SIGN + "\n\n" + (
    "Jelpár, amely tárgy-tárgy szimpátiát hoz létre két tárgy között, melyekre írják őket. Ha bármelyik rajzolat megsérül, a szimpátia azonnal megszűnik.")
T_KEMSZEM_VJ = SIGN + "\n\n" + (
    "Jelpár, amely egymással szimpátia viszonyban álló varázsjelből és bélyegből áll. Az első bárhol, bármilyen tárgyon elhelyezhető, míg a második arra a személyre írandó, aki a varázsjellel látni fog.\n\n"
    "A bélyeg viselője – ha akarja – képes a varázsjellel úgy látni, mintha az saját szeme lenne. Ilyenkor saját szemével természetesen nem lát. Ha valaki úgy rajzolja fel a bélyeget, hogy az egyszerre "
    "több varázsjellel is kapcsolatban áll, tetszése szerint válogathat azok között, egyikről a másikra váltva egymás után több helyszínt is szemmel tarthat. Fontos megjegyezni, hogy a kémszem nem forgatható, "
    "azaz egyetlen, meghatározott irányba mered. Forgatásáról, mozgatásáról más varázslattal, netán kahrei mechanikával szükséges gondoskodni.\n\n"
    "A rajzolat felírásának mp igénye a bélyeg és a varázsjel maximális távolságától függ. Ha ennél távolabb kerülnek egymástól, a szimpátia viszony szünetel, így a kémszem ideiglenesen megvakul, "
    "s csak akkor használható újból, ha ismét hatótávba kerül.\n\n"
    "Hatótáv – Mp igény:\n0–10 láb – 55 mp\n11–39 láb – 60 mp\n40–99 láb – 68 mp\n100–999 láb – 75 mp\nEnnél nagyobb távolságra nem alkalmazható.\n\n"
    "A fent feltüntetett Mana-pontok a teljes jelpárra vonatkoznak; a szimpátia viszony miatt közös transzban kell megrajzolni őket. Több varázsjel egy bélyeghez csatlakoztatása esetén a bélyeget is minden "
    "alkalommal újra kell rajzolni, bár vonalai tökéletesen fedhetik a korábban felírt bélyegekét. Az újabb és újabb jelpárok természetesen már újabb és újabb transzban is megalkothatóak – így minden pár "
    "felrajzolása után nyílik mód mana-pont gyűjtésre.\n\n"
    "A kémszem addig működőképes, ameddig mindkét rajzolat töretlen.")
T_TERGAT_VJ = SIGN + "\n\n" + (
    "A Térgát varázsjel Ynev várainak, palotáinak, erősségeinek mindennapos kelléke, a rúnatornyok leggyakoribb jele. Az adott területet belül gátat szab minden térmágiának: a területről és a területre "
    "teleportálni, távolbahatni, térkaput nyitni lehetetlen, hacsak a térmágia erőssége meg nem haladja a varázsjelét. Alapterület, amiben gátol: egy 1 méter sugarú gömb, amit méterenként 1 MP-ért lehet növelni.")
T_TERCSAPDA_VJ = SIGN + "\n\n" + (
    "A Tércsapda varázsjel a Térgát varázsjel különleges változata. Hatóterületén nem gátat szab a térmágiának, hanem a varázsjelhez vonzza. "
    "Minden térmágikus hatás a varázsjelen ill. annak közvetlen szomszédságában jön létre.")
T_TAPADAS_VJ = SIGN + "\n\n" + (
    "Varázsjel pár, amelyet két különálló tárgyon kell elhelyezni: a két jel tükörképe egymásnak, pontosan egymáshoz illesztve összetapadnak. Minél erősebb a beléjük foglalt mágia, annál nehezebben "
    "választhatóak szét, azaz annál nagyobb a súly, amelynek megtartására alkalmasak. Szét lehet választani őket erővel, de újra összetapadnak, ha a jelek töretlenek.")
T_FORGAS_VJ = SIGN + "\n\n" + (
    "A tapadáshoz hasonló jel pár, ám az összeillesztett tárgyak, a rögzítetlen vagy kisebb súlyú, a varázsjelek képezte tengely körül forognak. A jelek kicsik, de a tárgy lehet nagyobb; a forgás tengelye ott van, "
    "ahol a két jel találkozik. Egy tenyérnyi korong 5 szegmens alatt tesz meg egy teljes kört; ha a teljes mp igényt megduplázod, a sebesség duplázódik, háromszorosával triplázódik stb.")

T_SYMBOL_COMMON = (
    "Az Asztrál és Mentál mágiáról szóló fejezetben leírt összes varázslat felrajzolható ebben a formában. Hatásukat arra fejtik ki, aki megpillantja őket – ám az illető csak akkor válik a varázslat áldozatává, "
    "ha Mágia ellenállását elvéteti. Egy szimbólum elkészítése annyi mp-be kerül, amennyibe a belefoglalt varázslat.")
T_ASZTRAL_SZIMBOLUM = SYMBOL_INTRO + "\n\n" + T_SYMBOL_COMMON + "\n\n" + (
    "Az asztrálszimbólumok akkor fejtik ki hatásukat, amikor az áldozat megpillantja őket. Szimbólumként bármely az asztrál fejezetben leírt varázslat felírható. Ennek Mp igénye annyi, amennyi az azonos "
    "asztrál varázslaté és erőssége, valamint időtartama is azével egyenlő.\n\n"
    "Az asztrál szimbólumok ellen az áldozat dobhat asztrális mágiaellenállást, melynek sikere esetén mentesül a szimbólum hatása alól.")
T_MENTAL_SZIMBOLUM = SYMBOL_INTRO + "\n\n" + T_SYMBOL_COMMON + "\n\n" + (
    "A mentál szimbólumok az asztrál szimbólumokhoz hasonlóak. Hatásukat arra fejtik ki, aki megpillantja őket. Szimbólumként bármely mentál varázslat felírható, ennek mp igénye annyi, amennyi az azonos "
    "hatású mentál varázslaté, ugyanez igaz az erősségre és időtartamra is.\n\n"
    "A mentálszimbólumok ellen az áldozat dobhat mentális ellenállást, melynek sikere esetén mentesül a szimbólum hatása alól.")

T_ASZTRAL_BELYEG = STAMP_BLOCK + "\n\n" + (
    "Az asztrálbélyegek alkalmasak arra, hogy viselőjüket az adott asztrálmágia hatása alá helyezzék. Transzban tudja az áldozat testére rajzolni. Annyi manna, amennyibe az adott varázslat kerülne 15 E erősség "
    "esetén. Viszont nincs ellene ellenállás. Az áldozat adott esetben letörölheti magáról.\n\n"
    "Asztrálbélyeg esetén a jeleknek tartalmazniuk kell az érzelem irányát is.")
T_MENTAL_BELYEG = STAMP_BLOCK + "\n\n" + (
    "A mentálbélyegek viselőjüket az adott mentálmágia hatása alá helyezik. Transzban az áldozat testére kell festeni, de letörölhető. Annyi manna, amennyibe az adott mentál varázslat 20 E erősség esetén kerülne.\n\n"
    "A mentális operációk közül a kábítás és bénítás mentálbélyegben való elhelyezése nem jelent különösebb nehézséget, ám az olvasással és az írással merőben más a helyzet. Hiszen ilyenkor a varázsló és az áldozat "
    "agya között folyamatosan fennálló kapcsolatra van szükség, ami megköveteli, hogy a varázsló testén is legyen egy hasonló, csak néhány vonásában különböző bélyeg, továbbá, hogy az olvasás, írás ideje alatt "
    "a varázsló a kapcsolatra összpontosítson.")
T_VALTOZAS_BELYEG = STAMP_BLOCK + "\n\nA Változás varázsjel, amelyet személyre, élőlényre írnak. Ereje nem végtelen, a mozaik erejével egyezik meg, háromszoros mannaáron."
T_LELEKCSAPDA_BELYEG = STAMP_BLOCK + "\n\nLásd: Nekromancia."
T_HALOTTMOZGATAS_BELYEG = STAMP_BLOCK + "\n\nLásd: Nekromancia."
T_FELRUHAZAS_BELYEG = STAMP_BLOCK + "\n\nHárom fajtája ismeretes: értelemmel, tudattal, személyiséggel felruházás, ahogy a boszorkánymesteri varázslatoknál érvényesül."
T_PENTAGRAMMA = ("A pentagramma ötágú csillag, melyet a varázsló olykor csúcsait érintő körbe foglal. Méretére jellemző, hogy ennek a körnek – akár létezik, akár csak képzeletbeli – "
                 "legalább egy lépésnyi átmérőjűnek kell lennie.")

WRAP_NATURAL = [{"school": "anyagi", "subgroup": "termeszetes", "label": "Természetes anyagi mágia"}]
WRAP_ASZTRAL = [{"school": "asztral", "label": "Asztrál mágia"}]
WRAP_MENTAL = [{"school": "mental", "label": "Mentál mágia"}]

def sign(id, name, subgroup, text, cast_t, cost, pw=None, inputs=None, requires=None, rng=None, res=None, dur=None, missing=()):
    d = mosaic(id, name, "jelmagia", 2, list(missing), cast_t, dur or UNTIL_BROKEN, rng or DRAWING_RANGE, res or {"kind": "none"},
               pw, cost, inputs or [], subgroup=subgroup, effect=text, source=P14)
    if requires:
        d["requires"] = requires
    return d

def number_input(id, label, unit, integer=False, minimum=None):
    d = {"id": id, "label": label, "type": "number", "unit": unit}
    if integer:
        d["integer"] = True
    if minimum is not None:
        d["min"] = minimum
    return d

def req(school, tier):
    return [{"school": school, "tier": tier}]

AURA_TYPES = {"asztral": ("Asztrálmágia", "asztral"), "mental": ("Mentálmágia", "mental")}
aura_input = {"id": "effectType", "label": "Hatás típusa", "type": "choice", "values": list(AURA_TYPES),
              "labels": {k: v[0] for k, v in AURA_TYPES.items()},
              "requires": {k: req(v[1], 2) for k, v in AURA_TYPES.items()}}
variant_input = {"id": "variant", "label": "Válfaj", "type": "choice", "values": ["touch", "pass"],
                 "labels": {"touch": "Megérintőre hat (20 MP)", "pass": "Elhaladókra hat, 1 láb (30 MP)"}}
felruhazas_input = {"id": "kind", "label": "Fajta", "type": "choice", "values": ["ertelem", "tudat", "szemelyiseg"],
                    "labels": {"ertelem": "Értelemmel felruházás", "tudat": "Tudattal felruházás", "szemelyiseg": "Személyiséggel felruházás"}}
RADIUS = lambda: number_input("radiusM", "Sugár", "m", integer=True, minimum=1)
SPHERE_COST = lambda base: {"pattern": "linear", "input": "radiusM", "baseMp": base, "freeUnits": 1, "mpPerUnit": 1}
PER_5KG = {"mp": 1, "massG": 5000}
jel += [
    # ---- varázsjelek (lines 6 to 17)
    sign("jel-varazsjel-valtozas", "Változás varázsjel", "varazsjelek", T_VALTOZAS_VJ, cast("drawing", perc=5),
         {"pattern": "wrapper", "baseMp": 0, "factor": 3, "wraps": WRAP_NATURAL}, requires=req("anyagi", 2)),
    sign("jel-varazsjel-zona", "Zóna varázsjel", "varazsjelek", T_ZONA_VJ, cast("drawing", perc=5), flat(25)),
    sign("jel-varazsjel-szemelyes-aura", "Személyes aura varázsjel", "varazsjelek", T_SZEMELYES_AURA_VJ, cast("drawing", perc=5), flat(40), inputs=[aura_input]),
    sign("jel-varazsjel-magikus-aura", "Mágikus aura varázsjel", "varazsjelek", T_MAGIKUS_AURA_VJ, cast("drawing", perc=5), flat(33)),
    sign("jel-varazsjel-lelekcsapda", "Lélekcsapda varázsjel", "varazsjelek", T_LELEKCSAPDA_VJ, cast("drawing", perc=3), flat(35), requires=req("nekromancia", 2)),
    sign("jel-varazsjel-kisugarzo", "Kisugárzó varázsjel", "varazsjelek", T_KISUGARZO_VJ, cast("drawing", perc=3),
         {"pattern": "wrapper", "input": "variant", "baseMpByValue": {"touch": 20, "pass": 30}, "factor": 1}, inputs=[variant_input],
         rng={"kind": "byChoice", "input": "variant", "values": {"touch": {"kind": "touch"}, "pass": {"kind": "feet", "value": 1}}}),
    sign("jel-varazsjel-szimpatia", "Szimpátia varázsjel", "varazsjelek", T_SZIMPATIA_VJ, cast("drawing", perc=6), flat(20), pw=power(1), requires=req("szimpatikus", 2)),
    sign("jel-varazsjel-kemszem", "Kémszem varázsjel", "varazsjelek", T_KEMSZEM_VJ, cast("drawing", perc=10),
         {"pattern": "lookup", "input": "distanceFt",
          "table": [{"fromM": 0, "toM": 10, "mp": 55}, {"fromM": 11, "toM": 39, "mp": 60}, {"fromM": 40, "toM": 99, "mp": 68}, {"fromM": 100, "toM": 999, "mp": 75}]},
         pw=power(1), inputs=[number_input("distanceFt", "Legnagyobb távolság a jel és a bélyeg között", "láb", integer=True, minimum=0)],
         requires=req("szimpatikus", 2), rng={"kind": "byInput"}),
    sign("jel-varazsjel-tergat", "Térgát varázsjel", "varazsjelek", T_TERGAT_VJ, cast("drawing", perc=5), SPHERE_COST(3), pw=power(1, 3, 1),
         inputs=[RADIUS()], requires=req("ter", 2), rng={"kind": "byInput"}),
    sign("jel-varazsjel-tercsapda", "Tércsapda varázsjel", "varazsjelek", T_TERCSAPDA_VJ, cast("drawing", perc=5), SPHERE_COST(8), pw=power(1, 3, 1),
         inputs=[RADIUS()], requires=req("ter", 3), rng={"kind": "byInput"}),
    sign("jel-varazsjel-tapadas", "Tapadás varázsjel", "varazsjelek", T_TAPADAS_VJ, cast("drawing", kor=2),
         {"pattern": "perMass", "rate": PER_5KG}, pw=power(1, 1, 1), inputs=[number_input("weightKg", "Tartott súly", "kg")]),
    sign("jel-varazsjel-forgas", "Forgás varázsjel", "varazsjelek", T_FORGAS_VJ, cast("drawing", kor=2),
         {"pattern": "perMass", "rate": PER_5KG, "multiplierInput": "speed"}, pw=power(1, 1, 1),
         inputs=[number_input("weightKg", "Forgatott súly", "kg"), number_input("speed", "Sebesség (szorzó)", "×", integer=True, minimum=1)]),
    # ---- szimbólumok (lines 18, 19): the duration and the strength are those of the spell drawn in them
    sign("jel-szimbolum-asztral", "Asztrálszimbólum", "szimbolumok", T_ASZTRAL_SZIMBOLUM, cast("drawing", perc=30),
         {"pattern": "wrapper", "baseMp": 0, "factor": 1, "keepsDuration": True, "wraps": WRAP_ASZTRAL},
         rng={"kind": "sight"}, res=res_school("asztral"), dur=UNDEF),
    sign("jel-szimbolum-mental", "Mentálszimbólum", "szimbolumok", T_MENTAL_SZIMBOLUM, cast("drawing", perc=35),
         {"pattern": "wrapper", "baseMp": 0, "factor": 1, "keepsDuration": True, "wraps": WRAP_MENTAL},
         rng={"kind": "sight"}, res=res_school("mental"), dur=UNDEF),
    # ---- bélyegek (lines 20 to 25)
    sign("jel-belyeg-asztral", "Asztrálbélyeg", "belyegek", T_ASZTRAL_BELYEG, cast("drawing", perc=5),
         {"pattern": "wrapper", "baseMp": 0, "factor": 1, "innerE": 15, "wraps": WRAP_ASZTRAL}, pw=dict(INFINITE),
         requires=req("asztral", 2), rng={"kind": "body"}),
    sign("jel-belyeg-mental", "Mentálbélyeg", "belyegek", T_MENTAL_BELYEG, cast("drawing", perc=5),
         {"pattern": "wrapper", "baseMp": 0, "factor": 1, "innerE": 20, "wraps": WRAP_MENTAL}, pw=dict(INFINITE),
         requires=req("mental", 2), rng={"kind": "body"}),
    sign("jel-belyeg-valtozas", "Változás bélyege", "belyegek", T_VALTOZAS_BELYEG, cast("drawing", perc=5),
         {"pattern": "wrapper", "baseMp": 0, "factor": 3, "wraps": WRAP_NATURAL}, requires=req("anyagi", 3), rng={"kind": "body"}),
    sign("jel-belyeg-lelekcsapda", "Lélekcsapda bélyeg", "belyegek", T_LELEKCSAPDA_BELYEG, cast("drawing", perc=5), flat(39),
         requires=req("nekromancia", 2), rng={"kind": "body"}),
    sign("jel-belyeg-halottmozgatas", "Halottmozgatás bélyege", "belyegek", T_HALOTTMOZGATAS_BELYEG, cast("drawing", perc=5), flat(52),
         requires=req("nekromancia", 3), rng={"kind": "body"}),
    # the prices of the three kinds come from the boszorkánymester chapter (the user will give them later): incomplete
    sign("jel-belyeg-felruhazas", "Felruházás bélyege", "belyegek", T_FELRUHAZAS_BELYEG, cast("drawing", perc=5), UNDEF_COST, pw=dict(INFINITE),
         inputs=[felruhazas_input], requires=req("nekromancia", 3), rng={"kind": "body"}, missing=["cost"]),
    # ---- pentagrammák (lines 26, 27): only a name and a requirement in the source: incomplete
    sign("jel-pentagramma-idezo", "Idéző pentagramma", "pentagrammak", T_PENTAGRAMMA, UNDEF_CAST("drawing"), UNDEF_COST,
         requires=req("nekromancia", 2), rng={"kind": "pentagram"}, res=UNDEF, dur=UNDEF,
         missing=["cost", "castingTime", "duration", "resistance", "power"]),
    sign("jel-pentagramma-terkapu", "Térkapu pentagramma", "pentagrammak", T_PENTAGRAMMA, UNDEF_CAST("drawing"), UNDEF_COST,
         requires=req("ter", 3), rng={"kind": "pentagram"}, res=UNDEF, dur=UNDEF,
         missing=["cost", "castingTime", "duration", "resistance", "power"]),
]

schools_extra = [{"id": "jelmagia", "name": "Jelmágia", "hasTiers": True, "description": JEL_SCHOOL_DESCRIPTION}]

groups = [
    {"id": "I", "name": "I. csoport", "examples": ["Minden más nem mágikus anyag."], "source": P10B},
    {"id": "II", "name": "II. csoport",
     "examples": ["Nemtelen fémek, mint réz, ón, vas stb…", "Ércek, amikben 10%-nál nagyobb a tartalom."], "source": P10B},
    {"id": "III", "name": "III. csoport",
     "examples": ["Aryan, ezüst, platina és minden olyan ötvözet, amiben legalább 10%-ban megtalálhatóak.", "Savak, lúgok, mérgek."],
     "source": P10B},
]
# Texts that belong to no mosaic, form or group (id -> Hungarian text); the page shows them where they fit
reference_texts = [
    {"id": "materials.magical", "text": "Mágikus anyagok: Mithrill, abbitacél, drágakövek.", "source": P10B},
    {"id": "materials.touching", "text": "Lehet egyben kimondani varázslatot az érintkező tárgyakra.", "source": P10B},
]

for fname, data in [("alapvarazslatok.json", alap), ("ter.json", ter), ("mental.json", mental),
                    ("asztral.json", asztral), ("ido.json", ido), ("nekromancia.json", nekro),
                    ("anyagi.json", anyagi), ("forms.json", forms), ("material_groups.json", groups),
                    ("reference_texts.json", reference_texts),
                    ("jelmagia.json", jel), ("schools.json", schools_extra)]:
    write(fname, data)
print("written", sum(len(x) for x in [alap, ter, mental, asztral, ido, nekro, anyagi, jel]), "mosaics")
