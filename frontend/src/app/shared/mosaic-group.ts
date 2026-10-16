import { Component, computed, input, linkedSignal, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { GroupChoice, MosaicGroupDef } from '../mosaic-groups';
import { Form, MaterialGroup, Selection } from '../models';
import { T } from '../texts.hu';
import { MosaicForm } from './mosaic-form';

/** One drop-down for the mosaics of a group (for example Elemek idézése); the chosen entry decides which mosaic is added. */
@Component({
  selector: 'app-mosaic-group',
  imports: [FormsModule, MatFormFieldModule, MatSelectModule, MosaicForm],
  templateUrl: './mosaic-group.html',
  styleUrl: './mosaic-group.scss',
})
export class MosaicGroup {
  protected readonly T = T;
  readonly def = input.required<MosaicGroupDef>();
  readonly choices = input.required<GroupChoice[]>();
  readonly forms = input.required<Form[]>();
  readonly tiers = input.required<Record<string, number>>();
  readonly groups = input.required<MaterialGroup[]>();
  readonly added = output<Selection>();

  protected readonly key = linkedSignal<string | null>(() => { this.def(); return null; });
  protected readonly chosen = computed(() => this.choices().find((c) => c.key === this.key()) ?? null);
}
