import {
  HttpClient,
  HttpErrorResponse,
} from '@angular/common/http';

import {
  inject,
  Injectable,
  signal,
} from '@angular/core';

import {
  catchError,
  finalize,
  map,
  Observable,
  of,
  shareReplay,
  switchMap,
  tap,
  throwError,
  timeout,
  TimeoutError,
} from 'rxjs';

import {
  ALFRESCO_CURRENT_TICKET_URL,
  ALFRESCO_CURRENT_USER_URL,
  ALFRESCO_TICKETS_URL,
  BADEA_CURRENT_EMPLOYEE_URL,
} from './auth.constants';

import {
  AlfrescoEntryResponse,
  AlfrescoTicket,
  CurrentUser,
  EmployeeProfile,
  LoginCredentials,
} from './auth.model';

import {
  AuthSessionStore,
} from './auth-session.store';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http =
    inject(HttpClient);

  private readonly session =
    inject(AuthSessionStore);

  private restoreRequest:
    Observable<boolean> | null = null;

  private readonly deniedState = signal(false);
  readonly accessDenied = this.deniedState.asReadonly();

  readonly currentUser =
    this.session.currentUser;

  readonly isAuthenticated =
    this.session.isAuthenticated;

  readonly initials =
    this.session.initials;

  login(
    credentials: LoginCredentials,
  ): Observable<CurrentUser> {
    const userId =
      credentials.userId.trim();

    this.session.clear();
    this.deniedState.set(false);

    return this.http
      .post<
        AlfrescoEntryResponse<AlfrescoTicket>
      >(
        ALFRESCO_TICKETS_URL,
        {
          userId,
          password: credentials.password,
        } satisfies LoginCredentials,
      )
      .pipe(
        tap((response) => {
          this.session.setTicket(
            response.entry.id,
          );
        }),

        switchMap(() =>
          this.loadCurrentUser(),
        ),

        catchError((error: unknown) => {
          this.session.clear();

          return throwError(
            () => error,
          );
        }),
      );
  }

  ensureAuthenticated():
    Observable<boolean> {
    if (this.session.isAuthenticated()) {
      return of(true);
    }

    if (!this.session.ticket()) {
      return of(false);
    }

    if (this.restoreRequest) {
      return this.restoreRequest;
    }

    this.restoreRequest =
      this.loadCurrentUser().pipe(
        map(() => true),

        catchError(() => {
          this.session.clear();

          return of(false);
        }),

        finalize(() => {
          this.restoreRequest = null;
        }),

        shareReplay({
          bufferSize: 1,
          refCount: false,
        }),
      );

    return this.restoreRequest;
  }

  logout(): Observable<void> {
    this.deniedState.set(false);
    if (!this.session.ticket()) {
      this.session.clear();

      return of(undefined);
    }

    return this.http
      .delete<void>(
        ALFRESCO_CURRENT_TICKET_URL,
      )
      .pipe(
        catchError((error: unknown) => {
          if (
            error instanceof
              HttpErrorResponse &&
            (
              error.status === 401 ||
              error.status === 404
            )
          ) {
            return of(undefined);
          }

          return throwError(
            () => error,
          );
        }),

        finalize(() => {
          this.session.clear();
        }),
      );
  }

  private loadCurrentUser():
    Observable<CurrentUser> {
    return this.http
      .get<
        AlfrescoEntryResponse<CurrentUser>
      >(
        ALFRESCO_CURRENT_USER_URL,
      )
      .pipe(
        map(
          (response) =>
            response.entry,
        ),

        switchMap((user) =>
          this.loadEmployeeProfile().pipe(
            map((employeeProfile) => {
              if (
                !user.enabled ||
                !isTarasolRole(employeeProfile.correspondenceRole)
              ) {
                throw new HttpErrorResponse({
                  status: 403,
                  statusText: 'Forbidden',
                });
              }

              return { ...user, employeeProfile };
            }),
          ),
        ),

        catchError((error: unknown) => {
          if (
            error instanceof HttpErrorResponse &&
            error.status === 403
          ) {
            this.deniedState.set(true);
          }
          return throwError(() => error);
        }),

        tap((user) => {
          this.session.setCurrentUser(
            user,
          );
        }),
      );
  }

  private loadEmployeeProfile(): Observable<EmployeeProfile> {
    // A directory or role-lookup failure is never a successful login.
    // Unsubscribe from a stalled request instead of waiting for Nginx's 504.
    // Alfresco group authorization is still mandatory on the backend.
    return this.http.get<EmployeeProfile>(BADEA_CURRENT_EMPLOYEE_URL).pipe(
      timeout({ first: 18_000 }),
      catchError((error: unknown) => {
        if (error instanceof TimeoutError) {
          return throwError(() => new HttpErrorResponse({
            status: 503,
            statusText: 'Employee directory timed out',
            url: BADEA_CURRENT_EMPLOYEE_URL,
          }));
        }
        return throwError(() => error);
      }),
    );
  }
}

function isTarasolRole(role: string | null | undefined): boolean {
  return role === 'PRESIDENT' ||
    role === 'PRESIDENT_OFFICE' ||
    role === 'DIRECTOR';
}
