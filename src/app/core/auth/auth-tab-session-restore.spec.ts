import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  ALFRESCO_CURRENT_USER_URL, BADEA_CURRENT_EMPLOYEE_URL,
} from './auth.constants';
import { AuthService } from './auth.service';
import { AuthSessionStore } from './auth-session.store';
import { AuthTabSessionHandoffService } from './auth-tab-session-handoff.service';

describe('same-browser handoff still requires server authorization', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let session: AuthSessionStore;
  const handoff = { requestTicket: () => of('TICKET_received-from-other-tab') };
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [
      AuthService, AuthSessionStore,
      { provide: AuthTabSessionHandoffService, useValue: handoff },
      provideHttpClient(), provideHttpClientTesting(),
    ] });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    session = TestBed.inject(AuthSessionStore);
  });
  afterEach(() => { http.verify(); sessionStorage.clear(); });

  it('clears a transferred ticket when Alfresco rejects it', () => {
    let result: boolean | undefined;
    auth.ensureAuthenticated().subscribe(value => { result = value; });
    http.expectOne(ALFRESCO_CURRENT_USER_URL).flush('Unauthorized', {
      status: 401, statusText: 'Unauthorized',
    });
    expect(result).toBe(false);
    expect(session.ticket()).toBeNull();
    expect(session.isAuthenticated()).toBe(false);
  });

  it('requires an active BADEA profile before treating a transferred ticket as authenticated', () => {
    let result: boolean | undefined;
    auth.ensureAuthenticated().subscribe(value => { result = value; });
    http.expectOne(ALFRESCO_CURRENT_USER_URL).flush({ entry: {
      id: 'walid.salem', firstName: 'Walid', lastName: 'Salem',
      displayName: 'Walid Salem', email: 'walid.salem@badea.org',
      enabled: true, capabilities: { isGuest: false, isAdmin: false, isMutable: false },
    } });
    http.expectOne(BADEA_CURRENT_EMPLOYEE_URL).flush('Forbidden', {
      status: 403, statusText: 'Forbidden',
    });
    expect(result).toBe(false);
    expect(session.ticket()).toBeNull();
    expect(session.isAuthenticated()).toBe(false);
  });
});
