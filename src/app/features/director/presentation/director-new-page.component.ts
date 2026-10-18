import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';

import {
  HttpErrorResponse,
} from '@angular/common/http';

import {
  ActivatedRoute,
  Router,
} from '@angular/router';

import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import {
  firstValueFrom,
} from 'rxjs';

import {
  TranslationService,
} from '../../../core/i18n/translation.service';

import {
  CorrespondenceApiService,
  CorrespondenceDocumentUpload,
  CreateCorrespondenceDraft,
} from '../../../shared/data-access/correspondence-api.service';

type UploadStatus =
  | 'queued'
  | 'uploading'
  | 'failed'
  | 'verify';

interface SelectedDocument {
  readonly id: number;
  readonly file: File;
  readonly status: UploadStatus;
}

interface PageNotice {
  readonly type: 'success' | 'error' | 'warning';
  readonly message: string;
}

const MAX_PDF_BYTES = 25 * 1024 * 1024;

const COPY = {
  en: {
    eyebrow: 'DIRECTOR WORKSPACE',
    title: 'New Correspondence',
    editing: 'Edit Correspondence Draft',

    description:
      'Prepare correspondence and supporting documents for the President’s Office.',

    details: 'Correspondence Details',
    subject: 'Subject',
    documentNumber: 'Document Number',
    documentDate: 'Document Date',
    source: 'Source',
    body: 'Description',

    attachments: 'Supporting Documents',
    chooseFiles: 'Choose PDF Documents',
    fileHint: 'PDF only · Maximum 25 MiB per document',

    save: 'Save Draft & Upload',
    saving: 'Saving…',
    loading: 'Loading correspondence…',

    saved: 'Draft saved successfully in Alfresco.',

    subjectRequired: 'Please enter a subject.',

    invalidFile:
      'Only PDF files up to 25 MiB are supported.',

    createFailed:
      'The draft could not be created. Please try again.',

    createUncertain:
      'The server response was interrupted. The draft may have been created. Do not retry until its status is verified.',

    loadFailed:
      'Unable to load this correspondence. Please try again.',

    notEditable:
      'This correspondence is no longer a draft and cannot be edited here.',

    updateFailed:
      'The draft could not be updated. Please try again.',

    updateUncertain:
      'The save result could not be confirmed. Refresh the draft to verify its latest values.',

    uploadFailed:
      'Some documents could not be uploaded. The correspondence remains a draft.',

    uploadUncertain:
      'An upload result could not be confirmed. Verify the document before trying again.',

    savedReference: 'Correspondence ID',
    savedDocument: 'Saved in Alfresco',

    locked:
      'You can continue editing this draft until it is submitted.',

    pending: 'Queued',
    uploading: 'Uploading',
    failed: 'Failed',
    verify: 'Needs verification',

    retry: 'Retry',
    remove: 'Remove',

    automaticDate:
      'The creation timestamp is recorded automatically by Alfresco.',

    destination: 'Destination: President’s Office',
  },

  ar: {
    eyebrow: 'مساحة عمل المدير',
    title: 'مراسلة جديدة',
    editing: 'تعديل مسودة مراسلة',

    description:
      'إعداد المراسلة والمستندات الداعمة لإرسالها إلى مكتب الرئيس.',

    details: 'بيانات المراسلة',
    subject: 'الموضوع',
    documentNumber: 'رقم المستند',
    documentDate: 'تاريخ المستند',
    source: 'المصدر',
    body: 'وصف المراسلة',

    attachments: 'المستندات المرفقة',
    chooseFiles: 'اختيار ملفات PDF',
    fileHint: 'PDF فقط · الحد الأقصى 25 ميبيبايت لكل ملف',

    save: 'حفظ المسودة ورفع الملفات',
    saving: 'جارٍ الحفظ…',
    loading: 'جارٍ تحميل المراسلة…',

    saved: 'تم حفظ المسودة بنجاح في نظام ألفريسكو.',

    subjectRequired: 'يرجى إدخال موضوع المراسلة.',

    invalidFile:
      'يُسمح بملفات PDF فقط وبحد أقصى 25 ميبيبايت.',

    createFailed:
      'تعذر إنشاء المسودة. يرجى المحاولة مرة أخرى.',

    createUncertain:
      'انقطع الاتصال قبل تأكيد النتيجة. ربما تم إنشاء المسودة. يرجى التحقق قبل إعادة المحاولة.',

    loadFailed:
      'تعذر تحميل المراسلة. يرجى المحاولة مرة أخرى.',

    notEditable:
      'لم تعد هذه المراسلة مسودة ولا يمكن تعديلها من هذه الصفحة.',

    updateFailed:
      'تعذر تحديث المسودة. يرجى المحاولة مرة أخرى.',

    updateUncertain:
      'تعذر تأكيد نتيجة الحفظ. يرجى تحديث الصفحة والتحقق من آخر البيانات المحفوظة.',

    uploadFailed:
      'تعذر رفع بعض المستندات. ستبقى المراسلة في حالة مسودة.',

    uploadUncertain:
      'تعذر تأكيد نتيجة رفع أحد المستندات. يرجى التحقق قبل إعادة المحاولة.',

    savedReference: 'المعرف الفني للمراسلة',
    savedDocument: 'محفوظ في ألفريسكو',

    locked:
      'يمكنك متابعة تعديل هذه المسودة حتى يتم إرسالها.',

    pending: 'بانتظار الرفع',
    uploading: 'جارٍ الرفع',
    failed: 'فشل الرفع',
    verify: 'بحاجة إلى التحقق',

    retry: 'إعادة المحاولة',
    remove: 'إزالة',

    automaticDate:
      'يتم تسجيل تاريخ ووقت إنشاء المراسلة تلقائياً في ألفريسكو.',

    destination: 'الوجهة: مكتب الرئيس',
  },
} as const;

