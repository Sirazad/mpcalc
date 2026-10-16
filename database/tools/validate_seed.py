"""Validate seed/*.json against docs/mosaic-definition-format.md and print counts.
Run: .venv/bin/python tools/validate_seed.py   (exit code 1 if any error)
"""
import json
import os
import re
import sys
from collections import Counter, OrderedDict

SEED = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "seed")
SCHOOLS = ["alapvarazslatok", "anyagi", "asztral", "mental", "ter", "ido", "szimpatikus", "nekromancia"]
FILES = {"alapvarazslatok": "alapvarazslatok.json", "anyagi": "anyagi.json", "asztral": "asztral.json",
         "mental": "mental.json", "ter": "ter.json", "ido": "ido.json", "nekromancia": "nekromancia.json"}
STATES = ("complete", "partial", "incomplete")
UNITS = ("sz", "kor", "perc", "ora", "nap", "ev")
RANGES = ("zone", "self", "touch", "pentagram", "meters", "byInput", "undefined")
RESIST = ("none", "school", "concealment", "spellMp", "givenElement", "oppositeElement", "givenParaElement", "elementalForce", "armorSfe",
          "lifeForce", "naturalMaterialMagic", "undefined")
PATTERNS = ("flat", "perMass", "compositionPerMass", "compositionGrid", "lookup", "undefined")
INPUT_TYPES = ("choice", "number", "form", "composition", "text")
FIELDS = ["id", "name", "school", "tier", "status", "castingTime", "duration", "range", "resistance",
          "power", "cost", "inputs", "source"]
OPTIONAL = {"subgroup", "modifier", "options", "effect"}
TOP_FIELDS = set(FIELDS) | OPTIONAL
TIER_MIN, TIER_MAX = 0, 5  # user decision: tier range 0 to 5 (basic magic = 0)

errors = []

def err(where, msg):
    errors.append(f"{where}: {msg}")

def is_int(x, lo=None):
    return isinstance(x, int) and not isinstance(x, bool) and (lo is None or x >= lo)

def check_amounts(w, v):
    if not isinstance(v, dict) or v.get("kind") != "time" or not isinstance(v.get("amounts"), dict):
        return err(w, "time value must be {kind:'time', amounts:{...}}")
    for u, n in v["amounts"].items():
        if u not in UNITS:
            err(w, f"unknown unit {u}")
        if not is_int(n, 1):
            err(w, f"amount of {u} must be a positive integer")

def check_timevalue(w, v, allow_kinds):
    """Generic time value: time / oneOff / permanent / formDependent / undefined."""
    if not isinstance(v, dict) or "kind" not in v:
        return err(w, "missing kind")
    k = v["kind"]
    if k not in allow_kinds:
        return err(w, f"kind {k!r} not allowed here")
    if k == "time":
        check_amounts(w, v)

def check_duration(w, v):
    if not isinstance(v, dict) or v.get("kind") not in ("time", "oneOff", "permanent", "formDependent", "undefined"):
        return err(w, "bad duration")
    if v["kind"] == "time":
        check_amounts(w + ".time", v.get("time"))

def check_cost(w, c, d):
    if not isinstance(c, dict) or c.get("pattern") not in PATTERNS:
        return err(w, "bad cost pattern")
    p = c["pattern"]
    allowed = {"flat": {"pattern", "baseMp"}, "perMass": {"pattern", "rate"},
               "compositionPerMass": {"pattern", "rates", "rounding"},
               "compositionGrid": {"pattern", "rows", "columns", "rates"},
               "lookup": {"pattern", "input", "table"}, "undefined": {"pattern"}}[p]
    if set(c) != allowed:
        err(w, f"fields {sorted(c)} != {sorted(allowed)}")
        return
    if p == "flat" and not is_int(c["baseMp"], 0):
        err(w, "baseMp must be a non-negative integer")
    if p == "perMass":
        r = c["rate"]
        if not (isinstance(r, dict) and is_int(r.get("mp"), 1) and is_int(r.get("massG"), 1)):
            err(w, "rate must be {mp, massG} positive integers")
    if p == "compositionPerMass":
        if c["rounding"] is not None:
            err(w, "rounding must be null (not defined yet)")
        if set(c["rates"]) != {"I", "II", "III"}:
            err(w, "rates need groups I, II, III")
        else:
            for g, r in c["rates"].items():
                if r is not None and not (is_int(r.get("mp"), 1) and is_int(r.get("massG"), 1)):
                    err(w, f"rate {g} must be null or {{mp, massG}}")
    if p == "compositionGrid":
        if c["rows"] != ["I", "II", "III"] or c["columns"] != ["I", "I+II", "I+II+III"]:
            err(w, "grid rows/columns differ from the format")
    if p == "lookup":
        ids = [i.get("id") for i in d.get("inputs", [])]
        if c["input"] not in ids:
            err(w, "lookup input is not among the inputs")
        prev = None
        for row in c["table"]:
            if set(row) != {"fromM", "toM", "mp"} or not is_int(row["fromM"], 0) or not is_int(row["mp"], 0) \
                    or not (row["toM"] is None or is_int(row["toM"], 0)):
                err(w, f"bad table row {row}")
            if prev is not None and row["fromM"] <= prev:
                err(w, "table rows must be ascending")
            prev = row["fromM"]

