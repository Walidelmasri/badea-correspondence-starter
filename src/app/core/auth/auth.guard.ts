import {
  inject,
} from '@angular/core';

import {
  CanActivateChildFn,
  Router,
} from '@angular/router';

import {
  map,
} from 'rxjs';

import {
  AuthService,
} from './auth.service';

export const authGuard:
  CanActivateChildFn =
  (
    _route,
    state,
  ) => {
    const auth =
      inject(AuthService);

    const router =
      inject(Router);

    return auth
      .ensureAuthenticated()
      .pipe(
        map(
          (isAuthenticated) => {
            if (
              isAuthenticated
            ) {
              return true;
            }

            return router
              .createUrlTree(
                ['/login'],
                {
                  queryParams: {
                    returnUrl:
                      state.url,
                  },
                },
              );
          },
        ),
      );
  };