import { DerivedValue, Fraction, Maybe, MosaicResult, Resistance, School, TimeValue } from './models';
import { T } from './texts.hu';

const UNIT_ORDER = ['ev', 'nap', 'ora', 'perc', 'kor', 'sz'];
const number = new Intl.NumberFormat('hu-HU', { maximumFractionDigits: 6 });

/** A number as the backend gave it: exact decimal when it terminates, otherwise the fraction. */
export function formatFraction(f: Fraction): string {
  return f.decimal !== null ? number.format(f.decimal) : `${f.numerator}/${f.denominator}`;
}

export function formatNumber(n: number): string {
  return number.format(n);
}

/** Time value from the engine ("1 óra 2 kör", "Egyszeri", ...). */
export function formatTime(t: TimeValue): string {
  switch (t.kind) {
    case 'TIME': {
      const parts = UNIT_ORDER.filter((u) => (t.amounts?.[u] ?? 0) > 0).map((u) => `${t.amounts![u]} ${T.time.units[u]}`);
      return parts.length ? parts.join(' ') : '0';
    }
    case 'ONE_OFF': return T.time.oneOff;
    case 'PERMANENT': return T.time.permanent;
    case 'FORM_DEPENDENT': return T.time.formDependent;
    case 'UNTIL_BROKEN': return T.time.untilBroken;
    default: return T.time.undefined;
  }
}

const RESISTANCE_NAMES: Record<string, string> = {
  none: 'nincs',
  concealment: 'Leplezés',
  spellMp: 'Varázslat MP',
  armorSfe: 'SFÉ',
  naturalMaterialMagic: 'Természetes anyagi mágia',
};

export function formatResistance(r: Resistance, schools: School[]): string {
  // the engine has resolved which element resists this cast (and its name, from the labels of the choice input)
  const element = r.elementLabel ?? r.element ?? '';
  if (r.kind === 'oppositeElement') return `${element} (ellentétes elem)`;
  if (r.kind === 'givenParaElement') return `${element} (ugyanaz a paraelem)`;
  if (r.kind === 'elementalForce') return 'Elemi erő';
  if (r.kind === 'school') {
    const name = schools.find((s) => s.id === r.school)?.name ?? r.school ?? '';
    return `Iskola: ${name}`;
  }
  return RESISTANCE_NAMES[r.kind] ?? r.kind;
}

/** The strength of a mosaic in the spell: végtelen, nincs (the mosaic has none), or the number with its unit. */
export function formatStrength(m: Pick<MosaicResult, 'e' | 'infinitePower' | 'powerUnit'>): string {
  if (m.infinitePower) return T.spell.infinite;
  if (!m.e.defined && m.e.reason?.code === 'engine.reason.noE') return T.spell.noStrength;
  return formatMaybe(m.e, (v) => `${formatNumber(v)} ${m.powerUnit}`);
}

/** A calculated value of a mosaic: kilos as kilos and, from one tonne up, in tonnes too ("377 500 kg (377,5 tonna)"). */
export function formatDerived(d: DerivedValue): string {
  return formatMaybe(d.value, (v) => (d.unit === 'kg' && v >= 1000 ? `${formatNumber(v)} kg (${formatNumber(v / 1000)} tonna)` : `${formatNumber(v)} ${d.unit}`));
}

/** A value that may be undefined: the text, or "Nem meghatározott" with the engine's reason. */
export function formatMaybe<V>(m: Maybe<V> | null | undefined, fn: (v: V) => string): string {
  if (!m) return '';
  return m.defined ? fn(m.value as V) : `${T.undefinedValue}${m.reason ? ` (${m.reason.text})` : ''}`;
}
