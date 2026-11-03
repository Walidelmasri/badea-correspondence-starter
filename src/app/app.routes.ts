import {
  Routes,
} from '@angular/router';

import {
  authGuard,
} from './core/auth/auth.guard';

import {
  composeRoleGuard,
  directorRoleGuard,
  officeRoleGuard,
  secretaryDashboardGuard,
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
        canActivate: [secretaryDashboardGuard],
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
        path: 'office/new',
        canActivate: [officeRoleGuard],
        loadComponent: () =>
          import('./features/office-new/presentation/office-new-page.component')
            .then(module => module.OfficeNewPageComponent),
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
        path: 'tarasol/delegated',
        loadComponent: () => import('./features/tarasol-delegation/tarasol-delegation-page.component')
          .then(module => module.TarasolDelegationPageComponent),
      },
      {
        path: 'tarasol/secretary',
        loadComponent: () => import('./features/tarasol-secretary/tarasol-secretary-page.component')
          .then(module => module.TarasolSecretaryPageComponent),
        data: { mode: 'secretary' },
      },
      {
        path: 'tarasol/approvals',
        loadComponent: () => import('./features/tarasol-secretary/tarasol-secretary-page.component')
          .then(module => module.TarasolSecretaryPageComponent),
        data: { mode: 'manager' },
      },
      {
        path: 'tarasol/admin/access',
        loadComponent: () => import('./features/tarasol-access/presentation/tarasol-access-page.component')
          .then(module => module.TarasolAccessPageComponent),
        data: { mode: 'admin' },
      },
      {
        path: 'tarasol/team/access',
        loadComponent: () => import('./features/tarasol-access/presentation/tarasol-access-page.component')
          .then(module => module.TarasolAccessPageComponent),
        data: { mode: 'team' },
      },
      {
        path: '**',
        redirectTo: 'dashboard',
      },
    ],
  },
];