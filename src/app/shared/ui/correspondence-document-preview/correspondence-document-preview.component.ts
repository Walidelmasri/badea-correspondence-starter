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
import { TarasolSecretaryApiService } from '../../../features/tarasol-secretary/tarasol-secretary-api.service';
import { TarasolRoundContinuationApiService } from '../../../features/tarasol-secretary/tarasol-round-continuation-api.service';

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
  private readonly secretaryApi = inject(TarasolSecretaryApiService);
  private readonly roundApi = inject(TarasolRoundContinuationApiService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly translation = inject(TranslationService);
  readonly technicalId = input.required<string>();
  readonly document = input.required<CorrespondenceDocumentUpload>();
  readonly secretaryUnitCode = input<string | null>(null);
  readonly secretaryPersonalDraftUnitCode = input<string | null>(null);
  readonly secretaryPersonalSentUnitCode = input<string | null>(null);
  readonly secretaryManagerPendingUnitCode = input<string | null>(null);
  readonly managerRoundRequest = input<{ readonly unitCode: string; readonly requestId: string } | null>(null);
  readonly secretaryRoundRequest = input<{ readonly unitCode: string; readonly requestId: string } | null>(null);
  readonly closed = output<void>();

  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);

  private request: Subscription | null = null;
  private objectUrl: string | null = null;
  private destroyed = false;

  ngAfterViewInit(): void {
    this.dialog().nativeElement.showModal();
    const unit = this.secretaryUnitCode();
    const personalUnit = this.secretaryPersonalDraftUnitCode();
    const sentUnit = this.secretaryPersonalSentUnitCode();
    const managerUnit = this.secretaryManagerPendingUnitCode();
    const managerRound = this.managerRoundRequest();
    const secretaryRound = this.secretaryRoundRequest();
    this.request = (managerRound
      ? this.roundApi.managerPreview(managerRound.unitCode, this.technicalId(), managerRound.requestId, this.document().nodeId)
      : secretaryRound
      ? this.roundApi.secretaryPreview(secretaryRound.unitCode, this.technicalId(), secretaryRound.requestId, this.document().nodeId)
      : managerUnit
      ? this.secretaryApi.preview(managerUnit, this.technicalId(), this.document().nodeId, 'pending')
      : personalUnit
      ? this.api.getSecretaryPersonalDraftDocument(personalUnit, this.technicalId(), this.document().nodeId)
      : sentUnit ? this.api.getSecretarySentDocument(sentUnit, this.technicalId(), this.document().nodeId)
      : unit ? this.api.getSecretaryInboxDocument(unit, this.technicalId(), this.document().nodeId)
      : this.api.getDocumentContent(this.technicalId(), this.document().nodeId)
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
