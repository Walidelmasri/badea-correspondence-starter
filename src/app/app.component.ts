import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';

import {
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';

import {
  AuthService,
} from './core/auth/auth.service';

import {
  TranslationService,
} from './core/i18n/translation.service';

import {
  MAIN_NAVIGATION,
} from './core/navigation/navigation.config';

@Component({
  selector: 'app-root',

  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],

  templateUrl:
    './app.component.html',

  styleUrl:
    './app.component.scss',

  changeDetection:
    ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  private readonly router =
    inject(Router);

  readonly auth =
    inject(AuthService);

  readonly translation =
    inject(TranslationService);

  readonly navigation =
    MAIN_NAVIGATION;

  toggleLanguage(): void {
    this.translation
      .toggleLanguage();
  }

  logout(): void {
    this.auth
      .logout()
      .subscribe({
        complete: () => {
          void this.router
            .navigateByUrl(
              '/login',
            );
        },

        error: () => {
          void this.router
            .navigateByUrl(
              '/login',
            );
        },
      });
  }
}