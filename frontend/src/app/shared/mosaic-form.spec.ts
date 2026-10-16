import { TestBed } from '@angular/core/testing';
import { MosaicForm } from './mosaic-form';
import { Form, MaterialGroup, Mosaic, Selection } from '../models';

const groups: MaterialGroup[] = [{ id: 'I', name: 'I. csoport', examples: ['Minden más nem mágikus anyag.'] }, { id: 'II', name: 'II. csoport', examples: ['Nemtelen fémek.', 'Ércek.'] }];
const forms: Form[] = [{ id: 'small', name: 'Kicsi', tier: 2, duration: null, regeneration: '0,5 szegmens', movable: false, description: 'Támadó varázslat.\n\nMásodik bekezdés.' }, { id: 'big', name: 'Nagy', tier: 4, duration: null, movable: true }];
const oselem: Mosaic = {
  id: 'oselem', name: 'Őselem idézése', school: 'anyagi', tier: 2, status: { state: 'complete', missing: [] },
  power: { unit: 'E', baseE: 1, step: { mp: 4, e: 1 } }, cost: { pattern: 'flat' }, source: 'x',
  inputs: [{ id: 'element', label: 'Őselem', type: 'choice', values: ['ostuz', 'osviz'] }, { id: 'form', label: 'Forma', type: 'form' }],
};

