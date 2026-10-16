import { groupChoices, listEntries, MOSAIC_GROUPS } from './mosaic-groups';
import { Mosaic } from './models';

function mosaic(id: string, name: string, tier: number, extra: Partial<Mosaic> = {}): Mosaic {
  return { id, name, school: 'anyagi', tier, status: { state: 'complete', missing: [] }, power: null, cost: { pattern: 'flat' }, source: 'x', ...extra } as Mosaic;
}
const oselem = mosaic('anyagi-elemi-oselem-idezese', 'Őselem idézése', 2, {
  inputs: [{ id: 'element', label: 'Őselem', type: 'choice', values: ['ostuz', 'osviz'], labels: { ostuz: 'Ős tűz', osviz: 'Ős víz' }, descriptions: { ostuz: 'Önállóan létező anyag.' } }],
});
const para = mosaic('anyagi-elemi-paraelem-idezese', 'Paraelem idézése', 3, {
  inputs: [{ id: 'paraElement', label: 'Paraelem', type: 'choice', values: ['ho'], labels: { ho: 'Elemi hő' } }],
});
const ero = mosaic('anyagi-elemi-elemi-ero-idezese', 'Elemi erő idézése', 3, { effect: 'Mozgatni vagy megállítani lehet tárgyakat.' });
const other = mosaic('anyagi-term-atformazas', 'Átformázás', 2);

describe('mosaic groups', () => {
  const def = MOSAIC_GROUPS[0];

  it('turns the member mosaics into one choice per element, in the order of the members', () => {
    const c = groupChoices(def, [para, other, ero, oselem]);
    expect(c.map((x) => x.label)).toEqual(['Ős tűz', 'Ős víz', 'Elemi hő', 'Elemi erő']);
    expect(c[0]).toMatchObject({ key: 'anyagi-elemi-oselem-idezese:ostuz', tier: 2, preset: { element: 'ostuz' }, description: 'Önállóan létező anyag.' });
    expect(c[1].description).toBeNull();
    expect(c[3]).toMatchObject({ preset: {}, description: 'Mozgatni vagy megállítani lehet tárgyakat.' });
  });

  it('offers only the members the character can use (the others are not in the list)', () => {
    expect(groupChoices(def, [oselem]).map((x) => x.label)).toEqual(['Ős tűz', 'Ős víz']);
    expect(groupChoices(def, [other])).toEqual([]);
  });

  it('replaces the members by one entry in the list, sorted by fok and name', () => {
    const entries = listEntries('anyagi', [para, other, ero, oselem]);
    // both have fok 2 at the lowest, so the name decides
    expect(entries.map((e) => e.name)).toEqual(['Átformázás', 'Elemek idézése']);
    expect(entries[0].mosaic).toBe(other);
    expect(entries[1].tierText).toBe('2–3');
    expect(listEntries('anyagi', [para, ero, other]).map((e) => e.name)).toEqual(['Átformázás', 'Elemek idézése']);
  });

  it('shows no group entry when no member is available, and the other schools are untouched', () => {
    expect(listEntries('anyagi', [other]).map((e) => e.name)).toEqual(['Átformázás']);
    expect(listEntries('ter', [oselem])).toEqual([]);
  });
});
