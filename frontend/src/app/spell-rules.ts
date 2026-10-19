import { Mosaic, Selection } from './models';

/**
 * The Jelmágia mosaic that is already in the spell, or null. A spell can contain only one Jelmágia mosaic; the engine
 * refuses the later ones, the page greys out the Jelmágia list so the player cannot add a second one.
 */
export function jelmagiaInSpell(selections: Selection[], mosaics: Mosaic[]): Mosaic | null {
  for (const s of selections) {
    const m = mosaics.find((x) => x.id === s.mosaicId);
    if (m?.school === 'jelmagia') return m;
  }
  return null;
}
