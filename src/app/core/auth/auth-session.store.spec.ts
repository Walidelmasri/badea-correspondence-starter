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

  it('selects only manager workspaces from the authenticated profile and clears selection on logout', () => {
    const store = new AuthSessionStore();
    store.setCurrentUser({ ...currentUser, employeeProfile: {
      employeeId: '18284', username: 'ndjack', nameEnglish: 'NDjack', nameArabic: null,
      department: null, correspondenceRole: 'PRESIDENT_OFFICE',
      managerWorkspaces: [
        { code: '101', nameEnglish: 'Office', nameArabic: null, capacity: 'MANAGER', role: 'PRESIDENT_OFFICE' },
        { code: '118', nameEnglish: 'HR', nameArabic: null, capacity: 'ACTING_MANAGER', role: 'DIRECTOR' },
      ],
    }});
    store.selectWorkspace('118');
    expect(store.effectiveRole()).toBe('DIRECTOR');
    expect(store.effectiveDepartmentCode()).toBe('118');
    expect(() => store.selectWorkspace('106')).toThrow();
    store.clear();
    expect(store.workspaceUnit()).toBeNull();
  });

  it('shows only real managerial workspaces, including a dual manager of Office 101 and another unit', () => {
    const store = new AuthSessionStore();
    store.setCurrentUser({ ...currentUser, employeeProfile: {
      employeeId: '99999', username: 'walid.salem', nameEnglish: 'Walid', nameArabic: null,
      department: null, correspondenceRole: 'PRESIDENT_OFFICE',
      managerWorkspaces: [
        { code: '117', nameEnglish: 'Digital Transformation', nameArabic: null,
          capacity: 'MANAGER', role: 'DIRECTOR' },
        { code: '101', nameEnglish: "President's Office", nameArabic: null,
          capacity: 'ACTING_MANAGER', role: 'PRESIDENT_OFFICE' },
      ],
    }});
    expect(store.availableWorkspaces()).toHaveLength(2);
    store.selectWorkspace('101');
    expect(store.effectiveRole()).toBe('PRESIDENT_OFFICE');
    expect(store.activeManagerWorkspace()?.code).toBe('101');
    store.selectWorkspace('117');
    expect(store.effectiveRole()).toBe('DIRECTOR');
    expect(store.activeManagerWorkspace()?.code).toBe('117');

    store.setCurrentUser({ ...currentUser, employeeProfile: {
      employeeId: '99999', username: 'walid.salem', nameEnglish: 'Walid', nameArabic: null,
      department: null, correspondenceRole: 'PRESIDENT_OFFICE', managerWorkspaces: [],
    }});
    expect(store.availableWorkspaces()).toHaveLength(0);
    expect(store.workspaceUnit()).toBeNull();
    expect(() => store.selectWorkspace('117')).toThrow();
  });

  it('selects an authorized Oracle manager unit by default for a Director-only login', () => {
    const store = new AuthSessionStore();
    store.setCurrentUser({ ...currentUser, employeeProfile: {
      employeeId: '18284', username: 'ndjack', nameEnglish: 'NDjack', nameArabic: null,
      department: null, correspondenceRole: 'DIRECTOR',
      managerWorkspaces: [{ code: '118', nameEnglish: 'HR', nameArabic: null,
        capacity: 'ACTING_MANAGER', role: 'DIRECTOR' }],
    }});
    expect(store.workspaceUnit()).toBe('118');
    expect(store.effectiveRole()).toBe('DIRECTOR');
    expect(store.effectiveDepartmentCode()).toBe('118');
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