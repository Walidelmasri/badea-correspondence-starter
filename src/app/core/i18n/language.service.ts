import { DOCUMENT } from '@angular/common';
import {
  inject,
  Injectable,
  signal,
} from '@angular/core';

export type AppLanguage = 'ar' | 'en';
export type TextDirection = 'rtl' | 'ltr';

@Injectable({
  providedIn: 'root',
})
export class LanguageService {
  private readonly document = inject(DOCUMENT);

  readonly language = signal<AppLanguage>('ar');

  readonly direction = signal<TextDirection>('rtl');

  setLanguage(language: AppLanguage): void {
    const direction: TextDirection =
      language === 'ar'
        ? 'rtl'
        : 'ltr';

    this.language.set(language);
    this.direction.set(direction);

    this.document.documentElement.lang = language;
    this.document.documentElement.dir = direction;
  }

  toggleLanguage(): void {
    this.setLanguage(
      this.language() === 'ar'
        ? 'en'
        : 'ar',
    );
  }
}