import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { firstValueFrom, interval } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthSessionStore } from '../../core/auth/auth-session.store';
import { TranslationService } from '../../core/i18n/translation.service';
import { SecretaryDraft, SecretaryPdf, TarasolSecretaryApiService } from './tarasol-secretary-api.service';

@Component({
  selector: 'app-tarasol-secretary-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './tarasol-secretary-page.component.html',
  styleUrl: './tarasol-secretary-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TarasolSecretaryPageComponent implements OnDestroy {
  private readonly session = inject(AuthSessionStore);
  private readonly translation = inject(TranslationService);
  t(english: string, arabic: string): string {
    return this.translation.language() === 'ar' ? arabic : english;
  }
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(TarasolSecretaryApiService);
  private readonly sanitizer = inject(DomSanitizer);
  readonly documents = signal<readonly SecretaryPdf[]>([]);
  readonly documentsLoaded = signal(false);
  readonly previewUrl = signal<SafeResourceUrl | null>(null);
  private objectUrl: string | null = null;
  readonly managerMode = this.route.snapshot.data['mode'] === 'manager';
  readonly availableUnits = this.managerMode
    ? (this.session.currentUser()?.employeeProfile?.managerWorkspaces ?? [])
    : (this.session.currentUser()?.employeeProfile?.secretaryUnits ?? []);
  readonly unitCode = signal(this.managerMode
    ? (this.session.workspaceUnit() ?? this.availableUnits[0]?.code ?? '')
    : (this.availableUnits[0]?.code ?? ''));
  readonly items = signal<readonly SecretaryDraft[]>([]);
  readonly total = signal(0);
  readonly pageOffset = signal(0);
  readonly pageSize = 20;
  readonly busy = signal(false);
  readonly message = signal('');
  readonly error = signal('');
  subject = '';
  bodyText = '';
  explanation = '';
  selectedDraft: SecretaryDraft | null = null;

  constructor() {
    void this.reload();
    // Recheck only the caller's authorized approval list; no unrestricted
    // inbox polling or stale notification view for secretary-only accounts.
    interval(60_000).pipe(takeUntilDestroyed()).subscribe(() => {
      if (!this.busy() && (typeof document === 'undefined' || document.visibilityState === 'visible')) {
        void this.reload();
      }
    });
  }

  changeUnit(code: string): void {
    if (!this.availableUnits.some(unit => unit.code === code)) return;
    this.unitCode.set(code);
    this.pageOffset.set(0);
    this.selectedDraft = null;
    this.clearPreview();
    this.documents.set([]);
    this.documentsLoaded.set(false);
    void this.reload();
  }

  async reload(): Promise<void> {
    this.message.set('');
    this.error.set('');
    if (!this.unitCode()) return;
    this.busy.set(true);
    try {
      const result = await firstValueFrom(this.api.list(
        this.unitCode(), this.managerMode ? 'pending' : 'mine', this.pageOffset(), this.pageSize));
      this.items.set(result.items);
      this.total.set(result.total);
    } catch {
      this.error.set(this.t('Could not load the latest approvals. No changes were made.', 'تعذر تحميل الاعتمادات الحالية. لم تُجرَ أي تغييرات.'));
    } finally {
      this.busy.set(false);
    }
  }

  previousPage(): void {
    if (this.busy() || this.pageOffset() === 0) return;
    this.pageOffset.set(Math.max(0, this.pageOffset() - this.pageSize));
    void this.reload();
  }

  nextPage(): void {
    if (this.busy() || this.pageOffset() + this.pageSize >= this.total()) return;
    this.pageOffset.set(this.pageOffset() + this.pageSize);
    void this.reload();
  }

  clearSelection(): void {
    this.selectedDraft = null;
    this.subject = '';
    this.bodyText = '';
    this.explanation = '';
    this.clearPreview();
    this.documents.set([]);
    this.documentsLoaded.set(false);
  }

  open(draft: SecretaryDraft): void {
    this.selectedDraft = draft;
    this.subject = draft.subject;
    this.bodyText = draft.bodyText ?? '';
    this.explanation = '';
    void this.loadDocuments(draft);
  }

  ngOnDestroy(): void {
    this.clearPreview();
  }

  async loadDocuments(draft: SecretaryDraft): Promise<void> {
    this.clearPreview();
    this.documents.set([]);
    this.documentsLoaded.set(false);
    this.error.set('');
    try {
      const result = await firstValueFrom(this.api.documents(
        draft.unitCode, draft.technicalId, this.managerMode ? 'pending' : 'mine'));
      if (this.selectedDraft?.technicalId === draft.technicalId) {
        this.documents.set(result.items);
        this.documentsLoaded.set(true);
      }
    } catch {
      this.error.set(this.t('Could not load this draft’s PDFs. Do not approve before reviewing the documents.', 'تعذر تحميل ملفات PDF للمسودة. لا تعتمدها قبل مراجعة المستندات.'));
    }
  }

  async preview(document: SecretaryPdf): Promise<void> {
    const draft = this.selectedDraft;
    if (!draft) return;
    this.error.set('');
    try {
      const blob = await firstValueFrom(this.api.preview(
        draft.unitCode, draft.technicalId, document.nodeId,
        this.managerMode ? 'pending' : 'mine'));
      if (this.selectedDraft?.technicalId !== draft.technicalId) return;
      if (blob.type.toLowerCase() !== 'application/pdf') {
        this.error.set(this.t('The preview response was not a PDF.', 'الملف المعروض ليس بصيغة PDF.'));
        return;
      }
      this.clearPreview();
      this.objectUrl = URL.createObjectURL(blob);
      this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl));
    } catch {
      this.error.set(this.t('Unable to open this PDF with your current access.', 'تعذر فتح ملف PDF بصلاحياتك الحالية.'));
    }
  }

  closePreview(): void {
    this.clearPreview();
  }

  private clearPreview(): void {
    this.previewUrl.set(null);
    if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
    this.objectUrl = null;
  }

  async createDraft(): Promise<void> {
    if (!this.subject.trim()) { this.error.set(this.t('Subject is required', 'الموضوع مطلوب')); return; }
    await this.perform('CREATE_DRAFT', {
      subject: this.subject.trim(), bodyText: this.bodyText.trim() || null,
    });
  }

  async revise(): Promise<void> {
    if (!this.selectedDraft || !this.subject.trim()
        || this.selectedDraft.approvalStatus === 'APPROVED'
        || this.selectedDraft.approvalStatus === 'PENDING') return;
    await this.perform('REVISE', {
      technicalId: this.selectedDraft.technicalId, subject: this.subject.trim(),
      bodyText: this.bodyText.trim() || null,
    });
  }

  async uploadPdf(event: Event): Promise<void> {
    if (!this.selectedDraft || this.selectedDraft.approvalStatus === 'PENDING'
        || this.selectedDraft.approvalStatus === 'APPROVED') return;
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.error.set('');
    this.message.set('');
    if (file.type !== 'application/pdf' || file.size > 25 * 1024 * 1024) {
      this.error.set(this.t('Choose a PDF under 25 MiB.', 'اختر ملف PDF لا يتجاوز 25 ميغابايت.'));
      input.value = '';
      return;
    }
    this.busy.set(true);
    try {
      const draft = this.selectedDraft;
      await firstValueFrom(this.api.uploadPdf(this.unitCode(), draft.technicalId, file));
      if (this.selectedDraft?.technicalId === draft.technicalId) await this.loadDocuments(draft);
      this.message.set(this.t('PDF attached to your draft.', 'تم إرفاق ملف PDF بمسودتك.'));
    } catch {
      this.error.set(this.t('PDF upload was not confirmed. Check your draft before trying again.', 'لم يتأكد رفع الملف. راجع المسودة قبل إعادة المحاولة.'));
    } finally {
      this.busy.set(false);
      input.value = '';
    }
  }

  async removePdf(doc: SecretaryPdf): Promise<void> {
    const draft = this.selectedDraft;
    if (!draft || this.managerMode || !['DRAFT', 'RETURNED'].includes(draft.approvalStatus)) return;
    if (!window.confirm(this.t(`Remove ${doc.filename} from this personal draft?`,
        `هل تريد حذف ${doc.filename} من هذه المسودة الشخصية؟`))) return;
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    try {
      await firstValueFrom(this.api.deletePdf(draft.unitCode, draft.technicalId, doc.nodeId));
      if (this.selectedDraft?.technicalId === draft.technicalId) await this.loadDocuments(draft);
      this.message.set(this.t('PDF removed from your draft.', 'تم حذف ملف PDF من مسودتك.'));
    } catch {
      this.error.set(this.t('Deletion was not confirmed. Reload the files before retrying.',
        'لم يتأكد حذف الملف. أعد تحميل المرفقات قبل المحاولة مجددًا.'));
    } finally { this.busy.set(false); }
  }

  async submit(draft: SecretaryDraft): Promise<void> {
    await this.perform('SUBMIT', { technicalId: draft.technicalId });
  }

  async returnDraft(draft: SecretaryDraft): Promise<void> {
    if (!this.explanation.trim()) { this.error.set(this.t('Please enter a return reason', 'يرجى كتابة سبب الإرجاع')); return; }
    await this.perform('RETURN', {
      technicalId: draft.technicalId, explanation: this.explanation.trim(),
    });
  }

  async approve(draft: SecretaryDraft): Promise<void> {
    if (!this.documentsLoaded() || this.selectedDraft?.technicalId !== draft.technicalId) {
      this.error.set(this.t('Review the correspondence and its documents before approving.', 'راجع المراسلة ومرفقاتها قبل الاعتماد.'));
      return;
    }
    await this.perform('APPROVE_AND_SEND', { technicalId: draft.technicalId });
  }

  private async perform(
    action: 'CREATE_DRAFT' | 'REVISE' | 'SUBMIT' | 'RETURN' | 'APPROVE_AND_SEND',
    fields: Record<string, string | null>): Promise<void> {
    this.error.set('');
    this.message.set('');
    this.busy.set(true);
    try {
      const response = await firstValueFrom(this.api.change(this.unitCode(), action, fields));
      this.message.set(action === 'APPROVE_AND_SEND'
        ? `Approved and sent: ${response.state ?? 'submitted'}`
        : this.t('Saved successfully', 'تم الحفظ بنجاح'));
      this.clearSelection();
      this.pageOffset.set(0);
      if (action === 'CREATE_DRAFT') {
        this.subject = '';
        this.bodyText = '';
      }
      const listing = await firstValueFrom(this.api.list(
        this.unitCode(), this.managerMode ? 'pending' : 'mine', this.pageOffset(), this.pageSize));
      this.items.set(listing.items);
      this.total.set(listing.total);
    } catch {
      this.error.set(this.t('The operation could not be confirmed. Reload before retrying to avoid duplicates.', 'لم يتأكد نجاح العملية. حدّث البيانات قبل تكرارها لتجنب التكرار.'));
    } finally { this.busy.set(false); }
  }
}
