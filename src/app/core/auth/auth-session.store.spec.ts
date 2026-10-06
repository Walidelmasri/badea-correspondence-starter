import {
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest';

import {
  AuthSessionStore,
} from './auth-session.store';

import {
  CurrentUser,
} from './auth.model';

const SESSION_STORAGE_KEY =
  'badea-correspondence.alfresco-ticket';

const currentUser: CurrentUser = {
  id: 'walid.salem',
  firstName: 'Walid',
  lastName: 'Salem',
  displayName: 'Walid Salem',
  email: 'walid.salem@badea.org',
  enabled: true,

  capabilities: {
    isGuest: false,
    isAdmin: false,
    isMutable: false,
  },
};

describe('AuthSessionStore', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('stores the Alfresco ticket in the current browser session', () => {
    const store =
      new AuthSessionStore();

    store.setTicket(
      'TICKET_test-123',
    );

    expect(
      store.ticket(),
    ).toBe(
      'TICKET_test-123',
    );

    expect(
      sessionStorage.getItem(
        SESSION_STORAGE_KEY,
      ),
    ).toBe(
      'TICKET_test-123',
    );
  });

  it('restores an existing Alfresco ticket when the store is recreated', () => {
    sessionStorage.setItem(
      SESSION_STORAGE_KEY,
      'TICKET_restored',
    );

    const store =
      new AuthSessionStore();

    expect(
      store.ticket(),
    ).toBe(
      'TICKET_restored',
    );
  });

  it('tracks the authenticated user and clears the complete session', () => {
    const store =
      new AuthSessionStore();

    store.setTicket(
      'TICKET_authenticated',
    );

    store.setCurrentUser(
      currentUser,
    );

    expect(
      store.isAuthenticated(),
    ).toBe(true);

    expect(
      store.currentUser(),
    ).toEqual(
      currentUser,
    );

    expect(
      store.initials(),
    ).toBe('WS');

    store.clear();

    expect(
      store.ticket(),
    ).toBeNull();

    expect(
      store.currentUser(),
    ).toBeNull();

    expect(
      store.isAuthenticated(),
    ).toBe(false);

    expect(
      sessionStorage.getItem(
        SESSION_STORAGE_KEY,
      ),
    ).toBeNull();
  });
});