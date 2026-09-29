import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';

import { TranslationService } from '../../../../core/i18n/translation.service';

@Component({
  selector: 'app-pdf-toolbar',
  standalone: true,
  templateUrl: './pdf-toolbar.component.html',
  styleUrl: './pdf-toolbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfToolbarComponent {
  readonly translation = inject(TranslationService);
}