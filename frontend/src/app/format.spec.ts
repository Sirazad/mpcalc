import { formatDerived, formatFraction, formatMaybe, formatResistance, formatStrength, formatTime } from './format';
import { School } from './models';

describe('formatDerived', () => {
  const kg = (value: number) => ({ id: 'capacityKg', label: 'Áteresztőképesség', unit: 'kg', value: { defined: true, value } });
  it('writes kilos, and from one tonne up the tonnes too', () => {
    expect(formatDerived(kg(377500))).toBe('377\u00a0500 kg (377,5 tonna)');
    expect(formatDerived(kg(120000))).toBe('120\u00a0000 kg (120 tonna)');
    expect(formatDerived(kg(750))).toBe('750 kg');
  });
  it('keeps another unit as it is and shows an undefined value with its reason', () => {
    expect(formatDerived({ id: 'x', label: 'x', unit: 'm', value: { defined: true, value: 12 } })).toBe('12 m');
    expect(formatDerived({ id: 'x', label: 'x', unit: 'kg', value: { defined: false, reason: { code: 'c', args: [], text: 'nincs megadva' } } })).toContain('Nem meghatározott');
  });
});

describe('formatStrength', () => {
  const base = { infinitePower: false, powerUnit: 'E' };
  it('says végtelen, nincs, or the number', () => {
    expect(formatStrength({ ...base, infinitePower: true, e: { defined: false } })).toBe('végtelen');
    expect(formatStrength({ ...base, e: { defined: false, reason: { code: 'engine.reason.noE', args: [], text: 'Ehhez a mozaikhoz nincs erősség (E) megadva.' } } })).toBe('nincs');
    expect(formatStrength({ ...base, e: { defined: true, value: 3 } })).toBe('3 E');
  });
  it('keeps the reason for a strength that is missing in the source', () => {
    const t = formatStrength({ ...base, e: { defined: false, reason: { code: 'engine.reason.powerUndefined', args: [], text: 'Az erősség a forrásban nem meghatározott.' } } });
    expect(t).toContain('Nem meghatározott');
    expect(t).toContain('forrásban');
  });
});

describe('format', () => {
  it('shows a terminating fraction as a decimal and the others as a fraction', () => {
    expect(formatFraction({ numerator: 49, denominator: 2, decimal: 24.5 })).toBe('24,5');
    expect(formatFraction({ numerator: 10, denominator: 1, decimal: 10 })).toBe('10');
    expect(formatFraction({ numerator: 1, denominator: 3, decimal: null })).toBe('1/3');
  });

  it('shows time units in order, not normalised', () => {
    expect(formatTime({ kind: 'TIME', amounts: { kor: 2, ora: 1 } })).toBe('1 óra 2 kör');
    expect(formatTime({ kind: 'TIME', amounts: {} })).toBe('0');
    expect(formatTime({ kind: 'ONE_OFF' })).toBe('Egyszeri');
    expect(formatTime({ kind: 'FORM_DEPENDENT' })).toBe('Forma függő');
    expect(formatTime({ kind: 'UNDEFINED' })).toBe('Nem meghatározott');
  });

  it('shows an undefined value with the engine reason', () => {
    expect(formatMaybe({ defined: false, reason: { code: 'engine.reason.noE', args: [], text: 'Nincs erősség.' } }, (v: string) => v)).toBe('Nem meghatározott (Nincs erősség.)');
    expect(formatMaybe({ defined: true, value: 'x' }, (v: string) => v)).toBe('x');
  });

  it('names a school resistance with the school name and keeps unknown kinds as stored', () => {
    const schools: School[] = [{ id: 'mental', name: 'Mentál mágia', hasTiers: true }];
    expect(formatResistance({ kind: 'school', school: 'mental' }, schools)).toBe('Iskola: Mentál mágia');
    expect(formatResistance({ kind: 'concealment', school: null }, schools)).toBe('Leplezés');
    expect(formatResistance({ kind: 'armorSfe', school: null }, schools)).toBe('SFÉ');
    expect(formatResistance({ kind: 'naturalMaterialMagic', school: null }, schools)).toBe('Természetes anyagi mágia');
    expect(formatResistance({ kind: 'lifeForce', school: null }, schools)).toBe('lifeForce');
  });

  it('names the element that resists an Elemi mosaic in Hungarian', () => {
    expect(formatResistance({ kind: 'oppositeElement', school: null, element: 'osviz', elementLabel: 'Ős víz' }, [])).toBe('Ős víz (ellentétes elem)');
    expect(formatResistance({ kind: 'oppositeElement', school: null, element: 'oslevego', elementLabel: 'Ős levegő' }, [])).toBe('Ős levegő (ellentétes elem)');
    expect(formatResistance({ kind: 'givenParaElement', school: null, element: 'setet', elementLabel: 'Elemi sötétség' }, [])).toBe('Elemi sötétség (ugyanaz a paraelem)');
    // without a label the stored id is shown
    expect(formatResistance({ kind: 'givenParaElement', school: null, element: 'setet' }, [])).toBe('setet (ugyanaz a paraelem)');
    expect(formatResistance({ kind: 'elementalForce', school: null }, [])).toBe('Elemi erő');
  });
});
