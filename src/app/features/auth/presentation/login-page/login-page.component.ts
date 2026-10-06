import {
  HttpErrorResponse,
} from '@angular/common/http';

import {
  ChangeDetectionStrategy,
  Component,
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

  readonly isSubmitting =
    signal(false);

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
      .subscribe(
        (isAuthenticated) => {
          if (
            isAuthenticated
          ) {
            void this
              .navigateAfterLogin();
          }
        },
      );
  }

  submit(): void {
    if (
      this.form.invalid ||
      this.isSubmitting()
    ) {
      this.form
        .markAllAsTouched();

      return;
    }

    this.isSubmitting.set(true);
    this.errorMessage.set(null);

    this.auth
      .login(
        this.form.getRawValue(),
      )
      .pipe(
        finalize(() => {
          this.isSubmitting.set(
            false,
          );
        }),
      )
      .subscribe({
        next: () => {
          void this
            .navigateAfterLogin();
        },

        error: (
          error: unknown,
        ) => {
          this.errorMessage.set(
            this.resolveLoginError(
              error,
            ),
          );
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
        .get('returnUrl');

    if (
      !returnUrl ||
      !returnUrl.startsWith('/') ||
      returnUrl.startsWith('//') ||
      returnUrl.startsWith('/login')
    ) {
      return '/inbox';
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
        error.status === 401 ||
        error.status === 403
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