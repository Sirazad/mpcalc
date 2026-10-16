import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { SavedSpell } from '../models';
import { SpellStore } from '../spell.store';
import { T } from '../texts.hu';

/** Lists the saved spells of the open character; closes with the chosen one. */
@Component({
  selector: 'app-load-dialog',
  imports: [MatButtonModule, MatDialogModule, MatListModule],
  template: `
    <h2 mat-dialog-title>{{ T.load.title }}</h2>
    <mat-dialog-content>
      @if (spells().length === 0) { <p>{{ T.load.none }}</p> }
      <mat-list>
        @for (s of spells(); track s.id) {
          <mat-list-item [attr.data-testid]="'saved-' + s.id">
            <span matListItemTitle>{{ s.name }}</span>
            <span matListItemMeta>
              <button mat-button (click)="ref.close(s)" data-testid="load-spell">{{ T.load.load }}</button>
              <button mat-button (click)="remove(s)">{{ T.load.delete }}</button>
            </span>
          </mat-list-item>
        }
      </mat-list>
    </mat-dialog-content>
    <mat-dialog-actions><button mat-button mat-dialog-close>{{ T.load.close }}</button></mat-dialog-actions>
  `,
})
export class LoadDialog {
  protected readonly T = T;
  protected readonly ref = inject(MatDialogRef<LoadDialog, SavedSpell>);
  private readonly store = inject(SpellStore);
  protected readonly spells = signal<SavedSpell[]>([]);

  constructor() { void this.store.listSaved().then((l) => this.spells.set(l)); }

  protected async remove(s: SavedSpell) {
    await this.store.deleteSaved(s.id);
    this.spells.update((l) => l.filter((x) => x.id !== s.id));
  }
}
