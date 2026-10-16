import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { Difference, ValidateResponse } from '../models';
import { T } from '../texts.hu';

/** What the backend found different from the data the page holds. Closes with true when the user asks to reload. */
@Component({
  selector: 'app-validate-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ T.validate.title }}</h2>
    <mat-dialog-content data-testid="validate-result">
      @if (data.inSync) {
        <p data-testid="in-sync">{{ T.validate.inSync }}</p>
      } @else {
        <p>{{ T.validate.differences }}</p>
        <ul>
          @for (d of data.differences; track $index) {
            <li [attr.data-code]="d.code">
              <strong>{{ area(d) }}</strong> {{ d.id }}: {{ d.text }}
              @if (d.expected !== null || d.actual !== null) {
                <br /><small>{{ T.validate.expected }}: {{ show(d.expected) }}; {{ T.validate.actual }}: {{ show(d.actual) }}</small>
              }
            </li>
          }
        </ul>
      }
    </mat-dialog-content>
    <mat-dialog-actions>
      @if (!data.inSync) { <button mat-flat-button [mat-dialog-close]="true" data-testid="validate-reload">{{ T.validate.reload }}</button> }
      <button mat-button mat-dialog-close>{{ T.validate.close }}</button>
    </mat-dialog-actions>
  `,
})
export class ValidateDialog {
  protected readonly T = T;
  protected readonly data = inject<ValidateResponse>(MAT_DIALOG_DATA);

  protected area(d: Difference) { return T.validate.areas[d.area] ?? d.area; }
  protected show(v: unknown) { return v === null || v === undefined ? '–' : typeof v === 'object' ? JSON.stringify(v) : String(v); }
}
