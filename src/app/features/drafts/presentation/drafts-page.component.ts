import {
  ChangeDetectionStrategy,
  Component,
} from '@angular/core';

@Component({
  selector: 'app-drafts-page',
  standalone: true,
  template: `
    <section>
      <h1>Drafts</h1>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DraftsPageComponent {}