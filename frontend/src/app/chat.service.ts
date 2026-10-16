import { Injectable } from '@angular/core';

/** One server-sent event of the chat service: status (a line while a tool runs), text (a piece of the answer), error, done. */
export interface ChatEvent { kind: 'status' | 'text' | 'error' | 'done'; text?: string; code?: string; }
/** A finished turn of the conversation, as the service wants it back (text only). */
export interface ChatTurn { role: 'user' | 'assistant'; text: string; }

/** Turns the bytes of an event stream into events; a chunk may end in the middle of an event. */
export class SseParser {
  private buffer = '';

  push(chunk: string): ChatEvent[] {
    this.buffer += chunk.replace(/\r\n/g, '\n');
    const events: ChatEvent[] = [];
    let end: number;
    while ((end = this.buffer.indexOf('\n\n')) >= 0) {
      const block = this.buffer.slice(0, end);
      this.buffer = this.buffer.slice(end + 2);
      const e = parseBlock(block);
      if (e) events.push(e);
    }
    return events;
  }
}

export function parseBlock(block: string): ChatEvent | null {
  let name = '';
  let data = '';
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) name = line.slice(6).trim();
    else if (line.startsWith('data:')) data += line.slice(5).trim();
  }
  if (name !== 'status' && name !== 'text' && name !== 'error' && name !== 'done') return null;
  let parsed: { text?: string; code?: string } = {};
  try { parsed = data ? JSON.parse(data) : {}; } catch { return null; }
  return { kind: name, text: parsed.text, code: parsed.code };
}

/** Talks to the chat service (/chat/ask). The login cookie goes along; the page always asks for Hungarian. */
@Injectable({ providedIn: 'root' })
export class ChatApi {
  /** Sends one question and reports every event as it arrives. Rejects only on an aborted or broken connection. */
  async ask(characterId: number, message: string, history: ChatTurn[], onEvent: (e: ChatEvent) => void, signal?: AbortSignal): Promise<void> {
    const res = await fetch('/chat/ask', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'Accept-Language': 'hu' },
      body: JSON.stringify({ characterId, message, history }),
      signal,
    });
    if (!res.ok || !res.body) {
      // the service answers a refused question with {"error": "...", "code": "..."}
      let body: { error?: string; code?: string } = {};
      try { body = await res.json(); } catch { /* not JSON, for example a gateway error */ }
      onEvent({ kind: 'error', text: body.error, code: body.code ?? `http_${res.status}` });
      return;
    }
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    const parser = new SseParser();
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      for (const e of parser.push(decoder.decode(value, { stream: true }))) onEvent(e);
    }
  }
}
