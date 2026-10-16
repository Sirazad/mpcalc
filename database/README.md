# MPCalc database (phase 1)

Postgres schema (Flyway-style) and seed data. Format: `../docs/mosaic-definition-format.md`. Open questions: `QUESTIONS.md`.

- `migrations/V1__init.sql` schools, mosaics, forms, material_groups. `V2__players_characters.sql` players (admin flag), characters, character_school_tiers, saved_spells. `V3__audit_log.sql` append-only audit log.
- `seed/*.json` one file per school (`anyagi.json` holds Elemi and Természetes), plus `forms.json`, `material_groups.json`. 101 mosaics. Szimpatikus has no data.
- `mosaics` columns: id, school, subgroup, tier, status_state, cost_pattern, source, `definition` (whole JSON). CHECK constraints keep the columns equal to the JSON.
- Status rules: cost undefined -> `incomplete` and pattern `undefined` (exception: Légiesség keeps `compositionGrid`, see QUESTIONS 5); cost defined but another field undefined -> `partial`.

Tools (Python, only through the venv):
```
python3 -m venv .venv                         # once; then:  .venv/bin/python -m pip install pglast   (optional, SQL syntax check)
.venv/bin/python tools/validate_seed.py       # rules check + counts per school/status + Part 13 comparison
.venv/bin/python tools/seed_to_sql.py > build/seed.sql   # INSERTs; load with psql -f after the migrations
```
`tools/build_seed.py` generated the seed once from the plan; the JSON files are now the source of truth (re-running it overwrites hand edits).

Status: SQL was only syntax-parsed (pglast), not executed: no PostgreSQL available on this machine.
