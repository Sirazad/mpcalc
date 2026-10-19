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

## Notes for the chat (user, 2026-10-19)
Hungarian texts that are not used in any calculation, only given to the chat when a question uses their words (rules, lore).
- Source: `notes/<id>.md`, one file per note. A header between two `---` lines (`id`, `title`, `category` = `magic` or `world`, optional `school`, `keywords` separated by commas, `source`) and then the text. The file name is the id.
- `.venv/bin/python tools/build_notes.py` checks the files and writes `seed/notes.json` and the repeatable migration `R__notes.sql` (here and in the backend). Flyway runs it again whenever it changes; it rewrites the table `notes` from the files, so a note is edited only in its file and a note without a file is removed.
- Table `notes` (migration `V25__notes.sql`): the `search` column is generated: keywords (weight A), title (B), text (C), stemmed with Postgres' Hungarian configuration.
- Search (backend `NoteService`, MCP tool `find_notes`): a note is found when a word of the question matches its keywords or title; the text only raises the rank. A text-only match found notes for questions that merely share a common word. Stemming does not join word forms whose vowel changes (fonál, fonalat): list both forms as keywords.
- The chat service searches before the model answers and gives the found notes with the question; the model can also call `find_notes`. Known limit: a generic word of a compound keyword (the "mentál" of "mentál-fonál") also finds the note.

