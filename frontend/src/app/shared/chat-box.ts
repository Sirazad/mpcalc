import { Component, ElementRef, effect, inject, input, linkedSignal, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ChatApi, ChatEvent, ChatTurn } from '../chat.service';
import { T } from '../texts.hu';

interface Shown { role: 'user' | 'assistant'; text: string; error?: boolean; }

/** The chat box: questions go to the chat service, the answer streams in. The history lives here only (nothing is stored). */
@Component({
  selector: 'app-chat-box',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './chat-box.html',
  styleUrl: './chat-box.scss',
})
export class ChatBox {
  protected readonly T = T;
  private readonly api = inject(ChatApi);

  readonly characterId = input.required<number>();

  /** What is shown, and what the service gets back as history; both start empty for every character. */
  protected readonly shown = linkedSignal<Shown[]>(() => { this.characterId(); return []; });
  private history: ChatTurn[] = [];
  protected readonly question = signal('');
  protected readonly status = signal('');
  protected readonly busy = signal(false);
  private abort: AbortController | null = null;
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');

  constructor() {
    // another character is another conversation
    effect(() => { this.characterId(); this.stop(); this.history = []; this.status.set(''); this.busy.set(false); });
    // keep the newest text in view
    effect(() => { this.shown(); queueMicrotask(() => { const el = this.list()?.nativeElement; if (el) el.scrollTop = el.scrollHeight; }); });
  }

  protected async send() {
    const message = this.question().trim();
    if (!message || this.busy()) return;
    this.question.set('');
    this.busy.set(true);
    this.status.set('');
    this.shown.update((l) => [...l, { role: 'user', text: message }, { role: 'assistant', text: '' }]);
    this.abort = new AbortController();
    let answer = '';
    let failed = false;
    const onEvent = (e: ChatEvent) => {
      if (e.kind === 'status') this.status.set(e.text ?? '');
      else if (e.kind === 'text') { answer += e.text ?? ''; this.status.set(''); this.patchLast({ text: answer }); }
      else if (e.kind === 'error') { failed = true; this.status.set(''); this.patchLast({ text: e.text ?? T.errors.generic, error: true }); }
    };
    try {
      await this.api.ask(this.characterId(), message, this.history, onEvent, this.abort.signal);
    } catch (err) {
      failed = true;
      const stopped = (err as { name?: string }).name === 'AbortError';
      this.patchLast({ text: stopped ? (answer ? answer + ' ' + T.chat.stopped : T.chat.stopped) : T.errors.generic, error: !stopped });
    } finally {
      // only a finished question joins the history, so the turns the service receives always alternate and end with the assistant
      if (!failed && answer.trim()) this.history = [...this.history, { role: 'user', text: message }, { role: 'assistant', text: answer }];
      this.busy.set(false);
      this.status.set('');
      this.abort = null;
    }
  }

  private patchLast(patch: Partial<Shown>) {
    this.shown.update((l) => l.map((m, i) => (i === l.length - 1 ? { ...m, ...patch } : m)));
  }

  protected stop() { this.abort?.abort(); }

  protected reset() {
    this.stop();
    this.shown.set([]);
    this.history = [];
    this.status.set('');
  }

  protected onKey(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); void this.send(); }
  }
}
