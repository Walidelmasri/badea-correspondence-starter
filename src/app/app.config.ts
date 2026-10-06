import {
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';

import {
  ApplicationConfig,
} from '@angular/core';

import {
  provideRouter,
} from '@angular/router';

import {
  routes,
} from './app.routes';

import {
  authInterceptor,
} from './core/auth/auth.interceptor';

import {
  MockCorrespondenceRepository,
} from './features/inbox/data-access/mock-correspondence.repository';

import {
  CorrespondenceRepository,
} from './features/inbox/domain/correspondence.repository';

export const appConfig:
  ApplicationConfig = {
  providers: [
    provideRouter(routes),

    provideHttpClient(
      withInterceptors([
        authInterceptor,
      ]),
    ),

    {
      provide:
        CorrespondenceRepository,

      useClass:
        MockCorrespondenceRepository,
    },
  ],
};