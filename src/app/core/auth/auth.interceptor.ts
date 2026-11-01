import {
  HttpErrorResponse,
  HttpInterceptorFn,
} from '@angular/common/http';

import { inject } from '@angular/core';

import {
  Router,
} from '@angular/router';

import {
  catchError,
  throwError,
} from 'rxjs';

import {
  ALFRESCO_CURRENT_TICKET_URL,
  ALFRESCO_CURRENT_USER_URL,
  ALFRESCO_TICKETS_URL,
} from './auth.constants';

import {
  AuthSessionStore,
} from './auth-session.store';

export const authInterceptor:
  HttpInterceptorFn =
  (request, next) => {
    const session =
      inject(AuthSessionStore);

    const router =
      inject(Router);

    const ticket =
      session.ticket();

    const isAlfrescoRequest =
      request.url.startsWith(
        '/alfresco/',
      );

    const isLoginRequest =
      request.method === 'POST' &&
      request.url ===
        ALFRESCO_TICKETS_URL;

    const isLogoutRequest =
      request.method === 'DELETE' &&
      request.url ===
        ALFRESCO_CURRENT_TICKET_URL;

    const isCurrentUserRequest =
      request.url ===
        ALFRESCO_CURRENT_USER_URL;

    const authenticatedRequest =
      isAlfrescoRequest &&
      !isLoginRequest &&
      ticket
        ? request.clone({
            setHeaders: {
              Authorization:
                `Basic ${btoa(ticket)}`,
            },
          })
        : request;

    // A workspace is an explicit per-request hint, never a security token.
    // The Java endpoint re-checks the authenticated employee's live assignment.
    const workspaceUnit = session.workspaceUnit();
    const scopedRequest = workspaceUnit
      && authenticatedRequest.url.startsWith('/alfresco/service/badea/')
      && !authenticatedRequest.url.endsWith('/correspondence/me')
      ? authenticatedRequest.clone({
          params: authenticatedRequest.params.set('workspaceUnit', workspaceUnit),
        })
      : authenticatedRequest;

    return next(
      scopedRequest,
    ).pipe(
      catchError((error: unknown) => {
        if (
          error instanceof
            HttpErrorResponse &&
          error.status === 401 &&
          isAlfrescoRequest &&
          !isLoginRequest
        ) {
          session.clear();

          /*
           * When restoring authentication,
           * the route guard owns the redirect.
           *
           * Avoid racing the guard and losing
           * its state.url return URL.
           */
          if (
            !isCurrentUserRequest &&
            !isLogoutRequest &&
            !router.url.startsWith(
              '/login',
            )
          ) {
            const returnUrl =
              router.url.startsWith('/')
                ? router.url
                : '/inbox';

            void router.navigate(
              ['/login'],
              {
                queryParams: {
                  returnUrl,
                },
              },
            );
          }
        }

        return throwError(
          () => error,
        );
      }),
    );
  };