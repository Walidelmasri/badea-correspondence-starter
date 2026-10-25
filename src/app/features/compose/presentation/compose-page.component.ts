import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { TranslationService } from '../../../core/i18n/translation.service';
import {
  CorrespondenceApiService,
  CorrespondenceDocumentUpload,
  ExecutiveCorrespondenceDraft,
} from '../../../shared/data-access/correspondence-api.service';
import { CorrespondenceInstruction } from '../../inbox/domain/correspondence-instruction.model';
import { CORRESPONDENCE_INSTRUCTION_LABELS } from '../../inbox/presentation/correspondence-instruction.labels';

interface ComposeForm {
  recipient: FormControl<string>;
  subject: FormControl<string>;
  documentNumber: FormControl<string>;
  documentDate: FormControl<string>;
  entity: FormControl<string>;
  instruction: FormControl<CorrespondenceInstruction | null>;
  explanation: FormControl<string>;
  copyTo: FormControl<string>;
}

type UploadStatus = 'queued' | 'uploading' | 'failed' | 'verify';
interface QueuedPdf {
  readonly id: number;
  readonly file: File;
  readonly status: UploadStatus;
}
interface Notice {
  readonly kind: 'success' | 'warning' | 'error';
  readonly message: string;
}
const MAX_PDF_BYTES = 25 * 1024 * 1024;

