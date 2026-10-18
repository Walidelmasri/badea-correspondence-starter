import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RealMailboxComponent } from '../../../shared/ui/real-mailbox/real-mailbox.component';

@Component({
  selector: 'app-inbox-page',
  standalone: true,
  imports: [RealMailboxComponent],
  templateUrl: './inbox-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InboxPageComponent {}
