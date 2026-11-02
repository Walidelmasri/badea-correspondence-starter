import { TestBed } from '@angular/core/testing';

import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import {
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest';

import {
  AuthSessionStore,
} from './auth-session.store';

import {
  CorrespondenceRole,
  CurrentUser,
} from './auth.model';

import {
  composeRoleGuard,
  directorRoleGuard,
  officeRoleGuard,
} from './correspondence-role.guard';

const baseUser: CurrentUser = {
  id: 'test.user',
  firstName: 'Test',
  lastName: 'User',
  displayName: 'Test User',
  enabled: true,

  capabilities: {
    isGuest: false,
    isAdmin: false,
    isMutable: false,
  },
};

describe('Correspondence role guards', () => {
  let router: Router;

  const currentUser = vi.fn();

  beforeEach(() => {
    currentUser.mockReset();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),

        {
          provide: AuthSessionStore,
          useValue: { currentUser },
        },
      ],
    });

    router = TestBed.inject(Router);
  });

  function setRole(
    role: CorrespondenceRole | null,
  ): void {
    currentUser.mockReturnValue({
      ...baseUser,

      employeeProfile: {
        employeeId: '10001',
        username: 'test.user',
        nameEnglish: 'Test User',
        nameArabic: null,

        correspondenceRole: role,

        department: {
          code: '117',
          nameEnglish: 'Test Department',
          nameArabic: null,
        },
      },
    } satisfies CurrentUser);
  }

  function runComposeGuard(): boolean | UrlTree {
    return TestBed.runInInjectionContext(
      () => composeRoleGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/compose' } as RouterStateSnapshot,
      ),
    ) as boolean | UrlTree;
  }

  function runDirectorGuard(): boolean | UrlTree {
    return TestBed.runInInjectionContext(
      () => directorRoleGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/director/new' } as RouterStateSnapshot,
      ),
    ) as boolean | UrlTree;
  }

  it('routes Directors to their form', () => {
    setRole('DIRECTOR');

    const result = runComposeGuard();

    expect(result instanceof UrlTree).toBe(true);

    expect(
      router.serializeUrl(result as UrlTree),
    ).toBe('/director/new');
  });

  it('opens the shared executive form for the President', () => {
    setRole('PRESIDENT');

    expect(runComposeGuard()).toBe(true);
  });

  it('routes President Office users to their own intake form', () => {
    setRole('PRESIDENT_OFFICE');
    const result = runComposeGuard();
    expect(result instanceof UrlTree).toBe(true);
    expect(router.serializeUrl(result as UrlTree)).toBe('/office/new');
  });

  it('does not let the President open the Office intake editor', () => {
    setRole('PRESIDENT');
    const result = TestBed.runInInjectionContext(() => officeRoleGuard(
      {} as ActivatedRouteSnapshot,
      { url: '/office/new' } as RouterStateSnapshot,
    )) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/compose');
  });

  it('does not guess a role from department membership', () => {
    setRole(null);

    const result = runComposeGuard();

    expect(
      router.serializeUrl(result as UrlTree),
    ).toBe('/inbox');
  });

  it('blocks Compose when no employee profile exists', () => {
    currentUser.mockReturnValue(baseUser);

    const result = runComposeGuard();

    expect(
      router.serializeUrl(result as UrlTree),
    ).toBe('/inbox');
  });

  it('allows Directors to reopen saved drafts', () => {
    setRole('DIRECTOR');

    expect(runDirectorGuard()).toBe(true);
  });

  it('redirects the President away from the Director form', () => {
    setRole('PRESIDENT');

    const result = runDirectorGuard();

    expect(
      router.serializeUrl(result as UrlTree),
    ).toBe('/compose');
  });

  it('redirects Office users from the Director form to Office intake', () => {
    setRole('PRESIDENT_OFFICE');
    const result = runDirectorGuard() as UrlTree;
    expect(router.serializeUrl(result)).toBe('/office/new');
  });

  it('blocks users without a correspondence role', () => {
    setRole(null);

    const result = runDirectorGuard();

    expect(
      router.serializeUrl(result as UrlTree),
    ).toBe('/inbox');
  });
});