import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';

import { HttpErrorResponse } from '@angular/common/http';

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

import { firstValueFrom } from 'rxjs';

import {
  AuthSessionStore,
} from '../../../core/auth/auth-session.store';

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

@Component({
  selector: 'app-director-new-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './director-new-page.component.html',
  styleUrls: [
    '../../compose/presentation/compose-page.component.scss',
    './director-new-page.component.scss',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectorNewPageComponent implements OnInit {
  private readonly api = inject(CorrespondenceApiService);
  private readonly session = inject(AuthSessionStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly translation = inject(TranslationService);

  readonly labels = computed(
    () => this.translation.text().director,
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

  readonly technicalId = signal<string | null>(null);

  /**
   * Files selected in the browser but not yet confirmed
   * as persisted in Alfresco.
   */
  readonly documents =
    signal<readonly SelectedDocument[]>([]);

  /**
   * Actual Alfresco documents.
   *
   * These are metadata records, not browser File objects.
   */
  readonly savedDocuments =
    signal<readonly CorrespondenceDocumentUpload[]>([]);

  readonly readOnly = signal(false);
  readonly busy = signal(false);
  readonly loading = signal(false);

  readonly creationUncertain = signal(false);

  /**
   * Only one saved-attachment deletion at a time.
   */
  readonly deletingDocumentId = signal<string | null>(null);

  /**
   * Blocks further modifications if Alfresco's final
   * deletion state cannot be verified.
   */
  readonly deletionUncertain = signal(false);

  readonly notice = signal<PageNotice | null>(null);

  private nextDocumentId = 0;

  ngOnInit(): void {
    const draftId =
      this.route.snapshot.queryParamMap.get('draft');

    if (draftId) {
      // Existing drafts must retain their saved Source.
      void this.loadDraft(draftId);
      return;
    }

    this.prefillDepartmentSource();
  }

  /**
   * Set the default Source for new correspondence.
   *
   * The authenticated employee's department is already
   * loaded by AuthService and stored in AuthSessionStore.
   */
  private prefillDepartmentSource(): void {
    const department =
      this.session.currentUser()
        ?.employeeProfile
        ?.department;

    if (!department) {
      return;
    }

    const source =
      this.translation.language() === 'ar'
        ? (
            department.nameArabic?.trim() ||
            department.nameEnglish.trim()
          )
        : (
            department.nameEnglish.trim() ||
            department.nameArabic?.trim() ||
            ''
          );

    const control = this.form.controls.sourceText;

    if (
      source &&
      control.pristine &&
      !control.value.trim()
    ) {
      control.setValue(source);
    }
  }

  /**
   * Load an existing correspondence draft.
   *
   * Access is authorized by the backend, not by Angular.
   */
  private async loadDraft(
    technicalId: string,
  ): Promise<void> {
    this.busy.set(true);
    this.loading.set(true);
    this.readOnly.set(true);
    this.notice.set(null);

    this.form.disable({ emitEvent: false });

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

      this.savedDocuments.set(details.documents);

      if (details.state !== 'DRAFT') {
        this.notice.set({
          type: 'warning',
          message: this.labels().notEditable,
        });

        return;
      }

      this.readOnly.set(false);
      this.form.enable({ emitEvent: false });

    } catch {
      // A failed GET must not enable editing.
      this.notice.set({
        type: 'error',
        message: this.labels().loadFailed,
      });

    } finally {
      this.loading.set(false);
      this.busy.set(false);
    }
  }

  /**
   * Add PDF files to the local upload queue.
   *
   * Nothing is sent to Alfresco until Save Draft runs.
   */
  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const selected = Array.from(input.files ?? []);

    // Allow reselecting the same file later.
    input.value = '';

    if (this.modificationsBlocked()) {
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

  /**
   * Remove a queued or definitively failed upload.
   *
   * This works before any correspondence has been saved.
   * No backend request is necessary.
   */
  removeDocument(id: number): void {
    if (this.modificationsBlocked()) {
      return;
    }

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

  /**
   * Delete an attachment already stored in Alfresco.
   *
   * Unlike removeDocument(), this requires a DELETE request
   * and a persisted correspondence technical ID.
   */
  async removeSavedDocument(
    document: CorrespondenceDocumentUpload,
  ): Promise<void> {
    const technicalId = this.technicalId();

    if (!technicalId || this.modificationsBlocked()) {
      return;
    }

    const confirmed = window.confirm(
      `${this.labels().confirmDelete}\n${document.filename}`,
    );

    if (!confirmed) {
      return;
    }

    this.deletingDocumentId.set(document.nodeId);
    this.notice.set(null);

    try {
      await firstValueFrom(
        this.api.deleteDocument(
          technicalId,
          document.nodeId,
        ),
      );

      // Remove from the UI only after Alfresco confirms.
      this.savedDocuments.update(
        (current) =>
          current.filter(
            (item) => item.nodeId !== document.nodeId,
          ),
      );

      this.notice.set({
        type: 'success',
        message: this.labels().deleteSucceeded,
      });

    } catch (error: unknown) {
      const needsVerification =
        error instanceof HttpErrorResponse &&
        (
          error.status === 0 ||
          error.status === 404 ||
          error.status >= 500
        );

      if (!needsVerification) {
        // Definitive 400/403 responses are not retried.
        this.notice.set({
          type: 'error',
          message: this.labels().deleteFailed,
        });

        return;
      }

      /*
       * The DELETE may have committed before the response
       * was interrupted. Read the actual attachment list.
       *
       * Do not issue a second DELETE automatically.
       */
      try {
        const details = await firstValueFrom(
          this.api.getDetails(technicalId),
        );

        this.savedDocuments.set(details.documents);

        if (details.state !== 'DRAFT') {
          this.readOnly.set(true);
          this.form.disable({ emitEvent: false });

          this.notice.set({
            type: 'warning',
            message: this.labels().notEditable,
          });

          return;
        }

        const stillExists = details.documents.some(
          (item) => item.nodeId === document.nodeId,
        );

        this.notice.set({
          type: stillExists ? 'error' : 'success',
          message: stillExists
            ? this.labels().deleteFailed
            : this.labels().deleteSucceeded,
        });

      } catch {
        // State cannot be verified. Require a refresh.
        this.deletionUncertain.set(true);

        this.notice.set({
          type: 'warning',
          message: this.labels().deleteUncertain,
        });
      }

    } finally {
      this.deletingDocumentId.set(null);
    }
  }

  /**
   * Retry only definitively failed uploads.
   *
   * Uploads marked "verify" are not automatically
   * retried because doing so could create duplicates.
   */
  retryDocument(id: number): void {
    if (this.modificationsBlocked()) {
      return;
    }

    const document = this.documents().find(
      (item) => item.id === id,
    );

    if (document?.status !== 'failed') {
      return;
    }

    this.setDocumentStatus(id, 'queued');

    void this.saveDraft();
  }

  /**
   * Create or update the draft, then upload queued PDFs.
   *
   * This method does not transition correspondence to
   * the President's Office.
   */
  async saveDraft(): Promise<void> {
    if (this.modificationsBlocked()) {
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

    const wasCreating = this.technicalId() === null;

    this.busy.set(true);
    this.notice.set(null);

    /*
     * Disable edits while the save/upload operation runs.
     * getRawValue() still includes disabled form controls.
     */
    this.form.disable({ emitEvent: false });

    try {
      let technicalId = this.technicalId();

      const values = this.form.getRawValue();

      const metadata: CreateCorrespondenceDraft = {
        subject: values.subject.trim(),
        documentNumber: this.optional(values.documentNumber),
        documentDate: this.optional(values.documentDate),
        sourceText: this.optional(values.sourceText),
        bodyText: this.optional(values.bodyText),
      };

      if (!technicalId) {
        const created = await firstValueFrom(
          this.api.createDraft(metadata),
        );

        technicalId = created.technicalId;

        // Preserve the ID before uploading anything.
        this.technicalId.set(technicalId);
        this.form.markAsPristine();

        // Keep this component and pending files alive.
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { draft: technicalId },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        });

      } else if (this.form.dirty) {
        await firstValueFrom(
          this.api.updateDraft(technicalId, metadata),
        );

        this.form.markAsPristine();
      }

      /*
       * Upload queued files sequentially.
       * Failure of one file does not create another draft.
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

          this.savedDocuments.update(
            (current) => [...current, uploaded],
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
            resultUncertain ? 'verify' : 'failed',
          );
        }
      }

      const failed = this.documents().some(
        (document) => document.status === 'failed',
      );

      const uncertain = this.documents().some(
        (document) => document.status === 'verify',
      );

      this.notice.set({
        type: failed || uncertain ? 'warning' : 'success',
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
         * Creating again might duplicate correspondence
         * if the initial request already committed.
         */
        this.creationUncertain.set(true);
      }

      this.notice.set({
        type: resultUncertain ? 'warning' : 'error',
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
        !this.creationUncertain() &&
        !this.deletionUncertain()
      ) {
        this.form.enable({ emitEvent: false });
      }

      this.busy.set(false);
    }
  }

  documentStatus(status: UploadStatus): string {
    return this.labels()[
      status === 'queued' ? 'pending' : status
    ];
  }

  formatSize(bytes: number): string {
    return `${(bytes / (1024 * 1024)).toFixed(2)} MiB`;
  }

  private modificationsBlocked(): boolean {
    return (
      this.busy() ||
      this.readOnly() ||
      this.creationUncertain() ||
      this.deletingDocumentId() !== null ||
      this.deletionUncertain()
    );
  }

  private optional(value: string): string | null {
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