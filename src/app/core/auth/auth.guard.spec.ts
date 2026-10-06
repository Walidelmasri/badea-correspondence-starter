import {
  TestBed,
} from '@angular/core/testing';

import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import {
  firstValueFrom,
  Observable,
  of,
} from 'rxjs';

import {
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest';

import {
  AuthService,
} from './auth.service';

import {
  authGuard,
} from './auth.guard';

describe('authGuard', () => {
  let router: Router;

  const authService = {
    ensureAuthenticated: vi.fn(),
  };

  beforeEach(() => {
    authService
      .ensureAuthenticated
      .mockReset();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),

        {
          provide: AuthService,
          useValue: authService,
        },
      ],
    });

    router =
      TestBed.inject(Router);
  });

  it('allows navigation when the user is authenticated', async () => {
    authService
      .ensureAuthenticated
      .mockReturnValue(
        of(true),
      );

    const result =
      TestBed.runInInjectionContext(
        () =>
          authGuard(
            {} as ActivatedRouteSnapshot,
            {
              url: '/inbox',
            } as RouterStateSnapshot,
          ),
      );

    const guardResult =
      await firstValueFrom(
        result as Observable<
          boolean | UrlTree
        >,
      );

    expect(guardResult)
      .toBe(true);
  });

  it('redirects an unauthenticated user to login', async () => {
    authService
      .ensureAuthenticated
      .mockReturnValue(
        of(false),
      );

    const result =
      TestBed.runInInjectionContext(
        () =>
          authGuard(
            {} as ActivatedRouteSnapshot,
            {
              url: '/inbox',
            } as RouterStateSnapshot,
          ),
      );

    const guardResult =
      await firstValueFrom(
        result as Observable<
          boolean | UrlTree
        >,
      );

    expect(
      guardResult instanceof UrlTree,
    ).toBe(true);

    expect(
      router.serializeUrl(
        guardResult as UrlTree,
      ),
    ).toBe(
      '/login?returnUrl=%2Finbox',
    );
  });

  it('preserves the originally requested URL when redirecting to login', async () => {
    authService
      .ensureAuthenticated
      .mockReturnValue(
        of(false),
      );

    const requestedUrl =
      '/president-review?attachment=annex-a';

    const result =
      TestBed.runInInjectionContext(
        () =>
          authGuard(
            {} as ActivatedRouteSnapshot,
            {
              url: requestedUrl,
            } as RouterStateSnapshot,
          ),
      );

    const guardResult =
      await firstValueFrom(
        result as Observable<
          boolean | UrlTree
        >,
      );

    const redirectUrl =
      router.serializeUrl(
        guardResult as UrlTree,
      );

    expect(redirectUrl)
      .toContain('/login');

    expect(redirectUrl)
      .toContain(
        'returnUrl=',
      );

    const parsedUrl =
      router.parseUrl(
        redirectUrl,
      );

    expect(
      parsedUrl.queryParams[
        'returnUrl'
      ],
    ).toBe(
      requestedUrl,
    );
  });
});