def check_mosaic(fname, d):
    w = f"{fname}:{d.get('id', '?')}"
    for f in FIELDS:
        if f not in d:
            err(w, f"missing required field {f}")
    for f in d:
        if f not in TOP_FIELDS:
            err(w, f"unknown field {f}")
    if any(f not in d for f in FIELDS):
        return
    if not re.fullmatch(r"[a-z0-9]+(-[a-z0-9]+)*", d["id"]):
        err(w, "id must be lowercase ASCII with hyphens")
    if not isinstance(d["name"], str) or not d["name"]:
        err(w, "name must be a non-empty string")
    if d["school"] not in SCHOOLS:
        err(w, "bad school")
    elif FILES.get(d["school"]) != fname:
        err(w, f"school {d['school']} does not belong in {fname}")
    if d["school"] == "anyagi":
        if d.get("subgroup") not in ("elemi", "termeszetes"):
            err(w, "anyagi needs subgroup elemi|termeszetes")
    elif "subgroup" in d:
        err(w, "subgroup only for anyagi")
    # status
    st = d["status"]
    if not (isinstance(st, dict) and set(st) == {"state", "missing"} and st["state"] in STATES
            and isinstance(st["missing"], list) and all(isinstance(m, str) for m in st["missing"])):
        return err(w, "bad status")
    state, missing = st["state"], st["missing"]
    if len(set(missing)) != len(missing):
        err(w, "duplicate entries in missing")
    # tier
    t = d["tier"]
    if d["school"] == "alapvarazslatok":
        if t != 0:
            err(w, "tier must be 0 for alapvarazslatok")
    elif t is None:
        if not (state == "incomplete" and "tier" in missing):
            err(w, "tier null only for an incomplete mosaic with 'tier' in missing")
    elif not is_int(t) or not TIER_MIN <= t <= TIER_MAX:
        err(w, f"tier must be {TIER_MIN}..{TIER_MAX}")
    if t is not None and "tier" in missing:
        err(w, "'tier' in missing but tier is set")
    # casting time
    ct = d["castingTime"]
    if not (isinstance(ct, dict) and set(ct) == {"label", "time"} and ct["label"] in ("casting", "meditation")):
        err(w, "bad castingTime")
    else:
        check_timevalue(w + ".castingTime", ct["time"], ("time", "undefined"))
        if (d["school"] == "mental") != (ct["label"] == "meditation"):
            err(w, "label must be meditation exactly for mental")
    check_duration(w + ".duration", d["duration"])
    # range
    r = d["range"]
    if not (isinstance(r, dict) and r.get("kind") in RANGES):
        err(w, "bad range")
    elif r["kind"] == "meters" and not (isinstance(r.get("value"), (int, float)) and r["value"] > 0):
        err(w, "meters needs value")
    # resistance
    rs = d["resistance"]
    if not (isinstance(rs, dict) and rs.get("kind") in RESIST):
        err(w, "bad resistance")
    elif (rs["kind"] == "school") != ("school" in rs) or ("school" in rs and rs["school"] not in SCHOOLS):
        err(w, "resistance school field inconsistent")
    elif "input" in rs:
        choice = next((i for i in d.get("inputs", []) if i.get("id") == rs["input"] and i.get("type") == "choice"), None)
        if choice is None:
            err(w, "resistance input must name a choice input of the mosaic")
        elif "pairs" in rs:
            pairs, values = rs["pairs"], choice.get("values", [])
            if set(pairs) != set(values) or not set(pairs.values()) <= set(values):
                err(w, "resistance pairs must cover every value of the input and only use its values")
            elif any(pairs[pairs[k]] != k for k in pairs):
                err(w, "resistance pairs must be symmetric (fire-water, air-earth)")
    # power
    p = d["power"]
    if p is not None:
        if not (isinstance(p, dict) and set(p) == {"unit", "baseE", "step"} and p["unit"] in ("E", "Fp")
                and is_int(p["baseE"], 1)):
            err(w, "bad power")
        elif p["step"] is not None and not (isinstance(p["step"], dict) and set(p["step"]) == {"mp", "e"}
                                            and is_int(p["step"]["mp"], 1) and is_int(p["step"]["e"], 1)):
            err(w, "bad power step")
    # cost
    check_cost(w + ".cost", d["cost"], d)
    pat = d["cost"].get("pattern") if isinstance(d["cost"], dict) else None
    cost_undef = pat == "undefined" or (pat == "compositionGrid" and d["cost"].get("rates") is None)
    # modifier / options
    m = d.get("modifier")
    if m is not None:
        if set(m) != {"appliesToWholeSpell", "addsCastingTime", "addsDuration", "durationPerExtraMp"} \
                or m["appliesToWholeSpell"] is not True:
            err(w, "bad modifier")
        else:
            check_timevalue(w + ".modifier.addsCastingTime", m["addsCastingTime"], ("time",))
            check_timevalue(w + ".modifier.addsDuration", m["addsDuration"], ("time", "permanent", "undefined"))
            if m["durationPerExtraMp"] is not None:
                check_timevalue(w + ".modifier.durationPerExtraMp", m["durationPerExtraMp"], ("time",))
    for o in d.get("options", []):
        if set(o) != {"id", "label", "extraMp", "extraCastingTime"} or not is_int(o["extraMp"], 0):
            err(w, f"bad option {o}")
        elif o["extraCastingTime"] is not None:
            check_timevalue(w + ".option", o["extraCastingTime"], ("time",))
    # inputs
    ids = set()
    types = set()
    if not isinstance(d["inputs"], list):
        err(w, "inputs must be a list")
    else:
        for i in d["inputs"]:
            if not (isinstance(i, dict) and isinstance(i.get("id"), str) and isinstance(i.get("label"), str)
                    and i.get("type") in INPUT_TYPES):
                err(w, f"bad input {i}")
                continue
            if i["id"] in ids:
                err(w, f"duplicate input id {i['id']}")
            ids.add(i["id"])
            types.add(i["type"])
            if i["type"] == "choice" and not (isinstance(i.get("values"), list) and i["values"]):
                err(w, f"choice input {i['id']} needs values")
            for extra in ("labels", "descriptions"):
                if extra in i:
                    t = i[extra]
                    if i["type"] != "choice" or not (isinstance(t, dict) and t and set(t) <= set(i.get("values", []))
                                                      and all(isinstance(x, str) and x for x in t.values())):
                        err(w, f"input {i['id']}: {extra} must be a map from values of a choice input to non-empty text")
            if i["type"] == "number" and not isinstance(i.get("unit"), str):
                err(w, f"number input {i['id']} needs unit")
    if (d["duration"].get("kind") == "formDependent") != ("form" in types):
        err(w, "duration formDependent <=> a form input")
    if pat in ("compositionPerMass", "compositionGrid") and "composition" not in types:
        err(w, "composition cost needs a composition input")
    if not re.fullmatch(r"Part \d+[ab]?", d["source"]):
        err(w, "source must name a plan Part")
    if "effect" in d and not (isinstance(d["effect"], str) and d["effect"]):
        err(w, "effect must be a non-empty string")
    # status consistency
    if state == "complete" and missing:
        err(w, "complete but missing is not empty")
    if state != "complete" and not missing:
        err(w, f"{state} needs a non-empty missing")
    if state == "incomplete":
        if not cost_undef:
            err(w, "incomplete requires an undefined cost")
        if "cost" not in missing:
            err(w, "incomplete requires 'cost' in missing")
    else:
        if cost_undef:
            err(w, f"undefined cost requires status incomplete, got {state}")
        if "cost" in missing:
            err(w, "'cost' in missing needs status incomplete")
    # missing names must match the data
    undefined_of = {"castingTime": ct["time"].get("kind") == "undefined" if isinstance(ct, dict) else False,
                    "duration": d["duration"].get("kind") == "undefined",
                    "range": r.get("kind") == "undefined", "resistance": rs.get("kind") == "undefined",
                    "cost": cost_undef}
    for name, is_undef in undefined_of.items():
        if name in missing and not is_undef:
            err(w, f"'{name}' in missing but the value is defined")
        # a modifier mosaic carries its time via the modifier, so castingTime/duration/range may be empty
        exempt = m is not None and name in ("duration", "range")
        if is_undef and name not in missing and not exempt:
            err(w, f"{name} is undefined but not listed in missing")
    for name in missing:
        if name not in undefined_of and name not in ("power", "tier"):
            err(w, f"unknown missing entry {name}")

