import { Mosaic } from './models';

/**
 * Mosaics that the page offers under one entry with one drop-down (user decision 2026-10-16). Page only: the data keeps one
 * mosaic per rule (each has its own fok, MP and ME), a choice of the drop-down just decides which of them is added.
 */
export interface MosaicGroupDef {
  id: string;
  name: string;
  school: string;
  /** Label of the drop-down. */
  choiceLabel: string;
  /** The member mosaics; `label` names the single choice of a member that has no choice input. */
  members: { id: string; label?: string }[];
}

export const MOSAIC_GROUPS: MosaicGroupDef[] = [
  {
    id: 'elemek-idezese', name: 'Elemek idézése', school: 'anyagi', choiceLabel: 'Elem',
    members: [
      { id: 'anyagi-elemi-oselem-idezese' },
      { id: 'anyagi-elemi-paraelem-idezese' },
      { id: 'anyagi-elemi-elemi-ero-idezese', label: 'Elemi erő' },
    ],
  },
];

/** One entry of the drop-down of a group: the member mosaic it adds and the value of its choice input it presets. */
export interface GroupChoice {
  key: string;
  label: string;
  description: string | null;
  tier: number | null;
  mosaic: Mosaic;
  preset: Record<string, string>;
}

/** The choices of a group, in the order of its members. Only mosaics the character can use are in `mosaics`. */
export function groupChoices(def: MosaicGroupDef, mosaics: Mosaic[]): GroupChoice[] {
  const out: GroupChoice[] = [];
  for (const member of def.members) {
    const m = mosaics.find((x) => x.id === member.id);
    if (!m) continue;
    const choice = (m.inputs ?? []).find((i) => i.type === 'choice' && i.labels);
    if (choice) {
      for (const v of choice.values ?? []) {
        out.push({ key: `${m.id}:${v}`, label: choice.labels?.[v] ?? v, description: choice.descriptions?.[v] ?? null,
                   tier: m.tier, mosaic: m, preset: { [choice.id]: v } });
      }
    } else {
      out.push({ key: m.id, label: member.label ?? m.name, description: m.effect ?? null, tier: m.tier, mosaic: m, preset: {} });
    }
  }
  return out;
}

/** One line of the mosaic list of a school: a single mosaic or a group. */
export interface ListEntry {
  key: string;
  name: string;
  tier: number | null;
  /** The fok shown in the list: "2", or "2–3" for a group whose members differ. */
  tierText: string;
  mosaic?: Mosaic;
  group?: { def: MosaicGroupDef; choices: GroupChoice[] };
}

/** The entries of a school: members of a group are replaced by one entry; sorted by fok, then name. */
export function listEntries(school: string, mosaics: Mosaic[]): ListEntry[] {
  const inSchool = mosaics.filter((m) => m.school === school);
  const grouped = new Set<string>();
  const entries: ListEntry[] = [];
  for (const def of MOSAIC_GROUPS.filter((g) => g.school === school)) {
    const choices = groupChoices(def, inSchool);
    if (!choices.length) continue;
    choices.forEach((c) => grouped.add(c.mosaic.id));
    const tiers = choices.map((c) => c.tier).filter((t): t is number => t !== null);
    const lo = tiers.length ? Math.min(...tiers) : null, hi = tiers.length ? Math.max(...tiers) : null;
    entries.push({ key: def.id, name: def.name, tier: lo, tierText: lo === hi ? String(lo ?? '') : `${lo}–${hi}`, group: { def, choices } });
  }
  for (const m of inSchool) if (!grouped.has(m.id)) entries.push({ key: m.id, name: m.name, tier: m.tier, tierText: String(m.tier ?? ''), mosaic: m });
  return entries.sort((a, b) => (a.tier ?? 0) - (b.tier ?? 0) || a.name.localeCompare(b.name, 'hu'));
}
