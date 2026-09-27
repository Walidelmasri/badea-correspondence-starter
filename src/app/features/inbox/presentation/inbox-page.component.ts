import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';

import { TranslationService } from '../../../core/i18n/translation.service';

@Component({
  selector: 'app-inbox-page',
  standalone: true,
  templateUrl: './inbox-page.component.html',
  styleUrl: './inbox-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InboxPageComponent {
  readonly translation = inject(TranslationService);
}