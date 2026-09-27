import {
  computed,
  inject,
  Injectable,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { CorrespondenceRepository } from '../domain/correspondence.repository';

@Injectable()
export class InboxFacade {
  private readonly repository = inject(CorrespondenceRepository);

  readonly correspondence = toSignal(
    this.repository.getInbox(),
    {
      initialValue: [],
    },
  );

  readonly unreadCount = computed(
    () =>
      this.correspondence()
        .filter((item) => item.status === 'unread')
        .length,
  );
}