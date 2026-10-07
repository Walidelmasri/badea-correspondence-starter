import {
  provideHttpClient,
} from '@angular/common/http';

import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import {
  TestBed,
} from '@angular/core/testing';

import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest';

import {
  ALFRESCO_CURRENT_TICKET_URL,
  ALFRESCO_CURRENT_USER_URL,
  ALFRESCO_TICKETS_URL,
  BADEA_CURRENT_EMPLOYEE_URL,
} from './auth.constants';

import {
  CurrentUser,
  EmployeeProfile,
} from './auth.model';

import {
  AuthService,
} from './auth.service';

import {
  AuthSessionStore,
} from './auth-session.store';

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

const employeeProfile: EmployeeProfile = {
  employeeId: '18280',
  username: 'almunder.salih',
  nameEnglish:
    'Almunder Salih Melod Sahboun',
  nameArabic:
    'المنذر صالح ميلود سحبون',

  department: {
    code: '111',
    nameEnglish:
      'Strategy Department',
    nameArabic:
      'إدارة الاستراتيجية',
  },
};

describe('AuthService', () => {
  let service: AuthService;
  let session: AuthSessionStore;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        AuthSessionStore,

        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service =
      TestBed.inject(AuthService);

    session =
      TestBed.inject(
        AuthSessionStore,
      );

    http =
      TestBed.inject(
        HttpTestingController,
      );
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('logs in with Alfresco and enriches the authenticated user from the employee directory', () => {
    const next = vi.fn();
    const error = vi.fn();

    service
      .login({
        userId:
          '  Walid.salem  ',
        password:
          'test-password',
      })
      .subscribe({
        next,
        error,
      });

    const ticketRequest =
      http.expectOne(
        ALFRESCO_TICKETS_URL,
      );

    expect(
      ticketRequest.request.method,
    ).toBe('POST');

    expect(
      ticketRequest.request.body,
    ).toEqual({
      userId: 'Walid.salem',
      password:
        'test-password',
    });

    ticketRequest.flush({
      entry: {
        id: 'TICKET_test-123',
        userId: 'walid.salem',
      },
    });

    expect(
      session.ticket(),
    ).toBe(
      'TICKET_test-123',
    );

    const userRequest =
      http.expectOne(
        ALFRESCO_CURRENT_USER_URL,
      );

    expect(
      userRequest.request.method,
    ).toBe('GET');

    userRequest.flush({
      entry: currentUser,
    });

    const employeeRequest =
      http.expectOne(
        BADEA_CURRENT_EMPLOYEE_URL,
      );

    expect(
      employeeRequest.request.method,
    ).toBe('GET');

    employeeRequest.flush(
      employeeProfile,
    );

    const expectedUser: CurrentUser = {
      ...currentUser,
      employeeProfile,
    };

    expect(error)
      .not
      .toHaveBeenCalled();

    expect(next)
      .toHaveBeenCalledOnce();

    expect(next)
      .toHaveBeenCalledWith(
        expectedUser,
      );

    expect(
      session.currentUser(),
    ).toEqual(
      expectedUser,
    );

    expect(
      session.isAuthenticated(),
    ).toBe(true);
  });

  it('clears the session when login fails', () => {
    const next = vi.fn();
    const error = vi.fn();

    session.setTicket(
      'TICKET_stale',
    );

    session.setCurrentUser(
      currentUser,
    );

    service
      .login({
        userId: 'walid.salem',
        password:
          'wrong-password',
      })
      .subscribe({
        next,
        error,
      });

    expect(
      session.ticket(),
    ).toBeNull();

    expect(
      session.currentUser(),
    ).toBeNull();

    const ticketRequest =
      http.expectOne(
        ALFRESCO_TICKETS_URL,
      );

    ticketRequest.flush(
      {
        error: {
          briefSummary:
            'Authentication failed',
        },
      },
      {
        status: 401,
        statusText:
          'Unauthorized',
      },
    );

    expect(next)
      .not
      .toHaveBeenCalled();

    expect(error)
      .toHaveBeenCalledOnce();

    expect(
      session.ticket(),
    ).toBeNull();

    expect(
      session.currentUser(),
    ).toBeNull();

    expect(
      session.isAuthenticated(),
    ).toBe(false);
  });

  it('restores the authenticated user when no Oracle employee mapping exists', () => {
    const next = vi.fn();

    session.setTicket(
      'TICKET_existing',
    );

    expect(
      session.currentUser(),
    ).toBeNull();

    service
      .ensureAuthenticated()
      .subscribe(next);

    const userRequest =
      http.expectOne(
        ALFRESCO_CURRENT_USER_URL,
      );

    expect(
      userRequest.request.method,
    ).toBe('GET');

    userRequest.flush({
      entry: currentUser,
    });

    const employeeRequest =
      http.expectOne(
        BADEA_CURRENT_EMPLOYEE_URL,
      );

    employeeRequest.flush(
      {
        code:
          'EMPLOYEE_NOT_FOUND',
        message:
          'No active employee profile was found for the authenticated user.',
      },
      {
        status: 404,
        statusText:
          'Not Found',
      },
    );

    expect(next)
      .toHaveBeenCalledOnce();

    expect(next)
      .toHaveBeenCalledWith(true);

    expect(
      session.ticket(),
    ).toBe(
      'TICKET_existing',
    );

    expect(
      session.currentUser(),
    ).toEqual(
      currentUser,
    );

    expect(
      session.isAuthenticated(),
    ).toBe(true);
  });

  it('invalidates the Alfresco ticket and clears the local session on logout', () => {
    const complete = vi.fn();

    session.setTicket(
      'TICKET_active',
    );

    session.setCurrentUser(
      currentUser,
    );

    service
      .logout()
      .subscribe({
        complete,
      });

    const logoutRequest =
      http.expectOne(
        ALFRESCO_CURRENT_TICKET_URL,
      );

    expect(
      logoutRequest.request.method,
    ).toBe('DELETE');

    logoutRequest.flush(null);

    expect(complete)
      .toHaveBeenCalledOnce();

    expect(
      session.ticket(),
    ).toBeNull();

    expect(
      session.currentUser(),
    ).toBeNull();

    expect(
      session.isAuthenticated(),
    ).toBe(false);
  });
});