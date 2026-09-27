import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';

import { TranslationService } from '../../../core/i18n/translation.service';
import { InboxFacade } from '../application/inbox.facade';

@Component({
  selector: 'app-inbox-page',
  standalone: true,
  templateUrl: './inbox-page.component.html',
  styleUrl: './inbox-page.component.scss',
  providers: [
    InboxFacade,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InboxPageComponent {
  readonly translation = inject(TranslationService);
  readonly inbox = inject(InboxFacade);
}