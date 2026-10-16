import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { errorText } from '../api.service';
import { Character } from '../models';
import { SessionStore } from '../session.store';
import { SpellStore } from '../spell.store';
import { T } from '../texts.hu';

@Component({
  selector: 'app-characters',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './characters.html',
  styleUrl: './characters.scss',
})
export class Characters {
  protected readonly T = T;
  protected readonly session = inject(SessionStore);
  private readonly spell = inject(SpellStore);
  private readonly router = inject(Router);

  /** null: no form open; 'new': creating; a character: editing it. */
  protected readonly editing = signal<Character | 'new' | null>(null);
  protected readonly name = signal('');
  protected readonly maxMp = signal<number | null>(0);
  protected readonly currentMp = signal<number | null>(0);
  protected readonly tiers = signal<Record<string, number>>({});
  protected readonly error = signal('');
  /** MP and tiers are whole numbers (a decimal is refused, never cut). */
  protected readonly wholeNumbers = computed(() =>
    [this.maxMp() ?? 0, this.currentMp() ?? 0, ...Object.values(this.tiers())].every((n) => Number.isInteger(n)));
  /** Schools with tiers (alapvarazslatok has none). */
  protected readonly tierSchools = computed(() => this.session.schools().filter((s) => s.hasTiers));

  constructor() {
    void Promise.all([this.session.loadCharacters(), this.session.loadSchools()]).catch(() => this.error.set(T.errors.generic));
  }

  protected startNew() {
    this.editing.set('new');
    this.name.set('');
    this.maxMp.set(0);
    this.currentMp.set(0);
    this.tiers.set({});
    this.error.set('');
  }

  protected startEdit(c: Character) {
    this.editing.set(c);
    this.name.set(c.name);
    this.maxMp.set(c.maxMp);
    this.currentMp.set(c.currentMp);
    this.tiers.set({ ...c.tiers });
    this.error.set('');
  }

  protected setTier(school: string, value: number | null) {
    this.tiers.update((t) => ({ ...t, [school]: value ?? 0 }));
  }

  protected async save() {
    const e = this.editing();
    if (!e) return;
    const body = { name: this.name(), maxMp: this.maxMp() ?? 0, currentMp: this.currentMp() ?? 0, tiers: this.tiers() };
    try {
      if (e === 'new') await this.session.createCharacter(body);
      else await this.session.updateCharacter(e.id, body);
      this.editing.set(null);
    } catch (err) {
      this.error.set(errorText(err, T.errors.generic));
    }
  }

  protected async open(c: Character) {
    if (this.session.character()?.id !== c.id) await this.spell.clear();
    await this.router.navigate(['/builder', c.id]);
  }
}
