import { jelmagiaInSpell } from './spell-rules';
import { Mosaic, Selection } from './models';

function mosaic(id: string, school: string): Mosaic {
  return { id, name: id, school, tier: 1, status: { state: 'complete', missing: [] }, power: null, cost: { pattern: 'flat' }, source: 'x' } as Mosaic;
}
const sel = (mosaicId: string): Selection => ({ mosaicId, inputs: {}, k: 1, options: [], extraMp: 0 });
const mosaics = [mosaic('jel-szimbolum', 'jelmagia'), mosaic('jel-belyeg', 'jelmagia'), mosaic('alap-furkeszes', 'alap')];

describe('jelmagiaInSpell', () => {
  it('is null when the spell has no Jelmágia mosaic', () => {
    expect(jelmagiaInSpell([], mosaics)).toBeNull();
    expect(jelmagiaInSpell([sel('alap-furkeszes')], mosaics)).toBeNull();
  });

  it('returns the Jelmágia mosaic that is in the spell, wherever it is', () => {
    expect(jelmagiaInSpell([sel('alap-furkeszes'), sel('jel-belyeg')], mosaics)?.id).toBe('jel-belyeg');
  });

  it('returns the first one when a loaded spell has several', () => {
    expect(jelmagiaInSpell([sel('jel-szimbolum'), sel('jel-belyeg')], mosaics)?.id).toBe('jel-szimbolum');
  });
});
