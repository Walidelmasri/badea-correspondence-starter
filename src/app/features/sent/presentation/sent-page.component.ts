import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';
import { Router } from '@angular/router';

import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceWorkspaceComponent } from '../../../shared/ui/correspondence-workspace/correspondence-workspace.component';

@Component({
  selector: 'app-sent-page',
  standalone: true,
  imports: [
    CorrespondenceWorkspaceComponent,
  ],
  templateUrl: './sent-page.component.html',
  styleUrl: './sent-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SentPageComponent {
  private readonly router = inject(Router);

  readonly translation = inject(TranslationService);

  openCompose(): void {
    void this.router.navigate(['/compose']);
  }
}