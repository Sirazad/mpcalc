import { Component, computed, input, linkedSignal, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { CompositionRow, InputValues, defaultInputs, formsAllowed, groupsFor, missingInputs, powerAt, toSelection, unusedGroup } from '../inputs';
import { Form, MaterialGroup, Mosaic, MosaicInput, Selection } from '../models';
import { T } from '../texts.hu';

/** The boxes of one mosaic, generated from its `inputs`, `power`, `options` and `modifier`. */
@Component({
  selector: 'app-mosaic-form',
  imports: [FormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './mosaic-form.html',
  styleUrl: './mosaic-form.scss',
})
export class MosaicForm {
  protected readonly T = T;
  readonly mosaic = input.required<Mosaic>();
  readonly forms = input.required<Form[]>();
  readonly tiers = input.required<Record<string, number>>();
  readonly groups = input.required<MaterialGroup[]>();
  /** Values the caller has fixed (for example the element chosen in a group): they start filled and their boxes are hidden. */
  readonly preset = input<Record<string, string>>({});
  /** Text about the magical materials, shown under the material rows. */
  readonly materialNote = input<string | null>(null);
  readonly added = output<Selection>();

  protected readonly values = linkedSignal<InputValues>(() => ({ ...defaultInputs(this.mosaic()), ...this.preset() }));
  protected readonly visibleInputs = computed(() => (this.mosaic().inputs ?? []).filter((i) => !(i.id in this.preset())));
  protected readonly k = linkedSignal(() => { this.mosaic(); return 0; });
  protected readonly options = linkedSignal<string[]>(() => { this.mosaic(); return []; });
  protected readonly extraMp = linkedSignal(() => { this.mosaic(); return 0; });

  protected readonly allowedForms = computed(() => formsAllowed(this.forms(), this.tiers(), this.mosaic().school));
  protected readonly power = computed(() => powerAt(this.mosaic(), this.k()));
  protected readonly canRaisePower = computed(() => !!this.mosaic().power?.step);
  protected readonly isModifier = computed(() => !!this.mosaic().modifier?.durationPerExtraMp);
  /** Extra MP is a whole number of MP, 0 or more (a decimal is refused, never cut). */
  protected readonly extraMpValid = computed(() => Number.isInteger(this.extraMp()) && this.extraMp() >= 0);
  protected readonly ready = computed(() => missingInputs(this.mosaic(), this.values()).length === 0
    && this.mosaic().status.state !== 'incomplete' && (!this.isModifier() || this.extraMpValid()));

  /** The regenerációs idő of the chosen form, if it has one. */
  protected examplesOf(groupId: string): string { return this.groups().find((g) => g.id === groupId)?.examples.join('\n') ?? ''; }
  /** The values of a choice the character may use: a value can need a tier in another school. */
  protected availableValues(i: MosaicInput): string[] {
    return (i.values ?? []).filter((v) => (i.requires?.[v] ?? []).every((r) => (this.tiers()[r.school] ?? 0) >= r.tier));
  }
  protected formOf(formId: unknown): Form | undefined { return this.forms().find((f) => f.id === formId); }
  protected regenerationOf(formId: unknown): string | null { return this.formOf(formId)?.regeneration ?? null; }

  protected set(id: string, v: string | number | null) { this.values.update((x) => ({ ...x, [id]: v })); }

  protected rows(id: string): CompositionRow[] { return (this.values()[id] as CompositionRow[]) ?? []; }
  protected setRows(id: string, rows: CompositionRow[]) { this.values.update((x) => ({ ...x, [id]: rows })); }
  protected canAddRow(id: string) { return unusedGroup(this.rows(id), this.groups()) !== null; }
  protected groupsForRow(id: string, index: number) { return groupsFor(this.rows(id), this.groups(), index); }
  protected addRow(id: string) {
    const group = unusedGroup(this.rows(id), this.groups());
    if (group) this.setRows(id, [...this.rows(id), { group, massG: null }]);
  }
  protected removeRow(id: string, i: number) { this.setRows(id, this.rows(id).filter((_, j) => j !== i)); }
  protected setRow(id: string, i: number, patch: Partial<CompositionRow>) {
    this.setRows(id, this.rows(id).map((r, j) => (j === i ? { ...r, ...patch } : r)));
  }

  protected toggleOption(id: string, on: boolean) {
    this.options.update((o) => (on ? [...o, id] : o.filter((x) => x !== id)));
  }

  protected add() {
    this.added.emit(toSelection(this.mosaic(), this.values(), this.k(), this.options(), this.extraMp()));
  }

  protected step(delta: number) { this.k.update((k) => Math.max(0, k + delta)); }
}
