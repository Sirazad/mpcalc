import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  CalculateResult, CastResponse, Character, CharacterBody, Form, MaterialGroup, MosaicsResponse, Player, SavedSpell, School,
  Selection, SpellContent, ValidateResponse,
} from './models';

/** All calls to the backend. Same origin: nginx (or the dev proxy) forwards /api, the session cookie is sent by the browser. */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  players() { return firstValueFrom(this.http.get<string[]>(`${this.base}/players`)); }
  createPlayer(name: string) { return firstValueFrom(this.http.post<Player>(`${this.base}/players`, { name })); }
  login(name: string) { return firstValueFrom(this.http.post<Player>(`${this.base}/session`, { name })); }
  me() { return firstValueFrom(this.http.get<Player>(`${this.base}/session`)); }
  logout() { return firstValueFrom(this.http.delete<void>(`${this.base}/session`)); }

  schools() { return firstValueFrom(this.http.get<School[]>(`${this.base}/schools`)); }
  forms() { return firstValueFrom(this.http.get<Form[]>(`${this.base}/forms`)); }
  /** Texts that belong to no mosaic, form or group: id -> text. */
  texts() { return firstValueFrom(this.http.get<Record<string, string>>(`${this.base}/texts`)); }
  materialGroups() { return firstValueFrom(this.http.get<MaterialGroup[]>(`${this.base}/material-groups`)); }

  characters() { return firstValueFrom(this.http.get<Character[]>(`${this.base}/characters`)); }
  character(id: number) { return firstValueFrom(this.http.get<Character>(`${this.base}/characters/${id}`)); }
  createCharacter(body: CharacterBody) { return firstValueFrom(this.http.post<Character>(`${this.base}/characters`, body)); }
  updateCharacter(id: number, body: CharacterBody) { return firstValueFrom(this.http.put<Character>(`${this.base}/characters/${id}`, body)); }
  mosaics(characterId: number) { return firstValueFrom(this.http.get<MosaicsResponse>(`${this.base}/characters/${characterId}/mosaics`)); }

  calculate(characterId: number, selections: Selection[]) {
    return firstValueFrom(this.http.post<CalculateResult>(`${this.base}/calculate`, { characterId, selections }));
  }
  cast(characterId: number, selections: Selection[]) {
    return firstValueFrom(this.http.post<CastResponse>(`${this.base}/cast`, { characterId, selections }));
  }
  validate(body: unknown) { return firstValueFrom(this.http.post<ValidateResponse>(`${this.base}/validate`, body)); }

  spells(characterId: number) { return firstValueFrom(this.http.get<SavedSpell[]>(`${this.base}/characters/${characterId}/spells`)); }
  saveSpell(characterId: number, name: string, content: SpellContent) {
    return firstValueFrom(this.http.post<SavedSpell>(`${this.base}/characters/${characterId}/spells`, { name, content }));
  }
  deleteSpell(id: number) { return firstValueFrom(this.http.delete<void>(`${this.base}/spells/${id}`)); }
}

/** The backend's {"error": "..."} text, or a generic one. */
export function errorText(e: unknown, fallback: string): string {
  if (e instanceof HttpErrorResponse) {
    const msg = (e.error as { error?: string } | null)?.error;
    if (msg) return msg;
  }
  return fallback;
}