def check_forms(forms, mosaic_ids):
    seen = set()
    for f in forms:
        w = f"forms.json:{f.get('id', '?')}"
        if set(f) != {"id", "name", "tier", "duration", "regeneration", "source"}:
            err(w, "fields must be id,name,tier,duration,regeneration,source")
            continue
        if f["regeneration"] is not None and not (isinstance(f["regeneration"], str) and f["regeneration"]):
            err(w, "regeneration must be text or null")
        if not re.fullmatch(r"[a-z0-9]+", f["id"]) or f["id"] in seen:
            err(w, "bad or duplicate id")
        seen.add(f["id"])
        if not is_int(f["tier"]) or not TIER_MIN <= f["tier"] <= TIER_MAX:
            err(w, "bad tier")
        check_duration(w + ".duration", f["duration"])
        if f["duration"].get("kind") in ("undefined", "formDependent"):
            err(w, "form duration must be time or oneOff")

def check_groups(groups):
    if [g.get("id") for g in groups] != ["I", "II", "III"]:
        err("material_groups.json", "need exactly I, II, III")
    for g in groups:
        if not (isinstance(g.get("name"), str) and isinstance(g.get("examples"), list) and g["examples"]):
            err("material_groups.json", f"bad group {g.get('id')}")

# Part 13 table: (school/subgroup) -> (mosaics, regular, special, incomplete)
PART13 = OrderedDict([
    ("alapvarazslatok", (4, 4, 0, 0)), ("ter", (11, 9, 1, 1)), ("mental", (30, 28, 0, 2)),
    ("asztral", (12, 8, 0, 4)), ("ido", (16, 8, 0, 8)), ("nekromancia", (11, 5, 0, 6)),
    ("anyagi/elemi", (5, 4, 0, 1)), ("anyagi/termeszetes", (12, 11, 0, 1)),
])