describe('MosaicForm', () => {
  function create(mosaic: Mosaic, tiers: Record<string, number>) {
    const fixture = TestBed.createComponent(MosaicForm);
    fixture.componentRef.setInput('mosaic', mosaic);
    fixture.componentRef.setInput('forms', forms);
    fixture.componentRef.setInput('tiers', tiers);
    fixture.componentRef.setInput('groups', groups);
    return fixture;
  }

  it('shows one box per input, the power step, and keeps Add disabled until the inputs are filled', async () => {
    const fixture = create(oselem, { anyagi: 2 });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="input-element"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="input-form"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="power-e"]')?.textContent?.trim()).toBe('1 E');
    expect((el.querySelector('[data-testid="add-mosaic"]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('shows the Hungarian names of a choice and the description of the chosen value', async () => {
    const withTexts: Mosaic = {
      ...oselem,
      inputs: [{ id: 'element', label: 'Őselem', type: 'choice', values: ['ostuz', 'osviz'],
        labels: { ostuz: 'Ős tűz', osviz: 'Ős víz' }, descriptions: { osviz: 'Teljesen tiszta átlátszó víz.' } }],
    };
    const fixture = create(withTexts, { anyagi: 2 });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="description-element"]')).toBeNull();
    (fixture.componentInstance as unknown as { set(id: string, v: string): void }).set('element', 'osviz');
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="description-element"]')?.textContent?.trim()).toBe('Teljesen tiszta átlátszó víz.');
    (fixture.componentInstance as unknown as { set(id: string, v: string): void }).set('element', 'ostuz');
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="description-element"]')).toBeNull();
  });

  it('shows the regenerációs idő of the chosen form, and nothing for a form without one', async () => {
    const fixture = create({ ...oselem, inputs: [{ id: 'form', label: 'Forma', type: 'form' }] }, { anyagi: 4 });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const set = (v: string) => (fixture.componentInstance as unknown as { set(id: string, v: string): void }).set('form', v);
    expect(el.querySelector('[data-testid="regeneration-form"]')).toBeNull();
    set('small');
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="regeneration-form"]')?.textContent?.trim()).toBe('Regenerációs idő: 0,5 szegmens');
    expect(el.querySelector('[data-testid="form-description-form"]')?.textContent).toContain('Támadó varázslat.');
    expect(el.querySelector('[data-testid="form-movable-form"]')?.textContent).toContain('Helyhez kötött');
    set('big');
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="regeneration-form"]')).toBeNull();
    expect(el.querySelector('[data-testid="form-description-form"]')).toBeNull();
    expect(el.querySelector('[data-testid="form-movable-form"]')?.textContent?.trim()).toBe('Mozgatható.');
  });

  it('shows the examples of the chosen material group under its row and the note about the magical materials', async () => {
    const fixture = create({ ...oselem, inputs: [{ id: 'composition', label: 'Összetétel', type: 'composition' }] }, { anyagi: 2 });
    fixture.componentRef.setInput('materialNote', 'Mágikus anyagok: Mithrill.');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    // a composition starts with one row, for the first material group
    expect(el.querySelector('[data-testid="group-examples-0"]')?.textContent?.trim()).toBe('Minden más nem mágikus anyag.');
    (el.querySelector('[data-testid="add-row"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="group-examples-1"]')?.textContent).toContain('Nemtelen fémek.');
    expect(el.querySelector('[data-testid="group-examples-1"]')?.textContent).toContain('Ércek.');
    expect(el.querySelector('[data-testid="material-note"]')?.textContent?.trim()).toBe('Mágikus anyagok: Mithrill.');
  });

  it('raises the power step and emits the selection with k', async () => {
    const fixture = create({ ...oselem, inputs: [] }, { anyagi: 2 });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    let emitted: Selection | undefined;
    fixture.componentInstance.added.subscribe((s) => (emitted = s));
    (el.querySelector('[data-testid="k-up"]') as HTMLButtonElement).click();
    (el.querySelector('[data-testid="k-up"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="power-e"]')?.textContent?.trim()).toBe('3 E');
    (el.querySelector('[data-testid="add-mosaic"]') as HTMLButtonElement).click();
    expect(emitted).toEqual({ mosaicId: 'oselem', inputs: {}, k: 2, options: [], extraMp: 0 });
  });

  it('does not allow adding an incomplete mosaic', async () => {
    const fixture = create({ ...oselem, inputs: [], status: { state: 'incomplete', missing: ['cost'] } }, { anyagi: 2 });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect((el.querySelector('[data-testid="add-mosaic"]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('offers each material group only once and disables adding rows when all groups are used', async () => {
    const fixture = TestBed.createComponent(MosaicForm);
    const three = [...groups, { id: 'III', name: 'III. csoport', examples: [] }];
    const comp: Mosaic = { ...oselem, inputs: [{ id: 'composition', label: 'Összetétel', type: 'composition' }], power: null };
    fixture.componentRef.setInput('mosaic', comp);
    fixture.componentRef.setInput('forms', forms);
    fixture.componentRef.setInput('tiers', { anyagi: 2 });
    fixture.componentRef.setInput('groups', three);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const addRow = () => el.querySelector('[data-testid="add-row"]') as HTMLButtonElement;
    expect(addRow().disabled).toBe(false);
    addRow().click();
    await fixture.whenStable();
    addRow().click();
    await fixture.whenStable();
    expect(el.querySelectorAll('.composition .row').length).toBe(3);
    expect(addRow().disabled).toBe(true);
  });

  it('accepts only a whole number of extra MP on an extension', async () => {
    const fixture = TestBed.createComponent(MosaicForm);
    const extension: Mosaic = {
      ...oselem, id: 'nyujtas-ora', school: 'ido', inputs: [], power: null,
      modifier: { appliesToWholeSpell: true, durationPerExtraMp: { kind: 'time', amounts: { ora: 1 } } },
    };
    fixture.componentRef.setInput('mosaic', extension);
    fixture.componentRef.setInput('forms', forms);
    fixture.componentRef.setInput('tiers', { ido: 2 });
    fixture.componentRef.setInput('groups', groups);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const add = () => el.querySelector('[data-testid="add-mosaic"]') as HTMLButtonElement;
    const box = el.querySelector('[data-testid="extra-mp"]') as HTMLInputElement;
    const type = async (v: string) => { box.value = v; box.dispatchEvent(new Event('input')); await fixture.whenStable(); };
    expect(add().disabled).toBe(false);
    await type('1.5');
    expect(add().disabled).toBe(true);
    expect(el.querySelector('[data-testid="extra-mp-error"]')?.textContent).toContain('egész szám');
    await type('2');
    expect(add().disabled).toBe(false);
    expect(el.querySelector('[data-testid="extra-mp-error"]')).toBeNull();
  });
});
