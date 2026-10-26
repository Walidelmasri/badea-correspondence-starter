import {
  HttpErrorResponse,
} from '@angular/common/http';

import {
  ComponentFixture,
  TestBed,
} from '@angular/core/testing';

import {
  provideRouter,
  Router,
} from '@angular/router';

import {
  Observable,
  of,
  Subject,
  throwError,
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
} from '../../../../core/auth/auth.service';

import {
  CurrentUser,
  LoginCredentials,
} from '../../../../core/auth/auth.model';

import {
  LoginPageComponent,
} from './login-page.component';

describe(
  'LoginPageComponent',
  () => {
    let fixture:
      ComponentFixture<LoginPageComponent>;

    let component:
      LoginPageComponent;

    let router:
      Router;

    const authService = {
      ensureAuthenticated:
        vi.fn<
          () =>
            Observable<boolean>
        >(),

      login:
        vi.fn<
          (
            credentials:
              LoginCredentials,
          ) =>
            Observable<CurrentUser>
        >(),
    };

    beforeEach(() => {
      authService
        .ensureAuthenticated
        .mockReset();

      authService
        .login
        .mockReset();

      authService
        .ensureAuthenticated
        .mockReturnValue(
          of(false),
        );

      TestBed.configureTestingModule({
        imports: [
          LoginPageComponent,
        ],

        providers: [
          provideRouter(
            [],
          ),

          {
            provide:
              AuthService,

            useValue:
              authService,
          },
        ],
      });

      router =
        TestBed.inject(
          Router,
        );

      fixture =
        TestBed.createComponent(
          LoginPageComponent,
        );

      component =
        fixture.componentInstance;

      fixture.detectChanges();
    });

    it('shows a loading indicator while restoring an existing session', () => {
      const restore =
        new Subject<boolean>();

      authService
        .ensureAuthenticated
        .mockReturnValue(
          restore,
        );

      const restoringFixture =
        TestBed.createComponent(
          LoginPageComponent,
        );

      restoringFixture
        .detectChanges();

      expect(
        restoringFixture
          .nativeElement
          .querySelector(
            '.login-loading',
          ),
      ).not.toBeNull();

      expect(
        restoringFixture
          .nativeElement
          .querySelector(
            '.login-form',
          ),
      ).toBeNull();

      restore.next(
        false,
      );

      restore.complete();

      restoringFixture
        .detectChanges();

      expect(
        restoringFixture
          .nativeElement
          .querySelector(
            '.login-form',
          ),
      ).not.toBeNull();
    });

    it('keeps the loading state active until navigation completes', async () => {
      let resolveNavigation:
        (
          value: boolean,
        ) => void =
          () => undefined;

      const navigation =
        new Promise<boolean>(
          (
            resolve,
          ) => {
            resolveNavigation =
              resolve;
          },
        );

      vi.spyOn(
        router,
        'navigateByUrl',
      )
        .mockReturnValue(
          navigation,
        );

      authService
        .login
        .mockReturnValue(
          of(
            {} as CurrentUser,
          ),
        );

      component.form
        .setValue({
          userId:
            'walid.salem',

          password:
            'password',
        });

      component.submit();

      fixture.detectChanges();

      expect(
        component
          .isSubmitting(),
      ).toBe(true);

      expect(
        fixture
          .nativeElement
          .querySelector(
            '.login-loading',
          ),
      ).not.toBeNull();

      expect(
        fixture
          .nativeElement
          .querySelector(
            '.login-form',
          ),
      ).toBeNull();

      resolveNavigation(
        true,
      );

      await navigation;

      await fixture
        .whenStable();

      fixture.detectChanges();

      expect(
        component
          .isSubmitting(),
      ).toBe(false);
    });

    it('navigates to Dashboard after login when there is no return URL', async () => {
      const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      authService.login.mockReturnValue(of({} as CurrentUser));

      component.form.setValue({ userId: 'test.user', password: 'test-password' });
      component.submit();
      await fixture.whenStable();

      expect(navigate).toHaveBeenCalledWith('/dashboard');
    });

    it('restores the form when authentication fails', () => {
      authService
        .login
        .mockReturnValue(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 401,
                statusText:
                  'Unauthorized',
              }),
          ),
        );

      component.form
        .setValue({
          userId:
            'walid.salem',

          password:
            'wrong-password',
        });

      component.submit();

      fixture.detectChanges();

      expect(
        component
          .isSubmitting(),
      ).toBe(false);

      expect(
        fixture
          .nativeElement
          .querySelector(
            '.login-form',
          ),
      ).not.toBeNull();

      expect(
        component
          .errorMessage(),
      ).toBe(
        component
          .translation
          .text()
          .auth
          .invalidCredentials,
      );
    });
  },
);