import { Injectable, computed, inject, signal } from '@angular/core';
import { Api, errorText } from './api.service';
import { SessionStore } from './session.store';
import { CalculateResult, CastResponse, SavedSpell, Selection, ValidateResponse } from './models';
import { T } from './texts.hu';

/** The current spell: the selections the player added, and the backend's calculation of them. No number is computed here. */
@Injectable({ providedIn: 'root' })
export class SpellStore {
  private readonly api = inject(Api);
  private readonly session = inject(SessionStore);

  readonly selections = signal<Selection[]>([]);
  /** The caster is already in a trance: a drawing then adds no trance time (10 minutes) to the casting time. */
  readonly inTrance = signal(false);
  /** The spell is cast through a Zóna varázsjel: +5 MP once and +1 kör. */
  readonly viaZoneSign = signal(false);
  readonly result = signal<CalculateResult | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  /** Ids of the saved spells the page knows about (sent to validate). */
  readonly knownSpellIds = signal<number[]>([]);

  readonly overspent = computed(() => this.result()?.overspentMp ?? null);

  async add(s: Selection) {
    this.selections.update((l) => [...l, s]);
    await this.calculate();
  }

  async remove(index: number) {
    this.selections.update((l) => l.filter((_, i) => i !== index));
    await this.calculate();
  }

  async clear() {
    this.selections.set([]);
    this.inTrance.set(false);
    this.viaZoneSign.set(false);
    this.result.set(null);
    this.error.set(null);
  }

  async load(spell: SavedSpell) {
    this.selections.set(spell.content.selections ?? []);
    await this.calculate();
  }

  /** Asks the backend to calculate the whole spell for the open character. */
  async calculate() {
    const c = this.session.character();
    if (!c || this.selections().length === 0) { this.result.set(null); return; }
    this.busy.set(true);
    try {
      this.result.set(await this.api.calculate(c.id, this.selections(), this.inTrance(), this.viaZoneSign()));
      this.error.set(null);
    } catch (e) {
      this.result.set(null);
      this.error.set(errorText(e, T.errors.generic));
    } finally {
      this.busy.set(false);
    }
  }

  /** Casts: current MP changes only; the backend reports an overspend (the page then shows the warning). */
  async cast(): Promise<CastResponse | null> {
    const c = this.session.character();
    if (!c) return null;
    this.busy.set(true);
    try {
      const r = await this.api.cast(c.id, this.selections(), this.inTrance(), this.viaZoneSign());
      this.session.setCurrentMp(r.currentMp);
      this.error.set(null);
      await this.calculate();
      return r;
    } catch (e) {
      this.error.set(errorText(e, T.errors.generic));
      return null;
    } finally {
      this.busy.set(false);
    }
  }

  async listSaved(): Promise<SavedSpell[]> {
    const c = this.session.character();
    if (!c) return [];
    const list = await this.api.spells(c.id);
    this.knownSpellIds.set(list.map((s) => s.id));
    return list;
  }

  async save(name: string): Promise<SavedSpell | null> {
    const c = this.session.character();
    if (!c) return null;
    const saved = await this.api.saveSpell(c.id, name, { selections: this.selections() });
    this.knownSpellIds.update((ids) => [...ids, saved.id]);
    return saved;
  }

  async deleteSaved(id: number) {
    await this.api.deleteSpell(id);
    this.knownSpellIds.update((ids) => ids.filter((i) => i !== id));
  }

  /** Sends what the page holds to the backend, which reports every difference from its own data. */
  async validate(): Promise<ValidateResponse | null> {
    const c = this.session.character();
    if (!c) return null;
    const forms: Record<string, number> = {};
    for (const f of this.session.forms()) forms[f.id] = f.tier;
    const total = this.result()?.totalMp;
    return this.api.validate({
      characterId: c.id,
      character: { maxMp: c.maxMp, currentMp: c.currentMp, tiers: c.tiers },
      mosaicVersions: this.session.versions(),
      forms,
      selections: this.selections(),
      inTrance: this.inTrance(),
      viaZoneSign: this.viaZoneSign(),
      displayedTotalMp: total?.defined && total.value ? { numerator: total.value.numerator, denominator: total.value.denominator } : null,
      savedSpellIds: this.knownSpellIds(),
    });
  }
}
