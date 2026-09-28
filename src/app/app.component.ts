import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';
import {
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';

import { TranslationService } from './core/i18n/translation.service';
import { MAIN_NAVIGATION } from './core/navigation/navigation.config';

@Component({
  selector: 'app-root',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  readonly translation = inject(TranslationService);

  readonly navigation = MAIN_NAVIGATION;

  toggleLanguage(): void {
    this.translation.toggleLanguage();
  }
}