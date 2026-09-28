import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';

import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceWorkspaceComponent } from '../../../shared/ui/correspondence-workspace/correspondence-workspace.component';

@Component({
  selector: 'app-sent-page',
  standalone: true,
  imports: [
    CorrespondenceWorkspaceComponent,
  ],
  templateUrl: './sent-page.component.html',
  styleUrl: './sent-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SentPageComponent {
  readonly translation = inject(TranslationService);
}