import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { firstValueFrom } from 'rxjs';
import { AuthSessionStore } from './auth-session.store';
import { AuthTabSessionHandoffService } from './auth-tab-session-handoff.service';

class FakeChannel {
  static channels = new Set<FakeChannel>();
  private listeners = new Set<(event: MessageEvent) => void>();
  constructor(readonly name: string) { FakeChannel.channels.add(this); }
  addEventListener(_type: 'message', listener: (event: MessageEvent) => void): void {
    this.listeners.add(listener);
  }
  removeEventListener(_type: 'message', listener: (event: MessageEvent) => void): void {
    this.listeners.delete(listener);
  }
  postMessage(data: unknown): void {
    for (const channel of FakeChannel.channels) {
      if (channel !== this && channel.name === this.name) {
        for (const listener of channel.listeners) listener({ data } as MessageEvent);
      }
    }
  }
  close(): void { FakeChannel.channels.delete(this); this.listeners.clear(); }
}

const channelName = 'badea-tarasol-alfresco-active-tab-v1';
const windowChannel = window as Window & { BroadcastChannel: typeof BroadcastChannel };
const oldChannel = windowChannel.BroadcastChannel;

describe('Same-browser authenticated Tarasol tab handoff', () => {
  beforeEach(() => {
    FakeChannel.channels.clear();
    windowChannel.BroadcastChannel = FakeChannel as unknown as typeof BroadcastChannel;
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [AuthSessionStore, AuthTabSessionHandoffService] });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    for (const c of [...FakeChannel.channels]) c.close();
    windowChannel.BroadcastChannel = oldChannel;
    sessionStorage.clear();
  });

  it('accepts one ticket from a same-origin authenticated tab without persisting it', async () => {
    const handoff = TestBed.inject(AuthTabSessionHandoffService);
    const peer = new FakeChannel(channelName);
    peer.addEventListener('message', event => {
      const data = event.data as { type: string; from: string; requestId: string };
      if (data.type === 'ticket-request') {
        peer.postMessage({ type: 'ticket-response', from: 'existing-tab',
          to: data.from, requestId: data.requestId, ticket: 'TICKET_verified-later' });
      }
    });
    const result = firstValueFrom(handoff.requestTicket());
    expect(await result).toBe('TICKET_verified-later');
    expect(sessionStorage.length).toBe(0);
  });

  it('refuses ambiguous tickets from two different signed-in accounts', async () => {
    const handoff = TestBed.inject(AuthTabSessionHandoffService);
    for (const [name, ticket] of [['alice', 'TICKET_a'], ['bob', 'TICKET_b']]) {
      const peer = new FakeChannel(channelName);
      peer.addEventListener('message', event => {
        const data = event.data as { type: string; from: string; requestId: string };
        if (data.type === 'ticket-request') {
          peer.postMessage({ type: 'ticket-response', from: name,
            to: data.from, requestId: data.requestId, ticket });
        }
      });
    }
    const result = firstValueFrom(handoff.requestTicket());
    expect(await result).toBeNull();
  });

  it('falls back to sign-in when there is no other authenticated tab', async () => {
    const handoff = TestBed.inject(AuthTabSessionHandoffService);
    const result = firstValueFrom(handoff.requestTicket());
    expect(await result).toBeNull();
  });
});
