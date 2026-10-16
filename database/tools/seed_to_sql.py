"""Print SQL INSERT statements for seed/*.json (run validate_seed.py first).
Usage: .venv/bin/python tools/seed_to_sql.py > build/seed.sql ; psql -d <db> -f build/seed.sql
"""
import json
import os

SEED = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "seed")
FILES = ["alapvarazslatok", "anyagi", "asztral", "mental", "ter", "ido", "nekromancia"]

def q(s):
    return "NULL" if s is None else "'" + str(s).replace("'", "''") + "'"

def j(o):
    return q(json.dumps(o, ensure_ascii=False)) + "::jsonb"

def load(name):
    with open(os.path.join(SEED, name), encoding="utf-8") as f:
        return json.load(f)

print("BEGIN;")
for school in FILES:
    for d in load(school + ".json"):
        tier = "NULL" if d["tier"] is None else str(d["tier"])
        print("INSERT INTO mosaics (id, name, school, subgroup, tier, status_state, cost_pattern, source, definition) "
              f"VALUES ({q(d['id'])}, {q(d['name'])}, {q(d['school'])}, {q(d.get('subgroup'))}, {tier}, "
              f"{q(d['status']['state'])}, {q(d['cost']['pattern'])}, {q(d['source'])}, {j(d)});")
for f in load("forms.json"):
    print(f"INSERT INTO forms (id, name, tier, duration, regeneration, description, movable, source) VALUES "
          f"({q(f['id'])}, {q(f['name'])}, {f['tier']}, {j(f['duration'])}, {q(f['regeneration'])}, {q(f['description'])}, {'true' if f['movable'] else 'false'}, {q(f['source'])});")
for g in load("material_groups.json"):
    ex = "ARRAY[" + ", ".join(q(e) for e in g["examples"]) + "]::text[]"
    print(f"INSERT INTO material_groups (id, name, examples, source) VALUES "
          f"({q(g['id'])}, {q(g['name'])}, {ex}, {q(g['source'])});")
for t in load("reference_texts.json"):
    print(f"INSERT INTO reference_texts (id, text, source) VALUES ({q(t['id'])}, {q(t['text'])}, {q(t['source'])});")
print("COMMIT;")
