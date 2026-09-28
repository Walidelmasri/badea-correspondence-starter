import {
  ChangeDetectionStrategy,
  Component,
} from '@angular/core';

@Component({
  selector: 'app-sent-page',
  standalone: true,
  template: `
    <section>
      <h1>Sent</h1>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SentPageComponent {}