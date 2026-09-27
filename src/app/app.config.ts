import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { MockCorrespondenceRepository } from './features/inbox/data-access/mock-correspondence.repository';
import { CorrespondenceRepository } from './features/inbox/domain/correspondence.repository';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),

    {
      provide: CorrespondenceRepository,
      useClass: MockCorrespondenceRepository,
    },
  ],
};