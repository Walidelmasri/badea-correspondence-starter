import {
  computed,
  inject,
  Injectable,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { CorrespondenceRepository } from '../domain/correspondence.repository';

@Injectable()
export class InboxFacade {
  private readonly repository = inject(CorrespondenceRepository);

  private readonly selectedCorrespondenceId = signal<string | null>(null);

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

  readonly selectedCorrespondence = computed(
    () => {
      const selectedId = this.selectedCorrespondenceId();

      if (selectedId === null) {
        return null;
      }

      return (
        this.correspondence()
          .find((item) => item.id === selectedId)
        ?? null
      );
    },
  );

  selectCorrespondence(id: string): void {
    this.selectedCorrespondenceId.set(id);
  }
}