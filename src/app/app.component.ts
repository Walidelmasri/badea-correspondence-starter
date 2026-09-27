import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { TranslationService } from './core/i18n/translation.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  readonly translation = inject(TranslationService);

  toggleLanguage(): void {
    this.translation.toggleLanguage();
  }
}