import { castingBreakdown } from './casting';
import { CastingPart, Maybe, MosaicResult, SpellResult, TimeValue } from './models';

const time = (amounts: Record<string, number>): Maybe<TimeValue> => ({ defined: true, value: { kind: 'TIME', amounts } });
const undef = { defined: false, reason: { code: 'x', text: 'nincs megadva' } } as unknown as Maybe<TimeValue>;
function mosaic(name: string, parts: CastingPart[], refused = false): MosaicResult {
  return { mosaicId: name, name, refused, errors: [], warnings: [], castingParts: parts } as unknown as MosaicResult;
}

const spell = (mosaics: MosaicResult[], trance?: { needed: boolean; applied: boolean }) => ({ mosaics, trance }) as unknown as SpellResult;

describe('castingBreakdown', () => {
  it('lists the own time of every mosaic, its options and what a modifier adds', () => {
    const lines = castingBreakdown(spell([
      mosaic('Fürkészés', [{ kind: 'OWN', time: time({ kor: 1 }) }, { kind: 'OPTION', label: 'Lassabb', time: time({ kor: 2 }) }]),
      mosaic('Varázslat nyújtás-óra', [{ kind: 'OWN', time: time({}) }, { kind: 'MODIFIER', time: time({ perc: 5 }) }]),
    ]));
    expect(lines.map((l) => l.text)).toEqual([
      'Fürkészés: 1 kör',
      '+ Lassabb: 2 kör',
      'Varázslat nyújtás-óra, hozzáadott idő: 5 perc',
    ]);
    expect(lines.map((l) => l.extra)).toEqual([false, true, true]);
  });

  it('keeps an own time of 0 when it is the only part, and shows an undefined time with its reason', () => {
    const lines = castingBreakdown(spell([
      mosaic('Üres', [{ kind: 'OWN', time: time({}) }]),
      mosaic('Teleport', [{ kind: 'OWN', time: undef }]),
    ]));
    expect(lines.map((l) => l.text)).toEqual(['Üres: 0', 'Teleport: Nem meghatározott (nincs megadva)']);
  });

  it('leaves refused mosaics out and copes with a result without parts', () => {
    expect(castingBreakdown(spell([mosaic('Elutasított', [{ kind: 'OWN', time: time({ kor: 1 }) }], true)]))).toEqual([]);
    expect(castingBreakdown(spell([{ mosaicId: 'x', name: 'x', refused: false } as unknown as MosaicResult]))).toEqual([]);
    expect(castingBreakdown(null)).toEqual([]);
  });

  it('lists only the wrapper when a wrapper includes the other mosaics, and adds the trance line once', () => {
    const inner = { ...mosaic('Nyíl', [{ kind: 'OWN', time: time({ sz: 2 }) }]), includedInWrapper: true } as MosaicResult;
    const wrapper = mosaic('Végrehajtó varázskör', [{ kind: 'OWN', time: time({ perc: 30 }) }]);
    const lines = castingBreakdown(spell([wrapper, inner], { needed: true, applied: true }));
    expect(lines.map((l) => l.text)).toEqual(['Végrehajtó varázskör: 30 perc', '+10 perc és 1 pszi pont: transz, ha még nincs transzban']);
    expect(lines[1].struck).toBe(false);
  });

  it('shows the trance line struck through when the caster is already in a trance, and none without a drawing', () => {
    const wrapper = mosaic('Végrehajtó varázskör', [{ kind: 'OWN', time: time({ perc: 30 }) }]);
    const struck = castingBreakdown(spell([wrapper], { needed: true, applied: false }));
    expect(struck[1].struck).toBe(true);
    expect(struck[1].text).toContain('már transzban van');
    expect(castingBreakdown(spell([wrapper], { needed: false, applied: false })).length).toBe(1);
  });
});
