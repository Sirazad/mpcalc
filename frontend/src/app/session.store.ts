import { Injectable, computed, inject, signal } from '@angular/core';
import { Api } from './api.service';
import { schoolsWithMosaics } from './inputs';
import { Character, CharacterBody, Form, MaterialGroup, Mosaic, Player, School } from './models';

/**
 * Login, the player's characters and the data cached for the open character. The mosaics, forms, schools and material groups
 * are loaded once when a character is opened and again only on reload() (or when the page is told to by validate).
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly api = inject(Api);

  readonly ready = signal(false);
  readonly player = signal<Player | null>(null);
  readonly players = signal<string[]>([]);
  readonly characters = signal<Character[]>([]);
  readonly character = signal<Character | null>(null);
  readonly schools = signal<School[]>([]);
  readonly forms = signal<Form[]>([]);
  readonly groups = signal<MaterialGroup[]>([]);
  readonly mosaics = signal<Mosaic[]>([]);
  readonly versions = signal<Record<string, string>>({});

  /** Schools in which the character may use at least one mosaic. */
  readonly schoolsWithMosaics = computed(() => {
    const ids = schoolsWithMosaics(this.mosaics(), this.schools().map((s) => s.id));
    return this.schools().filter((s) => ids.includes(s.id));
  });

  private initPromise: Promise<void> | null = null;

  /** Restores the login from the session cookie (once). */
  init(): Promise<void> {
    this.initPromise ??= this.api.me().then((p) => this.player.set(p)).catch(() => this.player.set(null)).finally(() => this.ready.set(true));
    return this.initPromise;
  }

  async loadPlayers() { this.players.set(await this.api.players()); }

  async login(name: string) { this.player.set(await this.api.login(name)); }

  async createPlayer(name: string) {
    await this.api.createPlayer(name);
    await this.login(name);
  }

  async logout() {
    await this.api.logout();
    this.player.set(null);
    this.characters.set([]);
    this.clearCharacter();
  }

  async loadSchools() { this.schools.set(await this.api.schools()); }

  async loadCharacters() { this.characters.set(await this.api.characters()); }

  async createCharacter(body: CharacterBody): Promise<Character> {
    const c = await this.api.createCharacter(body);
    await this.loadCharacters();
    return c;
  }

  async updateCharacter(id: number, body: CharacterBody): Promise<Character> {
    const c = await this.api.updateCharacter(id, body);
    await this.loadCharacters();
    if (this.character()?.id === id) this.character.set(c);
    return c;
  }

  /** Opens a character: reads it, its available mosaics and the reference data once, and caches them. */
  async openCharacter(id: number) {
    const [character, mosaics, schools, forms, groups] = await Promise.all([
      this.api.character(id), this.api.mosaics(id), this.api.schools(), this.api.forms(), this.api.materialGroups(),
    ]);
    this.character.set(character);
    this.mosaics.set(mosaics.mosaics);
    this.versions.set(mosaics.versions);
    this.schools.set(schools);
    this.forms.set(forms);
    this.groups.set(groups);
  }

  /** The reload button: read everything of the open character again. */
  async reload() {
    const c = this.character();
    if (c) await this.openCharacter(c.id);
  }

  setCurrentMp(currentMp: number) {
    const c = this.character();
    if (c) this.character.set({ ...c, currentMp });
  }

  clearCharacter() {
    this.character.set(null);
    this.mosaics.set([]);
    this.versions.set({});
  }
}
