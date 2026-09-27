import {
  computed,
  inject,
  Injectable,
} from '@angular/core';

import { LanguageService } from './language.service';
import { TRANSLATIONS } from './translations';

@Injectable({
  providedIn: 'root',
})
export class TranslationService {
  private readonly languageService = inject(LanguageService);

  readonly text = computed(
    () => TRANSLATIONS[this.languageService.language()],
  );

  readonly language =
    this.languageService.language.asReadonly();

  readonly direction =
    this.languageService.direction.asReadonly();

  toggleLanguage(): void {
    this.languageService.toggleLanguage();
  }
}