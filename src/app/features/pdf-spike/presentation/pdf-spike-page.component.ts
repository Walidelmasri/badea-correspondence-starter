import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  signal,
  viewChild,
} from '@angular/core';

import { PdfWorkspaceComponent } from '../../../shared/ui/pdf-workspace/pdf-workspace.component';

@Component({
  selector: 'app-pdf-spike-page',
  standalone: true,
  imports: [
    PdfWorkspaceComponent,
  ],
  templateUrl: './pdf-spike-page.component.html',
  styleUrl: './pdf-spike-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfSpikePageComponent implements OnDestroy {
  private readonly pdfWorkspace =
    viewChild.required(PdfWorkspaceComponent);

  private editedDocumentUrl: string | null = null;

  readonly pdfSource = signal(
    '/pdf/local/report-badea.pdf',
  );

  async reloadEditedDocument(): Promise<void> {
    const blob =
      await this.pdfWorkspace().exportDocument();

    if (!blob) {
      return;
    }

    if (this.editedDocumentUrl) {
      URL.revokeObjectURL(this.editedDocumentUrl);
    }

    this.editedDocumentUrl =
      URL.createObjectURL(blob);

    this.pdfSource.set(this.editedDocumentUrl);
  }

  ngOnDestroy(): void {
    if (this.editedDocumentUrl) {
      URL.revokeObjectURL(this.editedDocumentUrl);
    }
  }
}