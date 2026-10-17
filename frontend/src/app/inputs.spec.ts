import { chosenDescription, defaultInputs, describeSelection, formsAllowed, groupsFor, missingInputs, powerAt, schoolsWithMosaics, toSelection, unusedGroup } from './inputs';
import { Form, Mosaic } from './models';

const forms: Form[] = [
  { id: 'a', name: 'A', tier: 2, duration: null },
  { id: 'b', name: 'B', tier: 3, duration: null },
];

function mosaic(partial: Partial<Mosaic>): Mosaic {
  return {
    id: 'm', name: 'M', school: 'anyagi', tier: 2, status: { state: 'complete', missing: [] }, power: null,
    cost: { pattern: 'flat' }, source: 'x', ...partial,
  };
}

describe('inputs', () => {
  it('lists only forms whose tier the character has in the school of the mosaic', () => {
    expect(formsAllowed(forms, { anyagi: 2 }, 'anyagi').map((f) => f.id)).toEqual(['a']);
    expect(formsAllowed(forms, { anyagi: 3 }, 'anyagi').map((f) => f.id)).toEqual(['a', 'b']);
    expect(formsAllowed(forms, { anyagi: 3 }, 'ter')).toEqual([]);
  });

  it('asks for every input and sends complete composition rows as group and massG', () => {
    const m = mosaic({ inputs: [{ id: 'composition', type: 'composition' }, { id: 'form', type: 'form' }] });
    const v = defaultInputs(m);
    expect(missingInputs(m, v)).toEqual(['composition', 'form']);
    v['form'] = 'a';
    v['composition'] = [{ group: 'II', massG: 2000 }, { group: 'I', massG: null }];
    expect(missingInputs(m, v)).toEqual([]);
    const s = toSelection(m, v, 0, [], 0);
    expect(s.inputs['composition']).toEqual([{ group: 'II', massG: 2000 }]);
    expect(s.inputs['form']).toBe('a');
  });

  it('builds the E of the chosen power step from the power data', () => {
    const m = mosaic({ power: { unit: 'E', baseE: 3, step: { mp: 2, e: 3 } } });
    expect(powerAt(m, 2)).toEqual({ value: 9, unit: 'E', infinite: false });
    expect(powerAt(mosaic({ power: { unit: 'E', baseE: 1, step: null } }), 5)).toEqual({ value: 1, unit: 'E', infinite: false });
    expect(powerAt(mosaic({ power: { unit: 'E', baseE: null, step: null, infinite: true } }), 0)).toEqual({ value: null, unit: 'E', infinite: true });
    expect(powerAt(mosaic({ power: null }), 1)).toBeNull();
  });

  it('lists only schools that have an available mosaic, in the schools order', () => {
    const ms = [mosaic({ school: 'ter' }), mosaic({ school: 'alapvarazslatok' })];
    expect(schoolsWithMosaics(ms, ['alapvarazslatok', 'anyagi', 'ter'])).toEqual(['alapvarazslatok', 'ter']);
  });

  it('describes the entered details of a selection for the spell panel', () => {
    const m = mosaic({
      inputs: [
        { id: 'element', label: 'Őselem', type: 'choice', values: ['ostuz'], labels: { ostuz: 'Ős tűz' } },
        { id: 'form', label: 'Forma', type: 'form' },
        { id: 'composition', label: 'Összetétel', type: 'composition' },
        { id: 'rangeM', label: 'Hatótáv', type: 'number', unit: 'm' },
      ],
      options: [{ id: 'move', label: 'Ablak mozgatása', extraMp: 20 }],
    });
    const groups = [{ id: 'II', name: 'II. csoport', examples: [] }];
    const sel = {
      mosaicId: 'm', k: 0, options: ['move'], extraMp: 2,
      inputs: { element: 'ostuz', form: 'a', composition: [{ group: 'II', massG: 2000 }], rangeM: 50 },
    };
    const d = describeSelection(m, sel, forms, groups);
    expect(d.map((x) => x.label)).toEqual(['Őselem', 'Forma', 'Összetétel', 'Hatótáv', 'Opciók', 'Többlet MP a bővítésre']);
    expect(d[0].lines).toEqual(['Ős tűz']);
    expect(d[1].lines).toEqual(['A']);
    expect(d[2].lines).toEqual(['II. csoport: 2000 g']);
    expect(d[3].lines).toEqual(['50 m']);
    expect(d[4].lines).toEqual(['Ablak mozgatása']);
    expect(describeSelection(undefined, sel, forms, groups)).toEqual([]);
  });

  it('uses the description of the chosen value when a mosaic has no effect text of its own', () => {
    const m = mosaic({ inputs: [{ id: 'element', label: 'Őselem', type: 'choice', values: ['ostuz', 'osviz'], descriptions: { osviz: 'Teljesen tiszta átlátszó víz.' } }] });
    const sel = (v: string) => ({ mosaicId: 'm', k: 0, options: [], extraMp: 0, inputs: { element: v } });
    expect(chosenDescription(m, sel('osviz'))).toBe('Teljesen tiszta átlátszó víz.');
    expect(chosenDescription(m, sel('ostuz'))).toBeNull();
    expect(chosenDescription(undefined, sel('osviz'))).toBeNull();
  });

  it('allows only a single row per material group', () => {
    const groups = [{ id: 'I', name: 'I', examples: [] }, { id: 'II', name: 'II', examples: [] }, { id: 'III', name: 'III', examples: [] }];
    const rows = [{ group: 'I', massG: 5 }, { group: 'III', massG: 7 }];
    expect(unusedGroup(rows, groups)).toBe('II');
    expect(unusedGroup([...rows, { group: 'II', massG: null }], groups)).toBeNull();
    // a row may keep its own group and pick an unused one, never a group of another row
    expect(groupsFor(rows, groups, 0).map((g) => g.id)).toEqual(['I', 'II']);
    expect(groupsFor(rows, groups, 1).map((g) => g.id)).toEqual(['II', 'III']);
  });
});
