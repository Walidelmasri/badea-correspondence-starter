import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  HostListener,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';

import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';

import {
  takeUntilDestroyed,
  toSignal,
} from '@angular/core/rxjs-interop';

import {
  catchError,
  forkJoin,
  firstValueFrom,
  map,
  of,
  Subscription,
  switchMap,
} from 'rxjs';

import {
  CorrespondenceService,
} from '../../../core/correspondence/correspondence.service';

import {
  CorrespondenceApiService,
  CorrespondenceDetails,
} from '../../../shared/data-access/correspondence-api.service';

import {
  TranslationService,
} from '../../../core/i18n/translation.service';

import {
  PdfWorkspaceComponent,
} from '../../../shared/ui/pdf-workspace/pdf-workspace.component';

import {
  CorrespondenceSummaryComponent,
  CorrespondenceSummaryViewModel,
} from '../../../shared/ui/correspondence-summary/correspondence-summary.component';

import {
  createEmptyPresidentExplanationInkDocument,
  PresidentExplanationInkDocument,
} from '../domain/president-explanation.model';

import {
  PresidentReviewRecipientOption,
  toPresidentReviewRecipients,
} from '../domain/president-review-recipient.model';

import {
  createEmptyPresidentReviewDraftState,
  normalizePresidentReviewDraftState,
  PresidentReviewDraftState,
  PresidentReviewExplanationMode,
  PresidentReviewPdfInkDocument,
  PresidentReviewRequiredActionCode,
} from '../domain/president-review-state.model';

import { PresidentSignatureSettingsComponent } from './president-signature-settings/president-signature-settings.component';

import {
  PresidentExplanationInkComponent,
} from './president-explanation-ink/president-explanation-ink.component';

interface PresidentReviewAttachment {
  readonly id: string;
  readonly filename: string;
  readonly nameAr: string;
  readonly nameEn: string;
  readonly sizeLabel: string;
  readonly src: string;
}

interface RequiredActionOption {
  readonly code: PresidentReviewRequiredActionCode;
  readonly labelAr: string;
  readonly labelEn: string;
}