@Component({
  selector: 'app-compose-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './compose-page.component.html',
  styleUrl: './compose-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComposePageComponent implements OnInit {
  private readonly api = inject(CorrespondenceApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly translation = inject(TranslationService);

  readonly instructionLabels = CORRESPONDENCE_INSTRUCTION_LABELS;
  readonly instructions: readonly CorrespondenceInstruction[] = [
    'required-action', 'study-and-opinion', 'review-and-advise',
    'discuss-directly', 'direct-response', 'prepare-summary',
    'information-and-action', 'follow-up', 'information-and-file',
    'act-per-phone-call', 'prepare-response', 'apology', 'return-later',
  ];

  readonly form = new FormGroup<ComposeForm>({
    recipient: new FormControl('', {
      nonNullable: true, validators: [Validators.required],
    }),
    subject: new FormControl('', {
      nonNullable: true, validators: [Validators.required],
    }),
    documentNumber: new FormControl('', { nonNullable: true }),
    documentDate: new FormControl('', {
      nonNullable: true, validators: [Validators.required],
    }),
    entity: new FormControl('', { nonNullable: true }),
    instruction: new FormControl<CorrespondenceInstruction | null>(null, {
      validators: [Validators.required],
    }),
    explanation: new FormControl('', { nonNullable: true }),
    copyTo: new FormControl('', { nonNullable: true }),
  });

  readonly technicalId = signal<string | null>(null);
  readonly busy = signal(false);
  readonly loading = signal(false);
  readonly readOnly = signal(false);
  readonly creationUncertain = signal(false);
  readonly updateUncertain = signal(false);
  readonly deletionUncertain = signal(false);
  readonly sendingUncertain = signal(false);
  readonly deletingDocumentId = signal<string | null>(null);
  readonly queuedPdfs = signal<readonly QueuedPdf[]>([]);
  readonly savedPdfs = signal<readonly CorrespondenceDocumentUpload[]>([]);
  readonly notice = signal<Notice | null>(null);
  private nextPdfId = 0;

  ngOnInit(): void {
    const draft = this.route.snapshot.queryParamMap.get('draft');
    if (draft) void this.loadDraft(draft);
  }

  private blocked(): boolean {
    return this.busy() || this.readOnly() || this.creationUncertain()
      || this.updateUncertain() || this.deletionUncertain() || this.sendingUncertain()
      || this.deletingDocumentId() !== null;
  }

  private async loadDraft(id: string): Promise<void> {
    this.loading.set(true);
    this.busy.set(true);
    this.readOnly.set(true);
    this.form.disable({ emitEvent: false });
    this.notice.set(null);
    try {
      const draft = await firstValueFrom(this.api.getDetails(id));
      // Never open a Director draft in the executive editor, even if a URL was crafted.
      if (draft.state !== 'DRAFT' || !draft.executiveCompose) {
        this.notice.set({ kind: 'error', message: this.translation.text().compose.feedback.notEditable });
        return;
      }
      this.technicalId.set(draft.technicalId);
      this.savedPdfs.set(draft.documents);
      this.form.patchValue({
        subject: draft.subject,
        documentNumber: draft.documentNumber ?? '',
        documentDate: draft.documentDate ?? '',
        recipient: draft.executiveCompose.recipient,
        copyTo: draft.executiveCompose.copyTo,
        entity: draft.executiveCompose.entity,
        instruction: (this.instructions.includes(draft.executiveCompose.instruction as CorrespondenceInstruction)
          ? draft.executiveCompose.instruction as CorrespondenceInstruction : null),
        explanation: draft.executiveCompose.explanation,
      });
      this.form.markAsPristine();
      this.form.markAsUntouched();
      this.readOnly.set(false);
      this.form.enable({ emitEvent: false });
    } catch {
      this.notice.set({ kind: 'error', message: this.translation.text().compose.feedback.loadFailed });
    } finally {
      this.busy.set(false);
      this.loading.set(false);
    }
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (this.blocked()) return;
    let invalid = false;
    const additions: QueuedPdf[] = [];
    for (const file of files) {
      if (!file.name.toLowerCase().endsWith('.pdf') || file.size <= 0
          || file.size > MAX_PDF_BYTES) {
        invalid = true;
        continue;
      }
      if ([...this.queuedPdfs(), ...additions].some(item =>
        item.file.name === file.name && item.file.size === file.size)) continue;
      additions.push({ id: ++this.nextPdfId, file, status: 'queued' });
    }
    if (additions.length) this.queuedPdfs.update(current => [...current, ...additions]);
    if (invalid) this.notice.set({ kind: 'error', message: this.translation.text().compose.feedback.invalidPdf });
  }

  removeQueuedPdf(id: number): void {
    if (this.blocked()) return;
    this.queuedPdfs.update(current => current.filter(item =>
      item.id !== id || (item.status !== 'queued' && item.status !== 'failed')));
  }

  retryFailedPdf(id: number): void {
    if (this.blocked()) return;
    this.queuedPdfs.update(current => current.map(item =>
      item.id === id && item.status === 'failed' ? { ...item, status: 'queued' } : item));
    void this.saveDraft();
  }

  private markPdf(id: number, status: UploadStatus): void {
    this.queuedPdfs.update(current => current.map(item =>
      item.id === id ? { ...item, status } : item));
  }

  async removeSavedPdf(document: CorrespondenceDocumentUpload): Promise<void> {
    const id = this.technicalId();
    if (!id || this.blocked()) return;
    if (!window.confirm(`${this.translation.text().compose.feedback.confirmDelete}\n${document.filename}`)) return;
    this.deletingDocumentId.set(document.nodeId);
    this.notice.set(null);
    try {
      await firstValueFrom(this.api.deleteDocument(id, document.nodeId));
      this.savedPdfs.update(current => current.filter(item => item.nodeId !== document.nodeId));
    } catch (error) {
      if (this.uncertain(error)) this.deletionUncertain.set(true);
      this.notice.set({
        kind: this.uncertain(error) ? 'warning' : 'error',
        message: this.translation.text().compose.feedback.deleteFailed,
      });
    } finally {
      this.deletingDocumentId.set(null);
    }
  }

  /** Persist exactly the existing executive form fields, then upload PDFs. Never sends. */
  async saveDraft(): Promise<void> {
    if (this.blocked()) return;
    if (!this.form.controls.subject.value.trim()) {
      this.form.controls.subject.markAsTouched();
      this.notice.set({ kind: 'error', message: this.translation.text().compose.feedback.subjectRequired });
      return;
    }
    const creating = !this.technicalId();
    this.busy.set(true);
    this.notice.set(null);
    this.form.disable({ emitEvent: false });
    try {
      let id = this.technicalId();
      const values = this.form.getRawValue();
      const payload: ExecutiveCorrespondenceDraft = {
        subject: values.subject.trim(),
        bodyText: null,
        documentNumber: this.optional(values.documentNumber),
        documentDate: this.optional(values.documentDate),
        sourceText: null,
        executiveCompose: {
          recipient: values.recipient.trim(),
          copyTo: values.copyTo.trim(),
          entity: values.entity.trim(),
          instruction: values.instruction ?? '',
          explanation: values.explanation.trim(),
        },
      };
      if (!id) {
        const created = await firstValueFrom(this.api.createDraft(payload));
        id = created.technicalId;
        // Retain this ID before touching attachments. Never create twice on retry.
        this.technicalId.set(id);
        this.form.markAsPristine();
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { draft: id },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        });
      } else if (this.form.dirty) {
        await firstValueFrom(this.api.updateDraft(id, payload));
        this.form.markAsPristine();
      }
      for (const queued of this.queuedPdfs()) {
        if (queued.status !== 'queued') continue;
        this.markPdf(queued.id, 'uploading');
        try {
          const saved = await firstValueFrom(this.api.uploadDocument(id, queued.file));
          this.savedPdfs.update(current => [...current, saved]);
          this.queuedPdfs.update(current => current.filter(item => item.id !== queued.id));
        } catch (error) {
          this.markPdf(queued.id, this.uncertain(error) ? 'verify' : 'failed');
        }
      }
      const uncertain = this.queuedPdfs().some(item => item.status === 'verify');
      const failed = this.queuedPdfs().some(item => item.status === 'failed');
      this.notice.set({
        kind: uncertain || failed ? 'warning' : 'success',
        message: uncertain ? this.translation.text().compose.feedback.uploadUncertain
          : failed ? this.translation.text().compose.feedback.uploadFailed
          : this.translation.text().compose.feedback.saved,
      });
    } catch (error) {
      if (this.uncertain(error)) {
        if (creating) this.creationUncertain.set(true);
        else this.updateUncertain.set(true);
      }
      this.notice.set({
        kind: this.uncertain(error) ? 'warning' : 'error',
        message: this.uncertain(error)
          ? this.translation.text().compose.feedback.saveUncertain
          : this.translation.text().compose.feedback.saveFailed,
      });
    } finally {
      if (!this.readOnly() && !this.creationUncertain()
          && !this.updateUncertain() && !this.deletionUncertain()) {
        this.form.enable({ emitEvent: false });
      }
      this.busy.set(false);
    }
  }

  /** Submit only a confirmed, fully persisted executive draft to the Office.
   *  The existing Director submit path remains separate and unchanged. */
  async send(): Promise<void> {
    if (this.blocked()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notice.set({ kind: 'error', message: this.translation.text().compose.feedback.sendIncomplete });
      return;
    }
    // Reuse the already-tested Save Draft and attachment pipeline. No second
    // create/upload is issued after an uncertain response.
    await this.saveDraft();
    const id = this.technicalId();
    if (!id || this.blocked() || this.notice()?.kind !== 'success'
        || this.queuedPdfs().length !== 0) {
      this.notice.set({ kind: 'warning', message: this.translation.text().compose.feedback.sendBlocked });
      return;
    }
    if (!window.confirm(this.translation.text().compose.feedback.confirmSend)) return;

    this.busy.set(true);
    this.form.disable({ emitEvent: false });
    this.notice.set(null);
    try {
      await firstValueFrom(this.api.transitionCorrespondence(id, { action: 'SEND_TO_OFFICE' }));
      this.readOnly.set(true);
      this.notice.set({ kind: 'success', message: this.translation.text().compose.feedback.sent });
      void this.router.navigate(['/sent']);
    } catch (error) {
      if (this.uncertain(error)) this.sendingUncertain.set(true);
      this.notice.set({
        kind: this.uncertain(error) ? 'warning' : 'error',
        message: this.uncertain(error)
          ? this.translation.text().compose.feedback.sendUncertain
          : this.translation.text().compose.feedback.sendFailed,
      });
    } finally {
      if (!this.readOnly() && !this.sendingUncertain()) this.form.enable({ emitEvent: false });
      this.busy.set(false);
    }
  }

  cancel(): void {
    if (this.busy()) return;
    if ((this.form.dirty || this.queuedPdfs().length > 0)
        && !window.confirm(this.translation.text().compose.feedback.confirmCancel)) return;
    void this.router.navigate(['/drafts']);
  }

  private optional(value: string): string | null {
    return value.trim() || null;
  }

  private uncertain(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
  }
}
