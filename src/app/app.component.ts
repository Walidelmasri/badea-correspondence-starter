import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';

import {
  toSignal,
} from '@angular/core/rxjs-interop';

import {
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';

import {
  filter,
  map,
  startWith,
} from 'rxjs';

import {
  AuthService,
} from './core/auth/auth.service';

import {
  TranslationService,
} from './core/i18n/translation.service';

import { UnreadInboxStore } from './core/notifications/unread-inbox.store';

import { MAIN_NAVIGATION,
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

  readonly notifications = inject(UnreadInboxStore);

  readonly navigation =
    MAIN_NAVIGATION;

  readonly isLoginRoute =
    toSignal(
      this.router.events.pipe(
        filter(
          (
            event,
          ): event is NavigationEnd =>
            event instanceof
            NavigationEnd,
        ),

        map(
          (
            event,
          ) =>
            event
              .urlAfterRedirects
              .startsWith(
                '/login',
              ),
        ),

        startWith(
          this.router.url
            .startsWith(
              '/login',
            ),
        ),
      ),
      {
        initialValue:
          this.router.url
            .startsWith(
              '/login',
            ),
      },
    );

  notificationText(): {title: string; empty: string; unread: string; all: string; failed: string} {
    return this.translation.language() === 'ar' ? {
      title: 'الإشعارات', empty: 'لا توجد مراسلات غير مقروءة.', unread: 'غير مقروء',
      all: 'عرض كل غير المقروء', failed: 'تعذر تحميل الإشعارات',
    } : {
      title: 'Notifications', empty: 'No unread correspondence.', unread: 'Unread',
      all: 'View unread inbox', failed: 'Could not load notifications',
    };
  }

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