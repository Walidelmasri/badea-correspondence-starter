import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'inbox',
  },
  {
    path: 'inbox',
    loadComponent: () =>
      import(
        './features/inbox/presentation/inbox-page.component'
      ).then((module) => module.InboxPageComponent),
  },
  {
    path: 'sent',
    loadComponent: () =>
      import(
        './features/sent/presentation/sent-page.component'
      ).then((module) => module.SentPageComponent),
  },
  {
    path: '**',
    redirectTo: 'inbox',
  },
];