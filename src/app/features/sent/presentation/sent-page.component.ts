import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RealMailboxComponent } from '../../../shared/ui/real-mailbox/real-mailbox.component';

@Component({
  selector: 'app-sent-page',
  standalone: true,
  imports: [RealMailboxComponent],
  templateUrl: './sent-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SentPageComponent {}
