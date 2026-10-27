import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslationService } from '../../../core/i18n/translation.service';

@Component({
  selector: 'app-access-denied-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="denied-page">
      <section class="denied-card" role="alert" aria-labelledby="denied-heading">
        <img class="denied-brand" src="branding/tarasol-logo.png"
          alt="تراسل | Tarasol" width="226" height="100" />
        <div class="denied-face" aria-hidden="true">☹</div>
        <strong class="denied-code">403</strong>
        <h1 id="denied-heading">{{ translation.text().auth.accessDeniedTitle }}</h1>
        <p>{{ translation.text().auth.accessDeniedDescription }}</p>
        <a routerLink="/login">{{ translation.text().auth.returnToLogin }}</a>
      </section>
    </main>
  `,
  styles: [`
    :host { display: block; min-height: 100dvh; }
    .denied-page { min-height: 100dvh; display: grid; place-items: center;
      padding: 1.5rem; background: var(--color-background); }
    .denied-card { width: min(100%, 29rem); text-align: center;
      background: var(--color-surface); border: 1px solid var(--color-border);
      border-radius: var(--radius-xl); box-shadow: var(--shadow-md);
      padding: clamp(1.5rem, 4vw, 2.5rem); }
    .denied-brand { display: block; max-width: 75%; height: auto;
      margin: 0 auto 1.5rem; }
    .denied-face { font-size: 3.6rem; line-height: 1; color: var(--color-text-secondary); }
    .denied-code { display: block; margin-top: 0.6rem; color: var(--color-primary-800);
      font-size: 2rem; }
    h1 { margin: 1rem 0 0.75rem; color: var(--color-text-primary); }
    p { color: var(--color-text-secondary); line-height: 1.7; }
    a { display: inline-block; margin-top: 0.9rem; padding: 0.8rem 1.3rem;
      color: white; background: var(--color-primary-800);
      border-radius: var(--radius-md); text-decoration: none; font-weight: 700; }
    a:focus-visible { outline: 3px solid var(--color-primary-600); outline-offset: 3px; }
  `],
})
export class AccessDeniedPageComponent {
  readonly translation = inject(TranslationService);
}