def cost_class(d):
    p = d["cost"]["pattern"]
    if p == "undefined" or (p == "compositionGrid" and d["cost"].get("rates") is None):
        return "incomplete"
    return "special" if p in ("lookup", "compositionGrid") else "regular"

def main():
    all_ids = Counter()
    by_key = OrderedDict()
    by_school = Counter()
    by_state = Counter()
    for school, fname in FILES.items():
        path = os.path.join(SEED, fname)
        try:
            with open(path, encoding="utf-8") as fh:
                data = json.load(fh)
        except Exception as e:  # noqa: BLE001
            err(fname, f"cannot load: {e}")
            continue
        if not isinstance(data, list):
            err(fname, "must be a JSON array")
            continue
        for d in data:
            check_mosaic(fname, d)
            if isinstance(d, dict) and "id" in d:
                all_ids[d["id"]] += 1
                key = d["school"] + ("/" + d["subgroup"] if d.get("subgroup") else "")
                by_school[d["school"]] += 1
                by_state[d["status"]["state"]] += 1
                by_key.setdefault(key, Counter())
                by_key[key]["n"] += 1
                by_key[key][cost_class(d)] += 1
                by_key[key]["state:" + d["status"]["state"]] += 1
    for i, n in all_ids.items():
        if n > 1:
            err("all files", f"duplicate id {i}")
    for name, check in (("forms.json", check_forms), ("material_groups.json", check_groups)):
        with open(os.path.join(SEED, name), encoding="utf-8") as fh:
            data = json.load(fh)
        check(data, all_ids) if name == "forms.json" else check(data)
    print("Mosaics per school:")
    for s in SCHOOLS:
        print(f"  {s:16s} {by_school[s]}")
    print(f"  {'TOTAL':16s} {sum(by_school.values())}")
    print("Mosaics per status: " + ", ".join(f"{s}={by_state[s]}" for s in STATES))
    print("\nPart 13 comparison (cost class: regular / special / incomplete); status in brackets")
    print(f"  {'group':20s} {'plan (n,reg,spe,inc)':24s} {'seed (n,reg,spe,inc)':24s} status c/p/i  match")
    tot_plan = [0, 0, 0, 0]
    tot_seed = [0, 0, 0, 0]
    for key, exp in PART13.items():
        c = by_key.get(key, Counter())
        got = (c["n"], c["regular"], c["special"], c["incomplete"])
        for i in range(4):
            tot_plan[i] += exp[i]
            tot_seed[i] += got[i]
        sts = "/".join(str(c["state:" + s]) for s in STATES)
        print(f"  {key:20s} {str(exp):24s} {str(got):24s} {sts:12s} {'ok' if exp == got else 'DIFF'}")
    print(f"  {'TOTAL':20s} {str(tuple(tot_plan)):24s} {str(tuple(tot_seed)):24s}")
    if errors:
        print(f"\n{len(errors)} ERROR(S):")
        for e in errors:
            print("  -", e)
        sys.exit(1)
    print("\nOK: no validation errors")

if __name__ == "__main__":
    main()
