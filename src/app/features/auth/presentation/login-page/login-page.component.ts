import {
  HttpErrorResponse,
} from '@angular/common/http';

import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';

import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import {
  ActivatedRoute,
  Router,
} from '@angular/router';

import {
  finalize,
  from,
  switchMap,
  take,
} from 'rxjs';

import {
  AuthService,
} from '../../../../core/auth/auth.service';

import {
  TranslationService,
} from '../../../../core/i18n/translation.service';

@Component({
  selector: 'app-login-page',

  imports: [
    ReactiveFormsModule,
  ],

  templateUrl:
    './login-page.component.html',

  styleUrl:
    './login-page.component.scss',

  changeDetection:
    ChangeDetectionStrategy.OnPush,
})
export class LoginPageComponent {
  private readonly auth =
    inject(AuthService);

  private readonly router =
    inject(Router);

  private readonly route =
    inject(ActivatedRoute);

  readonly translation =
    inject(TranslationService);

  readonly isCheckingSession =
    signal(true);

  readonly isSubmitting =
    signal(false);

  readonly isBusy =
    computed(
      () =>
        this.isCheckingSession() ||
        this.isSubmitting(),
    );

  readonly errorMessage =
    signal<string | null>(null);

  readonly form =
    new FormGroup({
      userId: new FormControl(
        '',
        {
          nonNullable: true,

          validators: [
            Validators.required,
          ],
        },
      ),

      password: new FormControl(
        '',
        {
          nonNullable: true,

          validators: [
            Validators.required,
          ],
        },
      ),
    });

  constructor() {
    this.auth
      .ensureAuthenticated()
      .pipe(
        take(1),
      )
      .subscribe({
        next: (
          isAuthenticated,
        ) => {
          if (!isAuthenticated) {
            if (this.auth.accessDenied()) {
              void this.router.navigateByUrl('/access-denied');
            }
            this.isCheckingSession.set(false);
            return;
          }

          void this
            .navigateAfterLogin()
            .then(
              (
                navigated,
              ) => {
                if (
                  !navigated
                ) {
                  this.errorMessage.set(
                    this.translation
                      .text()
                      .auth
                      .unavailable,
                  );
                }
              },
            )
            .catch(
              () => {
                this.errorMessage.set(
                  this.translation
                    .text()
                    .auth
                    .unavailable,
                );
              },
            )
            .finally(
              () => {
                this.isCheckingSession.set(
                  false,
                );
              },
            );
        },

        error: () => {
          this.isCheckingSession.set(
            false,
          );

          this.errorMessage.set(
            this.translation
              .text()
              .auth
              .unavailable,
          );
        },
      });
  }

  submit(): void {
    if (
      this.form.invalid ||
      this.isBusy()
    ) {
      this.form
        .markAllAsTouched();

      return;
    }

    this.isSubmitting.set(
      true,
    );

    this.errorMessage.set(
      null,
    );

    this.auth
      .login(
        this.form.getRawValue(),
      )
      .pipe(
        /*
         * Keep the loading state active until routing
         * has actually finished.
         *
         * Previously login() completed after currentUser
         * was populated but before navigateByUrl()
         * finished, causing the login form to flash back.
         */
        switchMap(
          () =>
            from(
              this.navigateAfterLogin(),
            ),
        ),

        finalize(
          () => {
            this.isSubmitting.set(
              false,
            );
          },
        ),
      )
      .subscribe({
        next: (
          navigated,
        ) => {
          if (
            !navigated
          ) {
            this.errorMessage.set(
              this.translation
                .text()
                .auth
                .unavailable,
            );
          }
        },

        error: (error: unknown) => {
          if (
            error instanceof HttpErrorResponse &&
            error.status === 403
          ) {
            void this.router.navigateByUrl('/access-denied');
            return;
          }
          this.errorMessage.set(this.resolveLoginError(error));
        },
      });
  }

  toggleLanguage(): void {
    this.translation
      .toggleLanguage();
  }

  private navigateAfterLogin():
    Promise<boolean> {
    return this.router
      .navigateByUrl(
        this.getReturnUrl(),
      );
  }

  private getReturnUrl():
    string {
    const returnUrl =
      this.route
        .snapshot
        .queryParamMap
        .get(
          'returnUrl',
        );

    if (
      !returnUrl ||
      !returnUrl.startsWith('/') ||
      returnUrl.startsWith('//') ||
      returnUrl.startsWith('/login')
    ) {
      return '/dashboard';
    }

    return returnUrl;
  }

  private resolveLoginError(
    error: unknown,
  ): string {
    if (
      error instanceof
        HttpErrorResponse &&
      (
        error.status === 401
      )
    ) {
      return this.translation
        .text()
        .auth
        .invalidCredentials;
    }

    return this.translation
      .text()
      .auth
      .unavailable;
  }
}