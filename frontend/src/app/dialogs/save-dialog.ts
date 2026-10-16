import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { T } from '../texts.hu';

@Component({
  selector: 'app-save-dialog',
  imports: [FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ T.save.title }}</h2>
    <mat-dialog-content>
      <mat-form-field>
        <mat-label>{{ T.save.name }}</mat-label>
        <input matInput [ngModel]="name()" (ngModelChange)="name.set($event)" name="spellName" data-testid="spell-name" />
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions>
      <button mat-button mat-dialog-close>{{ T.save.cancel }}</button>
      <button mat-flat-button [disabled]="!name().trim()" (click)="ref.close(name().trim())" data-testid="spell-save">{{ T.save.save }}</button>
    </mat-dialog-actions>
  `,
})
export class SaveDialog {
  protected readonly T = T;
  protected readonly ref = inject(MatDialogRef<SaveDialog, string>);
  protected readonly name = signal('');
}
