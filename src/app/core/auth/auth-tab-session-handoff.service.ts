import { DestroyRef, inject, Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { AuthSessionStore } from './auth-session.store';

const CHANNEL_NAME = 'badea-tarasol-alfresco-active-tab-v1';

type TicketMessage =
  | { type: 'ticket-request'; from: string; requestId: string }
  | { type: 'ticket-response'; from: string; to: string; requestId: string; ticket: string };

function randomId(): string {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/**
 * Reuse an already authenticated same-origin Tarasol tab without storing the
 * Alfresco ticket in localStorage, cookies managed by JavaScript or a URL.
 * No other open authenticated tab means the normal login screen remains.
 */
@Injectable({ providedIn: 'root' })
export class AuthTabSessionHandoffService {
  private readonly session = inject(AuthSessionStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly tabId = randomId();
  private readonly channel: BroadcastChannel | null =
    typeof window !== 'undefined' && typeof window.BroadcastChannel === 'function'
      ? new window.BroadcastChannel(CHANNEL_NAME) : null;

  constructor() {
    this.channel?.addEventListener('message', this.answerRequest);
    this.destroyRef.onDestroy(() => {
      this.channel?.removeEventListener('message', this.answerRequest);
      this.channel?.close();
    });
  }

  private readonly answerRequest = (event: MessageEvent<TicketMessage>): void => {
    const message = event.data;
    if (!message || message.type !== 'ticket-request'
        || typeof message.requestId !== 'string' || !message.requestId
        || typeof message.from !== 'string' || message.from === this.tabId
        || !this.session.isAuthenticated()) return;
    const ticket = this.session.ticket();
    if (!ticket) return;
    this.channel?.postMessage({
      type: 'ticket-response', from: this.tabId, to: message.from,
      requestId: message.requestId, ticket,
    } satisfies TicketMessage);
  };

  requestTicket(): Observable<string | null> {
    if (!this.channel) return of(null);
    return new Observable<string | null>(subscriber => {
      const requestId = randomId();
      const received = new Set<string>();
      const onMessage = (event: MessageEvent<TicketMessage>): void => {
        const message = event.data;
        if (message?.type === 'ticket-response'
            && message.to === this.tabId && message.requestId === requestId
            && typeof message.ticket === 'string' && message.ticket.length > 0
            && message.from !== this.tabId) received.add(message.ticket);
      };
      this.channel!.addEventListener('message', onMessage);
      const timer = setTimeout(() => {
        // Never arbitrarily choose an account if two authenticated tabs differ.
        subscriber.next(received.size === 1 ? [...received][0] : null);
        subscriber.complete();
      }, 500);
      this.channel!.postMessage({
        type: 'ticket-request', from: this.tabId, requestId,
      } satisfies TicketMessage);
      return () => {
        clearTimeout(timer);
        this.channel?.removeEventListener('message', onMessage);
      };
    });
  }
}
