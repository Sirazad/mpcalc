import { formatMaybe, formatTime } from './format';
import { SpellResult } from './models';
import { T } from './texts.hu';

/** One line of the explanation of the total casting time: what is added, and whether it is an extra of a mosaic. */
export interface BreakdownLine { text: string; extra: boolean; /** shown, but not added (already in a trance) */ struck?: boolean; }

/**
 * How the casting time of the spell is put together: the own time of every mosaic, the extra time of its ticked options
 * and what a modifier adds. The lines add up to the total shown next to them. Refused mosaics are left out, and so are the
 * mosaics a wrapper includes (the wrapper's time already contains theirs). A drawing adds the trance as its own last line.
 */
export function castingBreakdown(result: SpellResult | null): BreakdownLine[] {
  const lines: BreakdownLine[] = [];
  for (const m of result?.mosaics ?? []) {
    if (m.refused || m.includedInWrapper) continue;
    const parts = m.castingParts ?? [];
    for (const p of parts) {
      const time = formatMaybe(p.time, formatTime);
      const value = p.time.defined ? p.time.value : null;
      const zero = !!value && value.kind === 'TIME' && Object.values(value.amounts ?? {}).every((n) => n === 0);
      if (p.kind === 'OWN') {
        // a mosaic that only adds time (a modifier) has an own time of 0: that line says nothing
        if (zero && parts.length > 1) continue;
        lines.push({ text: `${m.name}: ${time}`, extra: false });
      } else if (p.kind === 'OPTION') {
        lines.push({ text: `+ ${p.label ?? ''}: ${time}`, extra: true });
      } else {
        lines.push({ text: `${m.name}, ${T.spell.modifierAdds}: ${time}`, extra: true });
      }
    }
  }
  if (result?.trance?.needed) {
    lines.push({ text: result.trance.applied ? T.spell.tranceLine : `${T.spell.tranceLine} ${T.spell.tranceDone}`, extra: true, struck: !result.trance.applied });
  }
  return lines;
}
