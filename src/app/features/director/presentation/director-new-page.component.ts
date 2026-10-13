import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';

import {
  HttpErrorResponse,
} from '@angular/common/http';

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
} from '../../../shared/data-access/correspondence-api.service';

type UploadStatus =
  | 'queued'
  | 'uploading'
  | 'uploaded'
  | 'failed'
  | 'verify';

interface SelectedDocument {
  readonly id: number;
  readonly file: File;
  readonly status: UploadStatus;
  readonly nodeId?: string;
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
    saved: 'Draft saved successfully in Alfresco.',
    subjectRequired: 'Please enter a subject.',
    invalidFile: 'Only PDF files up to 25 MiB are supported.',
    createFailed: 'The draft could not be created. Please try again.',
    createUncertain:
      'The server response was interrupted. The draft may have been created. Do not retry until its status is verified.',
    uploadFailed:
      'Some documents could not be uploaded. The correspondence remains a draft.',
    uploadUncertain:
      'An upload result could not be confirmed. Verify the document before trying again.',
    savedReference: 'Correspondence ID',
    locked:
      'Metadata is saved. Editing existing draft details will be enabled in the next development step.',
    pending: 'Queued',
    uploading: 'Uploading',
    uploaded: 'Uploaded',
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
    saved: 'تم حفظ المسودة بنجاح في نظام ألفريسكو.',
    subjectRequired: 'يرجى إدخال موضوع المراسلة.',
    invalidFile: 'يُسمح بملفات PDF فقط وبحد أقصى 25 ميبيبايت.',
    createFailed: 'تعذر إنشاء المسودة. يرجى المحاولة مرة أخرى.',
    createUncertain:
      'انقطع الاتصال قبل تأكيد النتيجة. ربما تم إنشاء المسودة. يرجى التحقق قبل إعادة المحاولة.',
    uploadFailed:
      'تعذر رفع بعض المستندات. ستبقى المراسلة في حالة مسودة.',
    uploadUncertain:
      'تعذر تأكيد نتيجة رفع أحد المستندات. يرجى التحقق قبل إعادة المحاولة.',
    savedReference: 'المعرف الفني للمراسلة',
    locked:
      'تم حفظ البيانات. ستتاح إمكانية تعديل بيانات المسودة في المرحلة التالية.',
    pending: 'بانتظار الرفع',
    uploading: 'جارٍ الرفع',
    uploaded: 'تم الرفع',
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
export class DirectorNewPageComponent {
  private readonly api =
    inject(CorrespondenceApiService);

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

  readonly documents =
    signal<readonly SelectedDocument[]>([]);

  readonly busy = signal(false);

  readonly creationUncertain =
    signal(false);

  readonly notice =
    signal<PageNotice | null>(null);

  private nextDocumentId = 0;

  onFilesSelected(event: Event): void {
    const input =
      event.target as HTMLInputElement;

    const selected =
      Array.from(input.files ?? []);

    // Allows selecting the same file again later.
    input.value = '';

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
        (current) => [...current, ...additions],
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
    if (this.busy()) {
      return;
    }

    // Uploaded documents are permanent until we
    // implement an authorized delete endpoint.
    this.documents.update(
      (current) =>
        current.filter(
          (document) =>
            document.id !== id ||
            document.status === 'uploaded' ||
            document.status === 'verify',
        ),
    );
  }

  retryDocument(id: number): void {
    if (this.busy()) {
      return;
    }

    this.setDocumentStatus(id, 'queued');
    void this.saveDraft();
  }

  async saveDraft(): Promise<void> {
    if (this.busy() || this.creationUncertain()) {
      return;
    }

    if (!this.technicalId() && this.form.invalid) {
      this.form.markAllAsTouched();

      this.notice.set({
        type: 'error',
        message: this.labels().subjectRequired,
      });

      return;
    }

    this.busy.set(true);
    this.notice.set(null);

    try {
      let technicalId = this.technicalId();

      if (!technicalId) {
        const values = this.form.getRawValue();

        const created = await firstValueFrom(
          this.api.createDraft({
            subject: values.subject.trim(),
            documentNumber:
              this.optional(values.documentNumber),
            documentDate:
              this.optional(values.documentDate),
            sourceText:
              this.optional(values.sourceText),
            bodyText:
              this.optional(values.bodyText),
          }),
        );

        technicalId = created.technicalId;

        // Store the ID immediately, before uploading
        // anything. Subsequent saves reuse this draft.
        this.technicalId.set(technicalId);

        // Metadata updates are not implemented yet.
        // Don't allow edits we cannot persist.
        this.form.disable({
          emitEvent: false,
        });
      }

      let failed = false;
      let uncertain = false;

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

          this.documents.update(
            (current) =>
              current.map(
                (item) =>
                  item.id === document.id
                    ? {
                        ...item,
                        status: 'uploaded' as const,
                        nodeId: uploaded.nodeId,
                      }
                    : item,
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
            resultUncertain ? 'verify' : 'failed',
          );

          failed = true;
          uncertain ||= resultUncertain;
        }
      }

      this.notice.set({
        type: failed
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

      if (!this.technicalId() && resultUncertain) {
        // Creating again might duplicate correspondence
        // if Alfresco committed before the response failed.
        this.creationUncertain.set(true);
      }

      this.notice.set({
        type: resultUncertain
          ? 'warning'
          : 'error',
        message: resultUncertain
          ? this.labels().createUncertain
          : this.labels().createFailed,
      });
    } finally {
      this.busy.set(false);
    }
  }

  documentStatus(
    status: UploadStatus,
  ): string {
    return this.labels()[status === 'queued'
      ? 'pending'
      : status];
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
              ? { ...document, status }
              : document,
        ),
    );
  }
}