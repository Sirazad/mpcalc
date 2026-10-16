// Types of the backend's JSON (docs/mosaic-definition-format.md and the engine's result shapes).

export interface Fraction { numerator: number; denominator: number; decimal: number | null; }
/** A message of the engine or the backend: code and arguments, plus the text in the request's language. */
export interface Msg { code: string; args: unknown[]; subject?: string; text: string; }
export interface Maybe<T> { defined: boolean; value?: T; reason?: Msg; }
export type TimeKind = 'TIME' | 'ONE_OFF' | 'PERMANENT' | 'FORM_DEPENDENT' | 'UNDEFINED';
export interface TimeValue { kind: TimeKind; amounts?: Record<string, number>; }
export interface Resistance { kind: string; school: string | null; element?: string | null; elementLabel?: string | null; }

export interface Player { id: number; name: string; admin: boolean; }
export interface School { id: string; name: string; hasTiers: boolean; }
export interface Form { id: string; name: string; tier: number; duration: unknown; regeneration?: string | null; description?: string | null; movable?: boolean; }
export interface MaterialGroup { id: string; name: string; examples: string[]; }

export interface Character { id: number; name: string; maxMp: number; currentMp: number; tiers: Record<string, number>; }
export interface CharacterBody { name?: string; maxMp?: number; currentMp?: number; tiers?: Record<string, number>; }

export interface MosaicInput { id: string; label?: string; type: 'choice' | 'number' | 'form' | 'composition' | 'text'; values?: string[]; labels?: Record<string, string>; descriptions?: Record<string, string>; unit?: string; }
export interface MosaicOption { id: string; label: string; extraMp: number; extraCastingTime?: unknown; }
export interface Mosaic {
  id: string; name: string; school: string; subgroup?: string | null; tier: number | null;
  status: { state: 'complete' | 'partial' | 'incomplete'; missing: string[] };
  castingTime?: { label: string; time: TimeValueDef } | null;
  duration?: TimeValueDef | null;
  range?: { kind: string; value?: number } | null;
  resistance?: { kind: string; school?: string; input?: string; pairs?: Record<string, string> } | null;
  power: { unit: string; baseE: number; step: { mp: number; e: number } | null } | null;
  cost: { pattern: string };
  modifier?: { appliesToWholeSpell: boolean; durationPerExtraMp?: unknown } | null;
  options?: MosaicOption[];
  inputs?: MosaicInput[];
  effect?: string | null;
  source: string;
}
/** A time value as stored in a mosaic definition (lower case kinds, wrapped or plain). */
export interface TimeValueDef { kind: string; amounts?: Record<string, number>; time?: TimeValueDef; }

export interface MosaicsResponse { mosaics: Mosaic[]; versions: Record<string, string>; }

/** One mosaic in the current spell, as sent to the backend. */
export interface Selection { mosaicId: string; inputs: Record<string, unknown>; k: number; options: string[]; extraMp: number; }

export interface MosaicResult {
  mosaicId: string; name: string; refused: boolean; errors: Msg[]; warnings: Msg[];
  mp: Maybe<Fraction>; e: Maybe<number>; powerUnit: string; effect: Maybe<string>;
  resistance: Maybe<Resistance>; castingTime: Maybe<TimeValue>; duration: Maybe<TimeValue>;
}
export interface SpellResult {
  valid: boolean; mosaics: MosaicResult[]; totalMp: Maybe<Fraction>; castingTime: Maybe<TimeValue>; duration: Maybe<TimeValue>;
  remainingMp: Maybe<Fraction>; enoughMp: boolean; warnings: Msg[]; errors: Msg[];
}
/** Result of /api/calculate: the engine result plus the overspend and the schools of the spell. */
export interface CalculateResult extends SpellResult { overspentMp: Fraction | null; schools: string[]; }
export interface CastResponse { result: SpellResult; totalMp: number; overspentMp: number; schools: string[]; currentMp: number; }

export interface SavedSpell { id: number; characterId: number; name: string; content: SpellContent; }
export interface SpellContent { selections?: Selection[]; }

export interface Difference { area: string; id: string; code: string; expected: unknown; actual: unknown; text: string; }
export interface ValidateResponse { inSync: boolean; differences: Difference[]; result: SpellResult | null; }
