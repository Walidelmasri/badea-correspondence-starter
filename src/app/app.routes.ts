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
    path: 'drafts',
    loadComponent: () =>
      import(
        './features/drafts/presentation/drafts-page.component'
      ).then((module) => module.DraftsPageComponent),
  },
  {
    path: 'compose',
    loadComponent: () =>
      import(
        './features/compose/presentation/compose-page.component'
      ).then((module) => module.ComposePageComponent),
  },
  {
    path: 'pdf-spike',
    loadComponent: () =>
      import(
        './features/pdf-spike/presentation/pdf-spike-page.component'
      ).then((module) => module.PdfSpikePageComponent),
  },
  {
    path: '**',
    redirectTo: 'inbox',
  },
];