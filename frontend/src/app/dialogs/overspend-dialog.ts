import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { T } from '../texts.hu';

export interface OverspendData { overspentMp: string; schools: string[]; }

/** Warning after a cast with more MP than the character had: schools used and how many MP are missing from the sum. */
@Component({
  selector: 'app-overspend-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ T.overspend.title }}</h2>
    <mat-dialog-content data-testid="overspend">
      <p><strong>{{ T.overspend.missing(data.overspentMp) }}</strong></p>
      <p>{{ T.overspend.schools }}</p>
      <ul>@for (s of data.schools; track s) { <li>{{ s }}</li> }</ul>
      <p>{{ T.overspend.reset }}</p>
    </mat-dialog-content>
    <mat-dialog-actions>
      <button mat-flat-button mat-dialog-close data-testid="overspend-ok">{{ T.overspend.ok }}</button>
    </mat-dialog-actions>
  `,
})
export class OverspendDialog {
  protected readonly T = T;
  protected readonly data = inject<OverspendData>(MAT_DIALOG_DATA);
}
