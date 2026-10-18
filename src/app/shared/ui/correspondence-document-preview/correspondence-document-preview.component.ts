import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Subscription } from 'rxjs';

import { TranslationService } from '../../../core/i18n/translation.service';
import {
  CorrespondenceApiService,
  CorrespondenceDocumentUpload,
} from '../../data-access/correspondence-api.service';

@Component({
  selector: 'app-correspondence-document-preview',
  standalone: true,
  templateUrl: './correspondence-document-preview.component.html',
  styleUrl: './correspondence-document-preview.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CorrespondenceDocumentPreviewComponent implements AfterViewInit, OnDestroy {
  private readonly api = inject(CorrespondenceApiService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly translation = inject(TranslationService);
  readonly technicalId = input.required<string>();
  readonly document = input.required<CorrespondenceDocumentUpload>();
  readonly closed = output<void>();

  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);

  private request: Subscription | null = null;
  private objectUrl: string | null = null;
  private destroyed = false;

  ngAfterViewInit(): void {
    this.dialog().nativeElement.showModal();
    this.request = this.api.getDocumentContent(
      this.technicalId(),
      this.document().nodeId,
    ).subscribe({
      next: (blob) => {
        if (this.destroyed) return;
        if (blob.size === 0 || blob.type !== 'application/pdf') {
          this.failed.set(true);
          this.loading.set(false);
          return;
        }
        this.objectUrl = URL.createObjectURL(blob);
        // Only trust an in-memory Blob URL created from our authorized response.
        this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl));
        this.loading.set(false);
      },
      error: () => {
        if (this.destroyed) return;
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.request?.unsubscribe();
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
  }
}
