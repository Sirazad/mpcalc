import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { errorText } from '../api.service';
import { LoadDialog } from '../dialogs/load-dialog';
import { OverspendDialog } from '../dialogs/overspend-dialog';
import { SaveDialog } from '../dialogs/save-dialog';
import { ValidateDialog } from '../dialogs/validate-dialog';
import { formatFraction, formatMaybe, formatNumber, formatResistance, formatStrength, formatTime } from '../format';
import { castingBreakdown } from '../casting';
import { DetailItem, chosenDescription, describeSelection } from '../inputs';
import { Mosaic, MosaicResult, SavedSpell, Selection } from '../models';
import { SessionStore } from '../session.store';
import { ChatBox } from '../shared/chat-box';
import { MosaicForm } from '../shared/mosaic-form';
import { MosaicGroup } from '../shared/mosaic-group';
import { ListEntry, listEntries } from '../mosaic-groups';
import { SpellStore } from '../spell.store';
import { T } from '../texts.hu';

@Component({
  selector: 'app-builder',
  imports: [MatButtonModule, MatCardModule, RouterLink, MosaicForm, MosaicGroup, ChatBox],
  templateUrl: './builder.html',
  styleUrl: './builder.scss',
})
export class Builder {
  protected readonly T = T;
  protected readonly session = inject(SessionStore);
  protected readonly spell = inject(SpellStore);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);

  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly school = signal<string | null>(null);

  /** The open entry of the list: a mosaic id, or the id of a group. */
  protected readonly openKey = signal<string | null>(null);
  protected readonly entries = computed<ListEntry[]>(() => listEntries(this.school() ?? '', this.session.mosaics()));
  protected readonly canCast = computed(() => this.spell.selections().length > 0 && !!this.spell.result()?.valid && !this.spell.busy());

  constructor() { void this.open(); }

  private async open() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    try {
      if (this.session.character()?.id !== id) {
        await this.spell.clear();
        await this.session.openCharacter(id);
      }
      await this.spell.listSaved();
    } catch (e) {
      this.error.set(errorText(e, T.errors.generic));
    } finally {
      this.loading.set(false);
    }
  }

  protected pickSchool(id: string) { this.school.set(id); this.openKey.set(null); }
  /** Opens the details of an entry (a mosaic or a group) right under it; clicking the open one closes it. */
  protected pick(e: ListEntry) { this.openKey.set(this.openKey() === e.key ? null : e.key); }

  protected async added(s: Selection) {
    await this.spell.add(s);
    this.openKey.set(null);
  }

  protected async cast() {
    const r = await this.spell.cast();
    if (r && r.overspentMp > 0) {
      const names = r.schools.map((id) => this.session.schools().find((s) => s.id === id)?.name ?? id);
      this.dialog.open(OverspendDialog, { data: { overspentMp: formatNumber(r.overspentMp), schools: names } });
    }
  }

  protected async newSpell() { await this.spell.clear(); }

  protected save() {
    this.dialog.open(SaveDialog).afterClosed().subscribe(async (name?: string) => {
      if (!name) return;
      try {
        await this.spell.save(name);
        this.snack.open(T.save.saved, undefined, { duration: 3000 });
      } catch (e) {
        this.error.set(errorText(e, T.errors.generic));
      }
    });
  }

  protected load() {
    this.dialog.open(LoadDialog).afterClosed().subscribe(async (s?: SavedSpell) => { if (s) await this.spell.load(s); });
  }

  protected async reload() {
    await this.session.reload();
    await this.spell.listSaved();
    await this.spell.calculate();
  }

  protected async validate() {
    try {
      const r = await this.spell.validate();
      if (!r) return;
      this.dialog.open(ValidateDialog, { data: r }).afterClosed().subscribe(async (reload?: boolean) => { if (reload) await this.reload(); });
    } catch (e) {
      this.error.set(errorText(e, T.errors.generic));
    }
  }

  /** What the player entered for the mosaic at this place of the spell (shown right under the mosaic). */
  protected details(index: number): DetailItem[] {
    const sel = this.spell.selections()[index];
    if (!sel) return [];
    return describeSelection(this.session.mosaics().find((m) => m.id === sel.mosaicId), sel, this.session.forms(), this.session.groups());
  }

  // Text of backend values for the spell panel.
  protected mp(m: MosaicResult) { return formatMaybe(m.mp, formatFraction); }
  protected e(m: MosaicResult) { return formatStrength(m); }
  /** The effect text of the mosaic; when it has none, the description of the chosen value (Őselem idézése: the element). */
  protected effect(m: MosaicResult, index: number) {
    const sel = this.spell.selections()[index];
    const own = formatMaybe(m.effect, (v) => v);
    if (m.effect?.defined || !sel) return own;
    return chosenDescription(this.session.mosaics().find((x) => x.id === sel.mosaicId), sel) ?? own;
  }
  protected resistance(m: MosaicResult) { return formatMaybe(m.resistance, (v) => formatResistance(v, this.session.schools())); }
  protected casting(m: MosaicResult) { return formatMaybe(m.castingTime, formatTime); }
  protected duration(m: MosaicResult) { return formatMaybe(m.duration, formatTime); }
  /** How the total casting time is put together (one line per mosaic and per extra). */
  protected castingLines() { return castingBreakdown(this.spell.result()); }
  protected async setTrance(on: boolean) { this.spell.inTrance.set(on); await this.spell.calculate(); }
  protected async setZoneSign(on: boolean) { this.spell.viaZoneSign.set(on); await this.spell.calculate(); }
  /** The description of the chosen school (its lore and main rules), if it has one. */
  protected readonly schoolDescription = computed(() => this.session.schools().find((s) => s.id === this.school())?.description ?? null);
  protected totalMp() { return formatMaybe(this.spell.result()?.totalMp, formatFraction); }
  protected totalCasting() { return formatMaybe(this.spell.result()?.castingTime, formatTime); }
  protected totalDuration() { return formatMaybe(this.spell.result()?.duration, formatTime); }
  protected remaining() { return formatMaybe(this.spell.result()?.remainingMp, formatFraction); }
  protected overspent() { const o = this.spell.overspent(); return o ? formatFraction(o) : null; }
}
