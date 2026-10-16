import { TestBed } from '@angular/core/testing';
import { ChatApi, ChatEvent, ChatTurn } from '../chat.service';
import { ChatBox } from './chat-box';

class FakeApi {
  calls: { characterId: number; message: string; history: ChatTurn[] }[] = [];
  script: (onEvent: (e: ChatEvent) => void, signal?: AbortSignal) => Promise<void> = async (on) => { on({ kind: 'text', text: 'Válasz.' }); on({ kind: 'done' }); };

  async ask(characterId: number, message: string, history: ChatTurn[], onEvent: (e: ChatEvent) => void, signal?: AbortSignal) {
    this.calls.push({ characterId, message, history: [...history] });
    await this.script(onEvent, signal);
  }
}

describe('ChatBox', () => {
  function create(api: FakeApi, characterId = 1) {
    TestBed.configureTestingModule({ providers: [{ provide: ChatApi, useValue: api }] });
    const fixture = TestBed.createComponent(ChatBox);
    fixture.componentRef.setInput('characterId', characterId);
    return fixture;
  }
  const el = (f: { nativeElement: unknown }) => f.nativeElement as HTMLElement;
  const type = (f: { nativeElement: unknown }, text: string) => {
    const box = el(f).querySelector('[data-testid="chat-input"]') as HTMLTextAreaElement;
    box.value = text;
    box.dispatchEvent(new Event('input'));
  };
  const texts = (f: { nativeElement: unknown }, who: string) =>
    [...el(f).querySelectorAll(`[data-testid="chat-${who}"] .text`)].map((e) => e.textContent?.trim());

  it('shows the question and the streamed answer, and keeps only finished turns as history', async () => {
    const api = new FakeApi();
    api.script = async (on) => { on({ kind: 'status', text: 'Számolok…' }); on({ kind: 'text', text: 'Elő' }); on({ kind: 'text', text: 'ször.' }); on({ kind: 'done' }); };
    const f = create(api);
    await f.whenStable();
    type(f, 'Első kérdés');
    await f.whenStable();
    (el(f).querySelector('[data-testid="chat-send"]') as HTMLButtonElement).click();
    await f.whenStable();
    expect(texts(f, 'user')).toEqual(['Első kérdés']);
    expect(texts(f, 'assistant')).toEqual(['Először.']);
    expect(el(f).querySelector('[data-testid="chat-status"]')).toBeNull();
    // the second question carries the first turn (text only)
    type(f, 'Második');
    await f.whenStable();
    (el(f).querySelector('[data-testid="chat-send"]') as HTMLButtonElement).click();
    await f.whenStable();
    expect(api.calls[0].history).toEqual([]);
    expect(api.calls[1].history).toEqual([{ role: 'user', text: 'Első kérdés' }, { role: 'assistant', text: 'Először.' }]);
    expect(api.calls[1].characterId).toBe(1);
  });

  it('shows a refused question as an error in the answer and does not add it to the history', async () => {
    const api = new FakeApi();
    api.script = async (on) => { on({ kind: 'error', text: 'Nem vagy bejelentkezve.', code: 'not_logged_in' }); };
    const f = create(api);
    await f.whenStable();
    type(f, 'kérdés');
    await f.whenStable();
    (el(f).querySelector('[data-testid="chat-send"]') as HTMLButtonElement).click();
    await f.whenStable();
    expect(texts(f, 'assistant')).toEqual(['Nem vagy bejelentkezve.']);
    expect(el(f).querySelector('.msg.error')).not.toBeNull();
    type(f, 'újra');
    await f.whenStable();
    (el(f).querySelector('[data-testid="chat-send"]') as HTMLButtonElement).click();
    await f.whenStable();
    expect(api.calls[1].history).toEqual([]);
  });

  it('starts a new conversation when another character is opened or when asked', async () => {
    const api = new FakeApi();
    const f = create(api, 1);
    await f.whenStable();
    type(f, 'q');
    await f.whenStable();
    (el(f).querySelector('[data-testid="chat-send"]') as HTMLButtonElement).click();
    await f.whenStable();
    expect(texts(f, 'user').length).toBe(1);
    f.componentRef.setInput('characterId', 2);
    await f.whenStable();
    expect(texts(f, 'user')).toEqual([]);
    type(f, 'q2');
    await f.whenStable();
    (el(f).querySelector('[data-testid="chat-send"]') as HTMLButtonElement).click();
    await f.whenStable();
    expect(api.calls[1]).toMatchObject({ characterId: 2, history: [] });
    (el(f).querySelector('[data-testid="chat-new"]') as HTMLButtonElement).click();
    await f.whenStable();
    expect(texts(f, 'user')).toEqual([]);
  });

  it('does not send an empty question', async () => {
    const api = new FakeApi();
    const f = create(api);
    await f.whenStable();
    expect((el(f).querySelector('[data-testid="chat-send"]') as HTMLButtonElement).disabled).toBe(true);
    type(f, '   ');
    await f.whenStable();
    expect((el(f).querySelector('[data-testid="chat-send"]') as HTMLButtonElement).disabled).toBe(true);
    expect(api.calls.length).toBe(0);
  });
});
