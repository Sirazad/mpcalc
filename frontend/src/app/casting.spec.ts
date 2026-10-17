import { castingBreakdown } from './casting';
import { CastingPart, Maybe, MosaicResult, TimeValue } from './models';

const time = (amounts: Record<string, number>): Maybe<TimeValue> => ({ defined: true, value: { kind: 'TIME', amounts } });
const undef = { defined: false, reason: { code: 'x', text: 'nincs megadva' } } as unknown as Maybe<TimeValue>;
function mosaic(name: string, parts: CastingPart[], refused = false): MosaicResult {
  return { mosaicId: name, name, refused, errors: [], warnings: [], castingParts: parts } as unknown as MosaicResult;
}

describe('castingBreakdown', () => {
  it('lists the own time of every mosaic, its options and what a modifier adds', () => {
    const lines = castingBreakdown([
      mosaic('Fürkészés', [{ kind: 'OWN', time: time({ kor: 1 }) }, { kind: 'OPTION', label: 'Lassabb', time: time({ kor: 2 }) }]),
      mosaic('Varázslat nyújtás-óra', [{ kind: 'OWN', time: time({}) }, { kind: 'MODIFIER', time: time({ perc: 5 }) }]),
    ]);
    expect(lines.map((l) => l.text)).toEqual([
      'Fürkészés: 1 kör',
      '+ Lassabb: 2 kör',
      'Varázslat nyújtás-óra, hozzáadott idő: 5 perc',
    ]);
    expect(lines.map((l) => l.extra)).toEqual([false, true, true]);
  });

  it('keeps an own time of 0 when it is the only part, and shows an undefined time with its reason', () => {
    const lines = castingBreakdown([
      mosaic('Üres', [{ kind: 'OWN', time: time({}) }]),
      mosaic('Teleport', [{ kind: 'OWN', time: undef }]),
    ]);
    expect(lines.map((l) => l.text)).toEqual(['Üres: 0', 'Teleport: Nem meghatározott (nincs megadva)']);
  });

  it('leaves refused mosaics out and copes with a result without parts', () => {
    expect(castingBreakdown([mosaic('Elutasított', [{ kind: 'OWN', time: time({ kor: 1 }) }], true)])).toEqual([]);
    expect(castingBreakdown([{ mosaicId: 'x', name: 'x', refused: false } as unknown as MosaicResult])).toEqual([]);
  });
});
