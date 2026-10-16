import { formatNumber } from './format';
import { Character, Form, MaterialGroup, Mosaic, MosaicInput, Selection } from './models';
import { T } from './texts.hu';

/** The form drop-down lists only forms whose tier the character has in the school of the mosaic. */
export function formsAllowed(forms: Form[], tiers: Record<string, number>, school: string): Form[] {
  const tier = tiers[school] ?? 0;
  return forms.filter((f) => f.tier <= tier);
}

export interface CompositionRow { group: string; massG: number | null; }

/** Values entered in the input form: by input id. */
export type InputValues = Record<string, string | number | null | CompositionRow[]>;

/** Only a single row per material group (user decision): the groups a row may pick are the unused ones plus its own. */
export function groupsFor(rows: CompositionRow[], groups: MaterialGroup[], index: number): MaterialGroup[] {
  const usedElsewhere = new Set(rows.filter((_, i) => i !== index).map((r) => r.group));
  return groups.filter((g) => !usedElsewhere.has(g.id));
}

/** The first material group no row uses yet, or null when every group has a row. */
export function unusedGroup(rows: CompositionRow[], groups: MaterialGroup[]): string | null {
  const used = new Set(rows.map((r) => r.group));
  return groups.find((g) => !used.has(g.id))?.id ?? null;
}

export function defaultInputs(m: Mosaic): InputValues {
  const v: InputValues = {};
  for (const i of m.inputs ?? []) {
    v[i.id] = i.type === 'composition' ? [{ group: 'I', massG: null }] : i.type === 'number' ? null : '';
  }
  return v;
}

export function isFilled(i: MosaicInput, value: InputValues[string] | undefined): boolean {
  if (i.type === 'composition') return (value as CompositionRow[] ?? []).some((r) => r.group && r.massG !== null && r.massG > 0);
  if (i.type === 'number') return typeof value === 'number' && !Number.isNaN(value);
  return typeof value === 'string' && value.trim() !== '';
}

/** Ids of the inputs that still need a value. */
export function missingInputs(m: Mosaic, values: InputValues): string[] {
  return (m.inputs ?? []).filter((i) => !isFilled(i, values[i.id])).map((i) => i.id);
}

/** The selection the backend expects: composition rows as {group, massG}, only complete rows. */
export function toSelection(m: Mosaic, values: InputValues, k: number, options: string[], extraMp: number): Selection {
  const inputs: Record<string, unknown> = {};
  for (const i of m.inputs ?? []) {
    const v = values[i.id];
    inputs[i.id] = i.type === 'composition'
      ? (v as CompositionRow[]).filter((r) => r.group && r.massG !== null && r.massG > 0).map((r) => ({ group: r.group, massG: r.massG }))
      : v;
  }
  return { mosaicId: m.id, inputs, k, options, extraMp };
}

/** E of the chosen power step, from the mosaic's power data (the format document: the page generates E choices from `power`). */
export function powerAt(m: Mosaic, k: number): { value: number; unit: string } | null {
  if (!m.power) return null;
  return { value: m.power.baseE + (m.power.step ? k * m.power.step.e : 0), unit: m.power.unit };
}

/** Schools of the mosaics the character may use, in the order of the schools list. */
export function schoolsWithMosaics(mosaics: Mosaic[], schoolIds: string[]): string[] {
  const have = new Set(mosaics.map((m) => m.school));
  return schoolIds.filter((s) => have.has(s));
}

export function tiersOf(c: Character): Record<string, number> { return c.tiers; }

/** One line group of the details shown under a mosaic of the current spell: a label and one or more lines. */
export interface DetailItem { label: string; lines: string[]; }

/** The description of the value the player chose in a choice input (for example the element), if the mosaic has one. */
export function chosenDescription(m: Mosaic | undefined, s: Selection): string | null {
  for (const i of m?.inputs ?? []) {
    const text = i.type === 'choice' ? i.descriptions?.[String(s.inputs[i.id])] : undefined;
    if (text) return text;
  }
  return null;
}

/** The details the player entered for a mosaic (element, form, composition, number, text, ticked options, extra MP). */
export function describeSelection(m: Mosaic | undefined, s: Selection, forms: Form[], groups: MaterialGroup[]): DetailItem[] {
  if (!m) return [];
  const items: DetailItem[] = [];
  for (const i of m.inputs ?? []) {
    const v = s.inputs[i.id];
    const label = i.label ?? i.id;
    if (i.type === 'composition') {
      const rows = (v as { group: string; massG: number }[] | undefined) ?? [];
      items.push({
        label,
        lines: rows.map((r) => `${groups.find((g) => g.id === r.group)?.name ?? r.group}: ${formatNumber(r.massG)} ${T.builder.massUnit}`),
      });
    } else if (i.type === 'form') {
      items.push({ label, lines: [forms.find((f) => f.id === v)?.name ?? String(v ?? '')] });
    } else if (i.type === 'number') {
      items.push({ label, lines: [`${formatNumber(Number(v))}${i.unit ? ' ' + i.unit : ''}`] });
    } else if (i.type === 'choice') {
      items.push({ label, lines: [i.labels?.[String(v)] ?? String(v ?? '')] });
    } else {
      items.push({ label, lines: [String(v ?? '')] });
    }
  }
  const ticked = (m.options ?? []).filter((o) => s.options.includes(o.id)).map((o) => o.label);
  if (ticked.length) items.push({ label: T.builder.options, lines: ticked });
  if (s.extraMp > 0) items.push({ label: T.builder.extraMp, lines: [String(s.extraMp)] });
  return items;
}
