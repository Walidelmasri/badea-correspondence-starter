import {
  Routes,
} from '@angular/router';

import {
  authGuard,
} from './core/auth/auth.guard';

import {
  composeRoleGuard,
  directorRoleGuard,
} from './core/auth/correspondence-role.guard';

export const routes: Routes = [
  {
    path: 'login',

    loadComponent: () =>
      import(
        './features/auth/presentation/login-page/login-page.component'
      ).then(
        (module) =>
          module.LoginPageComponent,
      ),
  },

  {
    path: 'access-denied',
    loadComponent: () =>
      import('./features/auth/presentation/access-denied-page.component')
        .then(module => module.AccessDeniedPageComponent),
  },

  {
    path: '',

    canActivateChild: [
      authGuard,
    ],

    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'dashboard',
      },

      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/presentation/dashboard-page.component')
            .then(module => module.DashboardPageComponent),
      },

      {
        path: 'inbox',

        loadComponent: () =>
          import(
            './features/inbox/presentation/inbox-page.component'
          ).then(
            (module) =>
              module.InboxPageComponent,
          ),
      },

      {
        path: 'sent',

        loadComponent: () =>
          import(
            './features/sent/presentation/sent-page.component'
          ).then(
            (module) =>
              module.SentPageComponent,
          ),
      },

      {
        path: 'drafts',

        loadComponent: () =>
          import(
            './features/drafts/presentation/drafts-page.component'
          ).then(
            (module) =>
              module.DraftsPageComponent,
          ),
      },

      {
        path: 'compose',

        canActivate: [composeRoleGuard],

        loadComponent: () =>
          import(
            './features/compose/presentation/compose-page.component'
          ).then(
            (module) => module.ComposePageComponent,
          ),
      },

      {
        path: 'pdf-spike',

        loadComponent: () =>
          import(
            './features/pdf-spike/presentation/pdf-spike-page.component'
          ).then(
            (module) =>
              module.PdfSpikePageComponent,
          ),
      },

      {
        path: 'president-review/:technicalId',

        loadComponent: () =>
          import(
            './features/president-review/presentation/president-review-page.component'
          ).then(
            (module) =>
              module.PresidentReviewPageComponent,
          ),
      },

      {
        path: 'president-review',
        pathMatch: 'full',
        redirectTo: 'inbox',
      },
      {
        path: 'director/new',

        canActivate: [directorRoleGuard],

        loadComponent: () =>
          import(
            './features/director/presentation/director-new-page.component'
          ).then(
            (module) => module.DirectorNewPageComponent,
          ),
      },
      {
        path: '**',
        redirectTo: 'dashboard',
      },
    ],
  },
];