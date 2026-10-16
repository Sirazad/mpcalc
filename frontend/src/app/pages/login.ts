import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { errorText } from '../api.service';
import { SessionStore } from '../session.store';
import { T } from '../texts.hu';

/** Interim login: pick your name (no password). Replaced by Google login later. */
@Component({
  selector: 'app-login',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <mat-card class="card">
      <mat-card-header><mat-card-title>{{ T.login.title }}</mat-card-title></mat-card-header>
      <mat-card-content>
        @if (session.players().length) {
          <mat-form-field class="full">
            <mat-label>{{ T.login.pick }}</mat-label>
            <mat-select [ngModel]="chosen()" (ngModelChange)="chosen.set($event)" name="player" data-testid="player-select">
              @for (n of session.players(); track n) { <mat-option [value]="n">{{ n }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <button mat-flat-button type="button" [disabled]="!chosen()" (click)="enter()" data-testid="enter">{{ T.login.enter }}</button>
        } @else {
          <p>{{ T.login.noPlayers }}</p>
        }
        <hr />
        <mat-form-field class="full">
          <mat-label>{{ T.login.newName }}</mat-label>
          <input matInput [ngModel]="newName()" (ngModelChange)="newName.set($event)" name="newName" data-testid="new-player" />
        </mat-form-field>
        <button mat-stroked-button type="button" [disabled]="!newName().trim()" (click)="create()" data-testid="create-player">{{ T.login.create }}</button>
        @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      </mat-card-content>
    </mat-card>
  `,
  styles: `.card { max-width: 420px; margin: 32px auto; } .full { width: 100%; } .error { color: var(--mat-sys-error); }`,
})
export class Login {
  protected readonly T = T;
  protected readonly session = inject(SessionStore);
  private readonly router = inject(Router);
  protected readonly chosen = signal('');
  protected readonly newName = signal('');
  protected readonly error = signal('');

  constructor() { void this.session.loadPlayers().catch(() => this.error.set(T.errors.generic)); }

  protected async enter() {
    await this.run(() => this.session.login(this.chosen()));
  }

  protected async create() {
    await this.run(() => this.session.createPlayer(this.newName().trim()));
  }

  private async run(action: () => Promise<void>) {
    try {
      await action();
      await this.router.navigateByUrl('/characters');
    } catch (e) {
      this.error.set(errorText(e, T.errors.generic));
    }
  }
}