@Component({
  selector: 'app-president-review-page',
  standalone: true,
  imports: [
    PdfWorkspaceComponent,
    PresidentExplanationInkComponent,
    PresidentSignatureSettingsComponent,
    CorrespondenceSummaryComponent,
  ],
  templateUrl: './president-review-page.component.html',
  styleUrl: './president-review-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PresidentReviewPageComponent {
  readonly translation = inject(TranslationService);

  private readonly correspondenceService =
    inject(CorrespondenceService);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(CorrespondenceApiService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly pdfWorkspace =
    viewChild(PdfWorkspaceComponent);

  /**
   * Null means the existing standalone demonstration route.
   */
  readonly technicalId = signal<string | null>(null);

  /** Only the real route uses Alfresco data; the standalone iPad demo is unchanged. */
  readonly correspondenceDetails = signal<CorrespondenceDetails | null>(null);
  readonly correspondenceLoaded = signal(true);
  readonly correspondenceLoadError = signal(false);
  readonly handoffInProgress = signal(false);
  readonly handoffUncertain = signal(false);
  readonly handoffError = signal(false);

  private detailsRequest: Subscription | null = null;
  private readonly pdfObjectUrls = new Set<string>();

  readonly revision = signal(0);
  readonly reviewLoaded = signal(true);

  readonly reviewSaveStatus = signal<
    'idle' | 'loading' | 'saving' | 'saved' | 'error' | 'conflict'
  >('idle');

  private readonly pdfInkDocuments =
    signal<readonly PresidentReviewPdfInkDocument[]>([]);

  private pendingPdfInkRestore:
    readonly PresidentReviewPdfInkDocument[] | null = null;

  private loadRequest: Subscription | null = null;
  private saveRequest: Subscription | null = null;

  private saveTimer:
    ReturnType<typeof setTimeout> | null = null;

  private saveInFlight = false;

  private lastPersistedStateJson:
    string | null = null;

  readonly correspondence =
    computed<CorrespondenceSummaryViewModel>(() => {
      const arabic =
        this.translation.language() === 'ar';

      if (this.technicalId() !== null) {
        const details = this.correspondenceDetails();
        return {
          reference: details?.documentNumber || '',
          dateLabel: details?.documentDate || '',
          subject: details?.subject || '',
          senderName: details
            ? (arabic
              ? details.createdByNameArabic || details.createdByNameEnglish
              : details.createdByNameEnglish)
            : null,
          senderDepartment: details?.sourceText || details?.originDepartmentCode || null,
          message: details?.bodyText || null,
        };
      }

      return {
        reference: 'PR-2026-0184',
        dateLabel: '30 Sep 2026',
        subject: arabic
          ? 'طلب مراجعة المستندات المرفقة'
          : 'Review of attached documents',
        senderName: null,
        senderDepartment: arabic
          ? 'إدارة الاستراتيجية'
          : 'Strategy Department',
        message: arabic
          ? 'يرجى التكرم بالاطلاع على المراسلة والمستندات المرفقة والتوجيه بما ترونه مناسباً.'
          : 'Please review the attached correspondence and supporting documents and provide your direction.',
      };
    });

  private readonly demonstrationAttachments:
    readonly PresidentReviewAttachment[] = [
      {
        id: 'main-letter',
        filename: 'report-badea.pdf',
        nameAr: 'الخطاب الرئيسي',
        nameEn: 'Main Letter',
        sizeLabel: '5.7 MB',
        src: '/pdf/local/report-badea.pdf',
      },
      {
        id: 'annex-a',
        filename: 'annex-a.pdf',
        nameAr: 'المرفق أ',
        nameEn: 'Annex A',
        sizeLabel: '1.8 MB',
        src: '/pdf/local/report-badea.pdf',
      },
      {
        id: 'supporting-document',
        filename: 'supporting-document.pdf',
        nameAr: 'مستند داعم',
        nameEn: 'Supporting Document',
        sizeLabel: '2.4 MB',
        src: '/pdf/local/report-badea.pdf',
      },
    ];

  /**
   * The PDF's Alfresco node ID is its stable ink document key.
   * Never use filename, URL or array index: they can change.
   */
  private readonly liveAttachments = signal<readonly PresidentReviewAttachment[]>([]);

  readonly attachments = computed(() =>
    this.technicalId() === null
      ? this.demonstrationAttachments
      : this.liveAttachments(),
  );

  /**
   * Departments come from the backend Oracle directory.
   * No hardcoded department catalogue.
   */
  readonly recipients = toSignal(
    this.correspondenceService
      .getDepartments()
      .pipe(
        map((departments) =>
          toPresidentReviewRecipients(departments),
        ),
        catchError(() =>
          of(toPresidentReviewRecipients([])),
        ),
      ),
    {
      initialValue: toPresidentReviewRecipients([]),
    },
  );

  readonly actions:
    readonly RequiredActionOption[] = [
      {
        code: 'required-action',
        labelAr: 'لإجراء اللازم وفق النظام',
        labelEn: 'Take the required action',
      },
      {
        code: 'study-and-opinion',
        labelAr: 'للدراسة وإبداء الرأي',
        labelEn: 'Study and provide an opinion',
      },
      {
        code: 'review-and-advise',
        labelAr: 'للمراجعة والإفادة',
        labelEn: 'Review and advise',
      },
      {
        code: 'discuss-directly',
        labelAr: 'للتفاهم / هاتفياً / شخصياً',
        labelEn: 'Discuss directly',
      },
      {
        code: 'direct-response',
        labelAr: 'للإجابة المباشرة',
        labelEn: 'Respond directly',
      },
      {
        code: 'prepare-summary',
        labelAr: 'لإعداد خلاصة',
        labelEn: 'Prepare a summary',
      },
      {
        code: 'information-and-action',
        labelAr: 'للعلم وإجراء اللازم',
        labelEn: 'For information and necessary action',
      },
      {
        code: 'follow-up',
        labelAr: 'للمتابعة',
        labelEn: 'Follow up',
      },
      {
        code: 'information-and-file',
        labelAr: 'للعلم والحفظ',
        labelEn: 'For information and filing',
      },
      {
        code: 'act-per-phone-call',
        labelAr: 'للعمل حسب المكالمة الهاتفية',
        labelEn: 'Act according to the phone call',
      },
      {
        code: 'prepare-response',
        labelAr: 'لإعداد رد مناسب',
        labelEn: 'Prepare an appropriate response',
      },
      {
        code: 'apology',
        labelAr: 'للاعتذار',
        labelEn: 'Prepare an apology',
      },
      {
        code: 'return-later',
        labelAr: 'لإعادتها لي لاحقاً',
        labelEn: 'Return to me later',
      },
      {
        code: 'other',
        labelAr: 'أخرى',
        labelEn: 'Other',
      },
    ];

  readonly activeAttachmentId =
    signal<string | null>(this.demonstrationAttachments[0].id);

  readonly explanationMode =
    signal<PresidentReviewExplanationMode>('pen');

  readonly typedExplanation = signal('');

  readonly explanationInkDocument =
    signal<PresidentExplanationInkDocument>(
      createEmptyPresidentExplanationInkDocument(),
    );

  readonly selectedRecipientIds =
    signal<ReadonlySet<string>>(new Set());

  readonly selectedCopyRecipientIds =
    signal<ReadonlySet<string>>(new Set());

  readonly selectedActionCodes =
    signal<ReadonlySet<PresidentReviewRequiredActionCode>>(
      new Set(),
    );

  readonly showCopyTo = signal(false);
  readonly showAllActions = signal(false);

  readonly otherRecipient = signal('');
  readonly otherAction = signal('');

  readonly activeAttachment = computed(() => {
    return this.attachments().find(
      (attachment) => attachment.id === this.activeAttachmentId(),
    ) ?? this.attachments()[0] ?? null;
  });

  readonly visibleActions = computed(() => {
    return this.showAllActions()
      ? this.actions
      : this.actions.slice(0, 6);
  });

  readonly otherRecipientSelected = computed(() =>
    this.selectedRecipientIds().has('other'),
  );

  readonly otherActionSelected = computed(() =>
    this.selectedActionCodes().has('other'),
  );

  /**
   * The President may leave every action-sheet field blank.
   * Validation of completed directions belongs to later workflow stages.
   *
   * Loading and persistence errors are separate operational concerns.
   */
  readonly canHandoff = computed(() =>
    this.technicalId() !== null &&
    this.correspondenceLoaded() &&
    this.reviewLoaded() &&
    !this.handoffInProgress() &&
    !this.handoffUncertain() &&
    this.reviewSaveStatus() !== 'conflict' &&
    this.reviewSaveStatus() !== 'error',
  );

  constructor() {
    /**
     * The backend GET can finish before the PDF workspace exists.
     * Restore committed strokes when Angular creates the child.
     */
    effect(() => {
      const workspace = this.pdfWorkspace();
      const pending = this.pendingPdfInkRestore;

      if (!workspace || pending === null) {
        return;
      }

      this.pendingPdfInkRestore = null;

      for (const document of pending) {
        workspace.restoreInkStrokes(
          document.documentKey,
          document.strokes,
        );
      }
    });

    /**
     * Angular may reuse the component when the route parameter changes.
     */
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const nextId = params.get('technicalId');

        if (nextId === this.technicalId()) {
          return;
        }

        this.activateReview(nextId);
      });

    this.destroyRef.onDestroy(() => {
      this.clearSaveTimer();
      this.loadRequest?.unsubscribe();
      this.saveRequest?.unsubscribe();
      this.detailsRequest?.unsubscribe();
      this.releasePdfUrls();
    });
  }

  @HostListener('window:beforeunload', ['$event'])
  warnIfReviewHasUnsavedChanges(
    event: BeforeUnloadEvent,
  ): void {
    if (!this.technicalId() || !this.reviewLoaded()) {
      return;
    }

    const hasUnsavedChanges =
      this.saveInFlight ||
      this.saveTimer !== null ||
      this.lastPersistedStateJson !==
        this.serializeDraftState(this.currentDraftState());

    if (hasUnsavedChanges) {
      event.preventDefault();
      event.returnValue = '';
    }
  }

  selectAttachment(id: string): void {
    if (this.attachments().some((attachment) => attachment.id === id)) {
      this.activeAttachmentId.set(id);
    }
  }

  setExplanationMode(
    mode: PresidentReviewExplanationMode,
  ): void {
    this.explanationMode.set(mode);
    this.scheduleAutosave();
  }

  toggleRecipient(id: string): void {
    this.selectedRecipientIds.update((current) =>
      this.toggleSetValue(current, id),
    );

    this.scheduleAutosave();
  }

  toggleCopyRecipient(id: string): void {
    this.selectedCopyRecipientIds.update((current) =>
      this.toggleSetValue(current, id),
    );

    this.scheduleAutosave();
  }

  toggleAction(
    code: PresidentReviewRequiredActionCode,
  ): void {
    this.selectedActionCodes.update((current) =>
      this.toggleSetValue(current, code),
    );

    this.scheduleAutosave();
  }

  recipientSelected(id: string): boolean {
    return this.selectedRecipientIds().has(id);
  }

  copyRecipientSelected(id: string): boolean {
    return this.selectedCopyRecipientIds().has(id);
  }

  actionSelected(
    code: PresidentReviewRequiredActionCode,
  ): boolean {
    return this.selectedActionCodes().has(code);
  }

  recipientLabel(
    recipient: PresidentReviewRecipientOption,
  ): string {
    return this.translation.language() === 'ar'
      ? recipient.nameAr
      : recipient.nameEn;
  }

  actionLabel(
    action: RequiredActionOption,
  ): string {
    return this.translation.language() === 'ar'
      ? action.labelAr
      : action.labelEn;
  }

  attachmentLabel(
    attachment: PresidentReviewAttachment,
  ): string {
    return this.translation.language() === 'ar'
      ? attachment.nameAr
      : attachment.nameEn;
  }

  onExplanationInkDocumentChange(
    document: PresidentExplanationInkDocument,
  ): void {
    this.explanationInkDocument.set(document);
    this.scheduleAutosave();
  }

  onTypedExplanationInput(event: Event): void {
    const target = event.target;

    if (!(target instanceof HTMLTextAreaElement)) {
      return;
    }

    this.typedExplanation.set(target.value);
    this.scheduleAutosave();
  }

  onOtherRecipientInput(event: Event): void {
    const target = event.target;

    if (!(target instanceof HTMLInputElement)) {
      return;
    }

    this.otherRecipient.set(target.value);
    this.scheduleAutosave();
  }

  onOtherActionInput(event: Event): void {
    const target = event.target;

    if (!(target instanceof HTMLInputElement)) {
      return;
    }

    this.otherAction.set(target.value);
    this.scheduleAutosave();
  }

  onPdfInkStateChanged(): void {
    const workspace = this.pdfWorkspace();

    if (!workspace) {
      return;
    }

    /**
     * PDF annotations belong to individual attachment IDs.
     * Preserve the PDF page number inside each stroke.
     */
    const documents = this.attachments()
      .map(
        (attachment): PresidentReviewPdfInkDocument => ({
          documentKey: attachment.id,
          strokes: workspace.inkStrokes(attachment.id),
        }),
      )
      .filter((document) => document.strokes.length > 0);

    /**
     * Don't discard data associated with another stored document.
     */
    const activeKeys = new Set(
      this.attachments().map((item) => item.id),
    );

    const otherDocuments = this.pdfInkDocuments()
      .filter(
        (document) => !activeKeys.has(document.documentKey),
      );

    this.pdfInkDocuments.set([
      ...otherDocuments,
      ...documents,
    ]);

    this.scheduleAutosave();
  }

  retryLoad(): void {
    const technicalId = this.technicalId();

    if (!technicalId) {
      return;
    }

    if (!this.correspondenceLoaded()) {
      this.loadCorrespondence(technicalId);
    }
    if (!this.reviewLoaded()) {
      this.loadPresidentReview(technicalId);
    }
  }

  retrySave(): void {
    if (
      !this.reviewLoaded() ||
      this.reviewSaveStatus() === 'conflict'
    ) {
      return;
    }

    this.flushAutosave();
  }

  /**
   * Save and confirm the entire President review before transferring control.
   * Every action-sheet field remains optional.
   */
  async handoff(): Promise<void> {
    const id = this.technicalId();
    if (!id || !this.canHandoff()) {
      return;
    }

    const confirmation = this.translation.text().presidentReview.confirmReturn;
    if (!window.confirm(confirmation)) {
      return;
    }

    this.handoffInProgress.set(true);
    this.handoffError.set(false);
    this.clearSaveTimer();

    try {
      if (!await this.ensureReviewPersisted(id)) {
        this.handoffError.set(true);
        return;
      }

      const result = await firstValueFrom(
        this.correspondenceService.transition(id, {
          action: 'SEND_TO_OFFICE',
        }),
      );

      if (result.technicalId !== id || result.state !== 'WITH_PRESIDENT_OFFICE') {
        this.handoffUncertain.set(true);
        return;
      }
      await this.router.navigate(['/inbox']);
    } catch (error: unknown) {
      // A lost transition response may mean that the handoff committed.
      // Do not automatically repeat SEND_TO_OFFICE.
      this.handoffUncertain.set(
        error instanceof HttpErrorResponse &&
        (error.status === 0 || error.status >= 500),
      );
      this.handoffError.set(true);
    } finally {
      this.handoffInProgress.set(false);
    }
  }

  private async ensureReviewPersisted(id: string): Promise<boolean> {
    // Preserve the established optimistic revision/autosave machinery.
    if (!this.saveInFlight) {
      this.flushAutosave();
    }
    const deadline = Date.now() + 20000;

    while (Date.now() < deadline && id === this.technicalId()) {
      const status = this.reviewSaveStatus();
      if (status === 'conflict' || status === 'error') {
        return false;
      }

      if (!this.saveInFlight && this.saveTimer === null) {
        if (
          this.lastPersistedStateJson ===
          this.serializeDraftState(this.currentDraftState())
        ) {
          return true;
        }
        this.flushAutosave();
      }
      await new Promise<void>((resolve) => setTimeout(resolve, 80));
    }
    return false;
  }

  private activateReview(
    technicalId: string | null,
  ): void {
    this.clearSaveTimer();

    this.loadRequest?.unsubscribe();
    this.saveRequest?.unsubscribe();
    this.detailsRequest?.unsubscribe();
    this.releasePdfUrls();

    this.correspondenceDetails.set(null);
    this.liveAttachments.set([]);
    this.correspondenceLoaded.set(technicalId === null);
    this.correspondenceLoadError.set(false);
    this.handoffError.set(false);
    this.handoffInProgress.set(false);
    this.handoffUncertain.set(false);
    this.activeAttachmentId.set(
      technicalId === null ? this.demonstrationAttachments[0].id : null,
    );

    this.saveInFlight = false;
    this.lastPersistedStateJson = null;

    this.technicalId.set(technicalId);
    this.revision.set(0);

    this.reviewLoaded.set(technicalId === null);

    this.reviewSaveStatus.set(
      technicalId === null ? 'idle' : 'loading',
    );

    /**
     * Reset each correspondence before loading its own state.
     */
    this.applyDraftState(
      createEmptyPresidentReviewDraftState(),
    );

    if (technicalId !== null) {
      this.loadCorrespondence(technicalId);
      this.loadPresidentReview(technicalId);
    }
  }

  /**
   * For real correspondence, fail closed until authenticated PDFs arrive.
   * Keep object URLs private; never put authentication tickets into PDF URLs.
   */
  private loadCorrespondence(technicalId: string): void {
    this.detailsRequest?.unsubscribe();
    this.correspondenceLoaded.set(false);
    this.correspondenceLoadError.set(false);

    this.detailsRequest = this.api.getDetails(technicalId).pipe(
      switchMap((details) => {
        if (
          details.technicalId !== technicalId ||
          details.state !== 'WITH_PRESIDENT'
        ) {
          throw new Error('Correspondence is not assigned to the President.');
        }
        const pdfs = details.documents;
        if (pdfs.some((document) => document.mimeType !== 'application/pdf')) {
          throw new Error('Unsupported correspondence attachment.');
        }
        return pdfs.length === 0
          ? of({ details, pdfs: [] as Blob[] })
          : forkJoin(
              pdfs.map((document) => this.api.getDocumentContent(
                technicalId, document.nodeId,
              )),
            ).pipe(map((blobs) => ({ details, pdfs: blobs })));
      }),
    ).subscribe({
      next: ({ details, pdfs }) => {
        if (this.technicalId() !== technicalId) {
          return;
        }
        if (pdfs.some((pdf) => pdf.size === 0 || pdf.type !== 'application/pdf')) {
          this.correspondenceLoadError.set(true);
          return;
        }
        const attachments: PresidentReviewAttachment[] = [];
        try {
          for (let index = 0; index < details.documents.length; index++) {
            const document = details.documents[index];
            const src = URL.createObjectURL(pdfs[index]);
            this.pdfObjectUrls.add(src);
            attachments.push({
              id: document.nodeId,
              filename: document.filename,
              nameAr: document.filename,
              nameEn: document.filename,
              sizeLabel: `${(document.sizeBytes / (1024 * 1024)).toFixed(2)} MiB`,
              src,
            });
          }
        } catch {
          this.releasePdfUrls();
          this.correspondenceLoadError.set(true);
          return;
        }
        this.correspondenceDetails.set(details);
        this.liveAttachments.set(attachments);
        this.activeAttachmentId.set(attachments[0]?.id ?? null);
        this.correspondenceLoaded.set(true);
        // The review snapshot may have loaded before real attachment IDs.
        this.restorePdfInkDocuments(this.pdfInkDocuments());
      },
      error: () => {
        if (this.technicalId() === technicalId) {
          this.correspondenceLoadError.set(true);
        }
      },
    });
  }

  private releasePdfUrls(): void {
    for (const url of this.pdfObjectUrls) {
      URL.revokeObjectURL(url);
    }
    this.pdfObjectUrls.clear();
  }

  private loadPresidentReview(
    technicalId: string,
  ): void {
    this.loadRequest?.unsubscribe();

    this.reviewLoaded.set(false);
    this.reviewSaveStatus.set('loading');

    this.loadRequest = this.correspondenceService
      .getPresidentReview<PresidentReviewDraftState>(
        technicalId,
      )
      .subscribe({
        next: (snapshot) => {
          if (technicalId !== this.technicalId()) {
            return;
          }

          if (
            snapshot.technicalId !== technicalId ||
            !Number.isSafeInteger(snapshot.revision) ||
            snapshot.revision < 0
          ) {
            this.reviewSaveStatus.set('error');
            return;
          }

          const state =
            normalizePresidentReviewDraftState(
              snapshot.state,
            );

          this.revision.set(snapshot.revision);

          this.applyDraftState(state);

          this.lastPersistedStateJson =
            this.serializeDraftState(state);

          this.reviewLoaded.set(true);
          this.reviewSaveStatus.set('idle');
        },

        error: () => {
          if (technicalId === this.technicalId()) {
            this.reviewSaveStatus.set('error');
          }
        },
      });
  }

  private applyDraftState(
    state: PresidentReviewDraftState,
  ): void {
    this.selectedRecipientIds.set(
      new Set(state.selectedRecipientIds),
    );

    this.selectedCopyRecipientIds.set(
      new Set(state.selectedCopyRecipientIds),
    );

    this.selectedActionCodes.set(
      new Set(state.selectedActionCodes),
    );

    this.otherRecipient.set(state.otherRecipient);
    this.otherAction.set(state.otherAction);

    this.explanationMode.set(state.explanationMode);
    this.typedExplanation.set(state.typedExplanation);

    this.explanationInkDocument.set(
      state.explanationInkDocument,
    );

    this.pdfInkDocuments.set(
      state.pdfInkDocuments,
    );

    /**
     * Reveal persisted choices even if they were
     * hidden behind expandable sections.
     */
    this.showCopyTo.set(
      state.selectedCopyRecipientIds.length > 0,
    );

    this.showAllActions.set(
      state.selectedActionCodes.some(
        (code) =>
          !this.actions.slice(0, 6).some(
            (action) => action.code === code,
          ),
      ),
    );

    this.restorePdfInkDocuments(
      state.pdfInkDocuments,
    );
  }

  private restorePdfInkDocuments(
    documents: readonly PresidentReviewPdfInkDocument[],
  ): void {
    const byKey = new Map(
      documents.map(
        (document) => [
          document.documentKey,
          document.strokes,
        ] as const,
      ),
    );

    /**
     * Explicitly restore empty lists too, so an
     * old correspondence's ink cannot leak into another.
     */
    const complete = this.attachments().map(
      (
        attachment,
      ): PresidentReviewPdfInkDocument => ({
        documentKey: attachment.id,
        strokes: byKey.get(attachment.id) ?? [],
      }),
    );

    const workspace = this.pdfWorkspace();

    if (!workspace) {
      this.pendingPdfInkRestore = complete;
      return;
    }

    this.pendingPdfInkRestore = null;

    for (const document of complete) {
      workspace.restoreInkStrokes(
        document.documentKey,
        document.strokes,
      );
    }
  }

  private currentDraftState():
    PresidentReviewDraftState {
    return {
      schemaVersion: 1,
      selectedRecipientIds: [
        ...this.selectedRecipientIds(),
      ],
      selectedCopyRecipientIds: [
        ...this.selectedCopyRecipientIds(),
      ],
      selectedActionCodes: [
        ...this.selectedActionCodes(),
      ],
      otherRecipient: this.otherRecipient(),
      otherAction: this.otherAction(),
      explanationMode: this.explanationMode(),
      typedExplanation: this.typedExplanation(),
      explanationInkDocument: this.explanationInkDocument(),
      pdfInkDocuments: this.pdfInkDocuments(),
    };
  }

  private scheduleAutosave(
    delayMilliseconds = 600,
  ): void {
    if (
      !this.technicalId() ||
      !this.reviewLoaded() ||
      this.reviewSaveStatus() === 'conflict'
    ) {
      return;
    }

    this.clearSaveTimer();

    if (!this.saveInFlight) {
      this.reviewSaveStatus.set('idle');
    }

    this.saveTimer = setTimeout(() => {
      this.saveTimer = null;
      this.flushAutosave();
    }, delayMilliseconds);
  }

  private flushAutosave(): void {
    const technicalId = this.technicalId();

    if (
      !technicalId ||
      !this.reviewLoaded() ||
      this.reviewSaveStatus() === 'conflict'
    ) {
      return;
    }

    const state = this.currentDraftState();

    const serialized =
      this.serializeDraftState(state);

    if (
      serialized === this.lastPersistedStateJson &&
      !this.saveInFlight
    ) {
      this.reviewSaveStatus.set('saved');
      return;
    }

    if (this.saveInFlight) {
      return;
    }

    this.saveInFlight = true;
    this.reviewSaveStatus.set('saving');

    this.saveRequest =
      this.correspondenceService
        .savePresidentReview(
          technicalId,
          {
            expectedRevision: this.revision(),
            state,
          },
        )
        .subscribe({
          next: (snapshot) => {
            if (technicalId !== this.technicalId()) {
              return;
            }

            this.saveInFlight = false;

            if (
              snapshot.technicalId !== technicalId ||
              !Number.isSafeInteger(snapshot.revision) ||
              snapshot.revision <= this.revision()
            ) {
              this.reviewSaveStatus.set('error');
              return;
            }

            this.revision.set(snapshot.revision);

            this.lastPersistedStateJson =
              serialized;

            const latest =
              this.serializeDraftState(
                this.currentDraftState(),
              );

            if (latest !== serialized) {
              this.scheduleAutosave(0);
            } else {
              this.reviewSaveStatus.set('saved');
            }
          },

          error: (error: unknown) => {
            if (technicalId !== this.technicalId()) {
              return;
            }

            this.saveInFlight = false;

            this.reviewSaveStatus.set(
              error instanceof HttpErrorResponse &&
              error.status === 409
                ? 'conflict'
                : 'error',
            );
          },
        });
  }

  private clearSaveTimer(): void {
    if (this.saveTimer !== null) {
      clearTimeout(this.saveTimer);
      this.saveTimer = null;
    }
  }

  private serializeDraftState(
    state: PresidentReviewDraftState,
  ): string {
    return JSON.stringify(state);
  }

  private toggleSetValue<T>(
    current: ReadonlySet<T>,
    value: T,
  ): ReadonlySet<T> {
    const next = new Set(current);

    if (next.has(value)) {
      next.delete(value);
    } else {
      next.add(value);
    }

    return next;
  }
}