@Component({
  selector: 'app-director-new-page',
  standalone: true,

  imports: [
    ReactiveFormsModule,
  ],

  templateUrl: './director-new-page.component.html',

  styleUrls: [
    '../../compose/presentation/compose-page.component.scss',
    './director-new-page.component.scss',
  ],

  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectorNewPageComponent implements OnInit {

  private readonly api =
    inject(CorrespondenceApiService);

  private readonly route =
    inject(ActivatedRoute);

  private readonly router =
    inject(Router);

  readonly translation =
    inject(TranslationService);

  readonly labels = computed(
    () => COPY[this.translation.language()],
  );

  readonly form = new FormGroup({
    subject: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),

    documentNumber: new FormControl('', {
      nonNullable: true,
    }),

    documentDate: new FormControl('', {
      nonNullable: true,
    }),

    sourceText: new FormControl('', {
      nonNullable: true,
    }),

    bodyText: new FormControl('', {
      nonNullable: true,
    }),
  });

  readonly technicalId =
    signal<string | null>(null);

  /**
   * Files selected in the browser but not yet
   * confirmed as stored in Alfresco.
   */
  readonly documents =
    signal<readonly SelectedDocument[]>([]);

  /**
   * Documents returned by Alfresco.
   *
   * These must remain separate from browser File
   * objects to avoid duplicate uploads.
   */
  readonly savedDocuments =
    signal<readonly CorrespondenceDocumentUpload[]>([]);

  readonly readOnly = signal(false);

  readonly busy = signal(false);

  readonly loading = signal(false);

  readonly creationUncertain = signal(false);

  readonly notice =
    signal<PageNotice | null>(null);

  private nextDocumentId = 0;

  ngOnInit(): void {
    const draftId =
      this.route.snapshot.queryParamMap.get('draft');

    if (draftId) {
      void this.loadDraft(draftId);
    }
  }

  /**
   * Loads a persisted correspondence draft.
   *
   * The backend decides whether the authenticated
   * Director may access this correspondence.
   */
  private async loadDraft(
    technicalId: string,
  ): Promise<void> {

    this.busy.set(true);
    this.loading.set(true);
    this.readOnly.set(true);

    this.notice.set(null);

    this.form.disable({
      emitEvent: false,
    });

    try {
      const details = await firstValueFrom(
        this.api.getDetails(technicalId),
      );

      this.technicalId.set(details.technicalId);

      this.form.patchValue({
        subject: details.subject,
        documentNumber: details.documentNumber ?? '',
        documentDate: details.documentDate ?? '',
        sourceText: details.sourceText ?? '',
        bodyText: details.bodyText ?? '',
      });

      this.form.markAsPristine();
      this.form.markAsUntouched();

      this.savedDocuments.set(
        details.documents,
      );

      if (details.state !== 'DRAFT') {
        this.notice.set({
          type: 'warning',
          message: this.labels().notEditable,
        });

        return;
      }

      this.readOnly.set(false);

      this.form.enable({
        emitEvent: false,
      });

    } catch {
      /*
       * Never enable editing after a failed retrieval.
       *
       * A failed GET does not prove that the
       * correspondence exists or is still a draft.
       */
      this.notice.set({
        type: 'error',
        message: this.labels().loadFailed,
      });

    } finally {
      this.loading.set(false);
      this.busy.set(false);
    }
  }

  onFilesSelected(event: Event): void {
    const input =
      event.target as HTMLInputElement;

    const selected =
      Array.from(input.files ?? []);

    input.value = '';

    if (
      this.busy() ||
      this.readOnly() ||
      this.creationUncertain()
    ) {
      return;
    }

    const additions: SelectedDocument[] = [];

    let rejected = false;

    for (const file of selected) {
      const validExtension =
        file.name.toLowerCase().endsWith('.pdf');

      const validSize =
        file.size > 0 &&
        file.size <= MAX_PDF_BYTES;

      if (!validExtension || !validSize) {
        rejected = true;
        continue;
      }

      const alreadySelected = [
        ...this.documents(),
        ...additions,
      ].some(
        (existing) =>
          existing.file.name === file.name &&
          existing.file.size === file.size,
      );

      if (alreadySelected) {
        continue;
      }

      additions.push({
        id: ++this.nextDocumentId,
        file,
        status: 'queued',
      });
    }

    if (additions.length > 0) {
      this.documents.update(
        (current) => [
          ...current,
          ...additions,
        ],
      );
    }

    if (rejected) {
      this.notice.set({
        type: 'error',
        message: this.labels().invalidFile,
      });
    }
  }

  removeDocument(id: number): void {
    if (
      this.busy() ||
      this.readOnly() ||
      this.creationUncertain()
    ) {
      return;
    }

    /*
     * Only unsaved or definitively failed uploads
     * may be removed locally.
     *
     * Saved Alfresco documents need an authorized
     * backend delete operation.
     */
    this.documents.update(
      (current) =>
        current.filter(
          (document) =>
            document.id !== id ||
            (
              document.status !== 'queued' &&
              document.status !== 'failed'
            ),
        ),
    );
  }

  retryDocument(id: number): void {
    if (
      this.busy() ||
      this.readOnly() ||
      this.creationUncertain()
    ) {
      return;
    }

    const document = this.documents().find(
      (item) => item.id === id,
    );

    if (document?.status !== 'failed') {
      return;
    }

    this.setDocumentStatus(
      id,
      'queued',
    );

    void this.saveDraft();
  }

  async saveDraft(): Promise<void> {
    if (
      this.busy() ||
      this.creationUncertain() ||
      this.readOnly()
    ) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();

      this.notice.set({
        type: 'error',
        message: this.labels().subjectRequired,
      });

      return;
    }

    const wasCreating =
      this.technicalId() === null;

    this.busy.set(true);
    this.notice.set(null);

    /*
     * Prevent form edits while a save or upload
     * is in progress.
     *
     * getRawValue() still returns disabled controls.
     */
    this.form.disable({
      emitEvent: false,
    });

    try {
      let technicalId =
        this.technicalId();

      const values =
        this.form.getRawValue();

      const metadata: CreateCorrespondenceDraft = {
        subject: values.subject.trim(),

        documentNumber:
          this.optional(values.documentNumber),

        documentDate:
          this.optional(values.documentDate),

        sourceText:
          this.optional(values.sourceText),

        bodyText:
          this.optional(values.bodyText),
      };

      if (!technicalId) {
        /*
         * Create once.
         *
         * Save the returned ID immediately so
         * subsequent uploads use this same draft.
         */
        const created = await firstValueFrom(
          this.api.createDraft(metadata),
        );

        technicalId = created.technicalId;

        this.technicalId.set(technicalId);

        this.form.markAsPristine();

        /*
         * Preserve the current component and
         * selected files while updating the URL.
         */
        void this.router.navigate([], {
          relativeTo: this.route,

          queryParams: {
            draft: technicalId,
          },

          queryParamsHandling: 'merge',
          replaceUrl: true,
        });

      } else if (this.form.dirty) {
        /*
         * Update existing metadata only when
         * the form has changed.
         */
        await firstValueFrom(
          this.api.updateDraft(
            technicalId,
            metadata,
          ),
        );

        this.form.markAsPristine();
      }

      /*
       * Upload outstanding files sequentially.
       *
       * A failed upload never creates a second
       * correspondence draft.
       */
      for (const document of this.documents()) {
        if (document.status !== 'queued') {
          continue;
        }

        this.setDocumentStatus(
          document.id,
          'uploading',
        );

        try {
          const uploaded = await firstValueFrom(
            this.api.uploadDocument(
              technicalId,
              document.file,
            ),
          );

          /*
           * Move the successful upload into
           * the persisted attachment collection.
           */
          this.savedDocuments.update(
            (current) => [
              ...current,
              uploaded,
            ],
          );

          this.documents.update(
            (current) =>
              current.filter(
                (item) => item.id !== document.id,
              ),
          );

        } catch (error: unknown) {
          const resultUncertain =
            error instanceof HttpErrorResponse &&
            (
              error.status === 0 ||
              error.status >= 500
            );

          this.setDocumentStatus(
            document.id,
            resultUncertain
              ? 'verify'
              : 'failed',
          );
        }
      }

      const failed =
        this.documents().some(
          (document) =>
            document.status === 'failed',
        );

      const uncertain =
        this.documents().some(
          (document) =>
            document.status === 'verify',
        );

      this.notice.set({
        type:
          failed || uncertain
            ? 'warning'
            : 'success',

        message: uncertain
          ? this.labels().uploadUncertain
          : failed
            ? this.labels().uploadFailed
            : this.labels().saved,
      });

    } catch (error: unknown) {
      const resultUncertain =
        error instanceof HttpErrorResponse &&
        (
          error.status === 0 ||
          error.status >= 500
        );

      if (wasCreating && resultUncertain) {
        /*
         * If creation committed but the response
         * was interrupted, retrying could create
         * duplicate correspondence.
         */
        this.creationUncertain.set(true);
      }

      this.notice.set({
        type: resultUncertain
          ? 'warning'
          : 'error',

        message: wasCreating
          ? resultUncertain
            ? this.labels().createUncertain
            : this.labels().createFailed
          : resultUncertain
            ? this.labels().updateUncertain
            : this.labels().updateFailed,
      });

    } finally {
      if (
        !this.readOnly() &&
        !this.creationUncertain()
      ) {
        this.form.enable({
          emitEvent: false,
        });
      }

      this.busy.set(false);
    }
  }

  documentStatus(
    status: UploadStatus,
  ): string {
    return this.labels()[
      status === 'queued'
        ? 'pending'
        : status
    ];
  }

  formatSize(bytes: number): string {
    return `${(bytes / (1024 * 1024)).toFixed(2)} MiB`;
  }

  private optional(
    value: string,
  ): string | null {
    return value.trim() || null;
  }

  private setDocumentStatus(
    id: number,
    status: UploadStatus,
  ): void {
    this.documents.update(
      (current) =>
        current.map(
          (document) =>
            document.id === id
              ? {
                  ...document,
                  status,
                }
              : document,
        ),
    );
  }
}