import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
} from '@angular/core';
import {
  NgxExtendedPdfViewerModule,
  NgxExtendedPdfViewerService,
} from 'ngx-extended-pdf-viewer';

@Component({
  selector: 'app-pdf-workspace',
  standalone: true,
  imports: [
    NgxExtendedPdfViewerModule,
  ],
  templateUrl: './pdf-workspace.component.html',
  styleUrl: './pdf-workspace.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfWorkspaceComponent {
  private readonly pdfViewerService =
    inject(NgxExtendedPdfViewerService);

  readonly src = input.required<string>();

  exportDocument(): Promise<Blob | undefined> {
    return this.pdfViewerService.getCurrentDocumentAsBlob();
  }
}