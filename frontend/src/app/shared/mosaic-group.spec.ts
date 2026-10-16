import { TestBed } from '@angular/core/testing';
import { MosaicGroup } from './mosaic-group';
import { MOSAIC_GROUPS, GroupChoice } from '../mosaic-groups';
import { Mosaic, Selection } from '../models';

const oselem = {
  id: 'oselem', name: 'Őselem idézése', school: 'anyagi', tier: 2, status: { state: 'complete', missing: [] },
  power: { unit: 'E', baseE: 1, step: { mp: 1, e: 1 } }, cost: { pattern: 'flat' }, source: 'x',
  inputs: [{ id: 'element', label: 'Őselem', type: 'choice', values: ['ostuz', 'osviz'], labels: { ostuz: 'Ős tűz', osviz: 'Ős víz' } }],
} as unknown as Mosaic;
const choices: GroupChoice[] = [
  { key: 'oselem:ostuz', label: 'Ős tűz', description: 'Önállóan létező anyag.', tier: 2, mosaic: oselem, preset: { element: 'ostuz' } },
  { key: 'oselem:osviz', label: 'Ős víz', description: null, tier: 2, mosaic: oselem, preset: { element: 'osviz' } },
];

describe('MosaicGroup', () => {
  function create() {
    const fixture = TestBed.createComponent(MosaicGroup);
    fixture.componentRef.setInput('def', MOSAIC_GROUPS[0]);
    fixture.componentRef.setInput('choices', choices);
    fixture.componentRef.setInput('forms', []);
    fixture.componentRef.setInput('tiers', { anyagi: 3 });
    fixture.componentRef.setInput('groups', []);
    return fixture;
  }
  const choose = (f: ReturnType<typeof create>, key: string) => (f.componentInstance as unknown as { key: { set(k: string): void } }).key.set(key);

  it('shows the form and the description only after an element is chosen, without a second element box', async () => {
    const fixture = create();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="mosaic-form"]')).toBeNull();
    choose(fixture, 'oselem:ostuz');
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="group-description"]')?.textContent?.trim()).toBe('Önállóan létező anyag.');
    expect(el.querySelector('[data-testid="mosaic-form"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="input-element"]')).toBeNull();
  });

  it('adds the member mosaic with the chosen element already filled in', async () => {
    const fixture = create();
    let emitted: Selection | undefined;
    fixture.componentInstance.added.subscribe((s) => (emitted = s));
    choose(fixture, 'oselem:osviz');
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('[data-testid="add-mosaic"]') as HTMLButtonElement).click();
    expect(emitted).toEqual({ mosaicId: 'oselem', inputs: { element: 'osviz' }, k: 0, options: [], extraMp: 0 });
  });
});
