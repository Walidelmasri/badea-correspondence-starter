import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';

import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import {
  TestBed,
} from '@angular/core/testing';

import {
  Router,
} from '@angular/router';

import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest';

import {
  ALFRESCO_CURRENT_USER_URL,
  ALFRESCO_TICKETS_URL,
} from './auth.constants';

import {
  authInterceptor,
} from './auth.interceptor';

import {
  AuthSessionStore,
} from './auth-session.store';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let session: AuthSessionStore;

  const router = {
    url: '/inbox',

    navigate:
      vi.fn()
        .mockResolvedValue(true),
  };

  beforeEach(() => {
    sessionStorage.clear();

    router.url = '/inbox';

    router.navigate
      .mockReset()
      .mockResolvedValue(true);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: Router,
          useValue: router,
        },

        provideHttpClient(
          withInterceptors([
            authInterceptor,
          ]),
        ),

        provideHttpClientTesting(),
      ],
    });

    client =
      TestBed.inject(HttpClient);

    http =
      TestBed.inject(
        HttpTestingController,
      );

    session =
      TestBed.inject(
        AuthSessionStore,
      );
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('attaches the Alfresco ticket to authenticated Alfresco requests', () => {
    session.setTicket(
      'TICKET_test-123',
    );

    client
      .get(
        ALFRESCO_CURRENT_USER_URL,
      )
      .subscribe();

    const request =
      http.expectOne(
        ALFRESCO_CURRENT_USER_URL,
      );

    expect(
      request.request.headers.get(
        'Authorization',
      ),
    ).toBe(
      `Basic ${btoa(
        'TICKET_test-123',
      )}`,
    );

    request.flush({
      entry: {},
    });
  });

  it('does not attach a stale ticket to the login request', () => {
    session.setTicket(
      'TICKET_stale',
    );

    client
      .post(
        ALFRESCO_TICKETS_URL,
        {
          userId:
            'walid.salem',

          password:
            'test-password',
        },
      )
      .subscribe();

    const request =
      http.expectOne(
        ALFRESCO_TICKETS_URL,
      );

    expect(
      request.request.headers.has(
        'Authorization',
      ),
    ).toBe(false);

    request.flush({
      entry: {
        id:
          'TICKET_new',

        userId:
          'walid.salem',
      },
    });
  });

  it('clears the local session when Alfresco returns 401', () => {
    session.setTicket(
      'TICKET_expired',
    );

    client
      .get(
        '/alfresco/service/badea/correspondence/inbox',
      )
      .subscribe({
        error: () => {
          // Expected in this test.
        },
      });

    const request =
      http.expectOne(
        '/alfresco/service/badea/correspondence/inbox',
      );

    request.flush(
      {
        error:
          'Unauthorized',
      },
      {
        status: 401,
        statusText:
          'Unauthorized',
      },
    );

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

  it('redirects to login and preserves the current route when an authenticated request expires', () => {
    session.setTicket(
      'TICKET_expired',
    );

    router.url =
      '/president-review';

    client
      .get(
        '/alfresco/service/badea/correspondence/123',
      )
      .subscribe({
        error: () => {
          // Expected in this test.
        },
      });

    const request =
      http.expectOne(
        '/alfresco/service/badea/correspondence/123',
      );

    request.flush(
      {
        error:
          'Unauthorized',
      },
      {
        status: 401,
        statusText:
          'Unauthorized',
      },
    );

    expect(
      router.navigate,
    ).toHaveBeenCalledOnce();

    expect(
      router.navigate,
    ).toHaveBeenCalledWith(
      ['/login'],
      {
        queryParams: {
          returnUrl:
            '/president-review',
        },
      },
    );
  });
});