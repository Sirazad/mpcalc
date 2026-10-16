import { SseParser, parseBlock } from './chat.service';

describe('SseParser', () => {
  it('reads complete events', () => {
    const p = new SseParser();
    const out = p.push('event: status\ndata: {"text": "Számolok…"}\n\nevent: text\ndata: {"text": "Szia"}\n\nevent: done\ndata: {}\n\n');
    expect(out).toEqual([
      { kind: 'status', text: 'Számolok…', code: undefined },
      { kind: 'text', text: 'Szia', code: undefined },
      { kind: 'done', text: undefined, code: undefined },
    ]);
  });

  it('waits for the rest of an event that was cut in the middle, also in the middle of a character sequence', () => {
    const p = new SseParser();
    expect(p.push('event: text\ndata: {"te')).toEqual([]);
    expect(p.push('xt": "á')).toEqual([]);
    expect(p.push('rvíz"}\n')).toEqual([]);
    expect(p.push('\nevent: done\ndata: {}\n\n').map((e) => e.kind)).toEqual(['text', 'done']);
  });

  it('accepts Windows line ends and reads the error text and code', () => {
    const out = new SseParser().push('event: error\r\ndata: {"text": "Nem érhető el.", "code": "unavailable"}\r\n\r\n');
    expect(out).toEqual([{ kind: 'error', text: 'Nem érhető el.', code: 'unavailable' }]);
  });

  it('ignores unknown events and broken data instead of failing', () => {
    expect(parseBlock('event: ping\ndata: {}')).toBeNull();
    expect(parseBlock('event: text\ndata: {not json')).toBeNull();
    expect(parseBlock(': just a comment')).toBeNull();
  });
});
