import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';
import { Router } from '@angular/router';

import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceWorkspaceComponent } from '../../../shared/ui/correspondence-workspace/correspondence-workspace.component';
import { InboxFacade } from '../application/inbox.facade';
import { CORRESPONDENCE_INSTRUCTION_LABELS } from './correspondence-instruction.labels';

@Component({
  selector: 'app-inbox-page',
  standalone: true,
  imports: [
    CorrespondenceWorkspaceComponent,
  ],
  templateUrl: './inbox-page.component.html',
  styleUrl: './inbox-page.component.scss',
  providers: [
    InboxFacade,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InboxPageComponent {
  private readonly router = inject(Router);

  readonly translation = inject(TranslationService);
  readonly inbox = inject(InboxFacade);

  readonly instructionLabels =
    CORRESPONDENCE_INSTRUCTION_LABELS;

  openCompose(): void {
    void this.router.navigate(['/compose']);
  }
}