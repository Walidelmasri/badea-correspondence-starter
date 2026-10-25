import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { CorrespondenceService } from '../../../core/correspondence/correspondence.service';
import { CorrespondenceDepartment } from '../../../core/correspondence/correspondence.model';
import { TranslationService } from '../../../core/i18n/translation.service';
import {
  CorrespondenceApiService,
  CorrespondenceDetails,
  CorrespondenceHistoryEntry,
  CorrespondenceMailboxBox,
  CorrespondenceInboxView,
  CorrespondenceMailboxPage,
  DepartmentAssignment,
  ShareEmployeeResult,
} from '../../data-access/correspondence-api.service';
import { eligibleForInitialOfficeForward } from './office-forward-eligibility';
import { chooseFeaturedPacket } from './featured-packet';
import { queueFollowUpFiles } from './follow-up-file-queue';
import { CorrespondenceWorkspaceComponent } from '../correspondence-workspace/correspondence-workspace.component';
import { CorrespondenceDocumentPreviewComponent } from '../correspondence-document-preview/correspondence-document-preview.component';
import { OfficeCompletionEditorComponent } from '../office-completion/office-completion-editor.component';

const PAGE_SIZE = 25;

@Component({
  selector: 'app-real-mailbox',
  standalone: true,
  imports: [
    CorrespondenceWorkspaceComponent,
    CorrespondenceDocumentPreviewComponent,
    OfficeCompletionEditorComponent,
  ],
  templateUrl: './real-mailbox.component.html',
  styleUrl: './real-mailbox.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RealMailboxComponent implements OnInit {
  readonly box = input.required<CorrespondenceMailboxBox>();
  private readonly api = inject(CorrespondenceApiService);
  private readonly correspondence = inject(CorrespondenceService);
  private readonly router = inject(Router);
  readonly session = inject(AuthSessionStore);
  readonly translation = inject(TranslationService);

  readonly headings = computed(() => this.box() === 'inbox'
    ? this.translation.text().inbox
    : this.translation.text().sent);
  readonly labels = computed(() => this.translation.text().mailbox);

  readonly inboxView = signal<CorrespondenceInboxView>('all');
  readonly inboxViews: readonly CorrespondenceInboxView[] =
    ['all', 'needs_action', 'handled', 'archived'];
  readonly page = signal<CorrespondenceMailboxPage | null>(null);
  readonly loading = signal(false);
  readonly listError = signal(false);
  readonly selectedId = signal<string | null>(null);
  readonly details = signal<CorrespondenceDetails | null>(null);
  // Presentation only: never change the API's document collection or Alfresco metadata.
  readonly featuredPacket = computed(() => chooseFeaturedPacket(this.details()?.generatedDocuments));
  readonly secondaryGeneratedDocuments = computed(() => {
    const packet = this.featuredPacket();
    return (this.details()?.generatedDocuments ?? []).filter(
      document => document.nodeId !== packet?.nodeId,
    );
  });
  readonly detailsLoading = signal(false);
  readonly detailsError = signal(false);
  readonly history = signal<readonly CorrespondenceHistoryEntry[] | null>(null);
  // The API order is oldest-first for authorization decisions. Display-only
  // projections must not mutate that order or affect canCompleteOfficeReview().
  readonly newestHistory = computed(() => [...(this.history() ?? [])].reverse());
  readonly latestDirectorFollowUp = computed(() => this.newestHistory().find(
    action => action.actionType === 'DIRECTOR_CONTINUE_CORRESPONDENCE',
  ) ?? null);
  readonly assignments = signal<readonly DepartmentAssignment[]>([]);
  readonly departments = signal<readonly CorrespondenceDepartment[]>([]);
  readonly assignmentResponse = signal('');
  readonly completingAssignment = signal(false);
  readonly assignmentNotice = signal<'failed' | 'uncertain' | null>(null);
  readonly continuingCycle = signal(false);
  readonly continueUncertain = signal(false);
  readonly continueNotice = signal<'continued' | 'failed' | 'uncertain' | null>(null);
  readonly followUpReason = signal('');
  readonly followUpFiles = signal<readonly File[]>([]);
  readonly sendingFollowUp = signal(false);
  readonly followUpUncertain = signal(false);
  readonly followUpNotice = signal<'failed' | 'uncertain' | 'invalid' | null>(null);
  readonly archiveReason = signal('');
  readonly archiving = signal(false);
  readonly archiveUncertain = signal(false);
  readonly archiveNotice = signal<'failed' | 'uncertain' | null>(null);
  readonly historyLoading = signal(false);
  readonly historyError = signal(false);
  readonly forwarding = signal(false);
  readonly forwardUncertain = signal(false);
  readonly forwardNotice = signal<'sent' | 'failed' | 'uncertain' | null>(null);
  readonly previewDocument = signal<CorrespondenceDetails['documents'][number] | null>(null);

  readonly mobileDetailsOpen = signal(false);
  readonly sharingDocument = signal<CorrespondenceDetails['documents'][number] | null>(null);
  readonly shareSelectedNodeIds = signal<readonly string[]>([]);
  readonly shareDocuments = computed(() => {
    const d = this.details();
    return d ? [...d.documents, ...(d.generatedDocuments ?? [])]
      .filter(doc => doc.mimeType === 'application/pdf') : [];
  });
  readonly sharedLink = signal<string | null>(null);
  readonly shareRecipient = signal('');
  readonly shareSelectedEmployee = signal<ShareEmployeeResult | null>(null);
  readonly shareComment = signal('');
  readonly shareBusy = signal(false);
  readonly shareLinkChecking = signal(false);
  readonly shareSearching = signal(false);
  readonly shareMatches = signal<readonly ShareEmployeeResult[]>([]);
  private shareLookupVersion = 0;
  private shareSearchTimer: ReturnType<typeof setTimeout> | undefined;
  readonly shareNotice = signal<'link-error' | 'email-submitted' | 'email-uncertain'
    | 'email-error' | 'revoked' | 'revoke-error' | 'copied' | null>(null);
  private shareVersion = 0;
  private sharePreviousFocus: HTMLElement | null = null;
  readonly validShareRecipient = computed(() => !!this.shareSelectedEmployee()
    && /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/.test(this.shareSelectedEmployee()!.username));

  readonly shareText = computed(() => this.translation.language() === 'ar' ? {
    share: 'مشاركة', title: 'مشاركة المستندات', files: 'المستندات المراد مشاركتها',
    recipient: 'إرسال إلى', recipientHint: 'ابحث بالاسم العربي أو الإنجليزي أو اسم المستخدم.',
    recipientPlaceholder: 'ابحث عن موظف...', remove: 'إزالة المستلم',
    searching: 'جارٍ البحث...', noMatches: 'لا توجد نتائج مطابقة.',
    archivedLink: 'لا يمكن إرسال مستندات مؤرشفة؛ يمكنك إدارة الرابط الموجود.',
    message: 'رسالة إضافية (اختياري)',
    creating: 'جارٍ التحضير...', copy: 'نسخ الرابط', send: 'إرسال المستندات',
    revoke: 'إلغاء الرابط الحالي', public: 'الرابط متاح لمن يملكه ويستطيع الوصول إلى خادم BADEA.',
    submitted: 'تم قبول البريد للإرسال. تحقق من صندوق البريد قبل المحاولة مرة ثانية.',
    uncertain: 'قد تكون الرسالة أُرسلت، لكن لم نستطع التأكيد. تحقق من البريد قبل المحاولة مجددًا.',
    failed: 'تعذّر إتمام العملية. تحقق من البيانات أو اتصل بالدعم.',
    revoked: 'تم إلغاء الرابط.', copied: 'تم نسخ الرابط.',
    revokeConfirm: 'إلغاء الرابط سيمنع جميع حامليه من فتح هذا المستند. متابعة؟',
    close: 'إغلاق', back: 'العودة إلى القائمة', linkOptions: 'رابط المستند المحدد',
    selectOne: 'اختر مستندًا واحدًا على الأقل (بحد أقصى 10).',
    lookupFailed: 'تعذّر البحث عن الموظفين. حاول مرة أخرى.',
  } : {
    share: 'Share', title: 'Share documents', files: 'Choose documents',
    recipient: 'Send to', recipientHint: 'Search by English or Arabic name, or username.',
    recipientPlaceholder: 'Search employees by name...', remove: 'Remove recipient',
    searching: 'Searching...', noMatches: 'No matching employees.',
    archivedLink: 'Archived correspondence cannot be newly shared; existing links can be managed.',
    message: 'Add a message (optional)',
    creating: 'Working...', copy: 'Copy Link', send: 'Send documents',
    revoke: 'Revoke current link', public: 'Anyone holding the link who can reach BADEA can open it.',
    submitted: 'Email accepted for delivery. Check the mailbox before sending again.',
    uncertain: 'Email might have been sent. Check the mailbox before trying again.',
    failed: 'The operation failed. Check the details or contact support.',
    revoked: 'Shared link revoked.', copied: 'Link copied.',
    revokeConfirm: 'Revoking this link removes access for everyone using it. Continue?',
    close: 'Close', back: 'Back to list', linkOptions: 'Link for selected document',
    selectOne: 'Select at least one PDF, up to 10.',
    lookupFailed: 'Employee search failed. Try again.',
  });

  private detailVersion = 0;
  private pageVersion = 0;

  readonly selected = computed(() => this.page()?.items.find(
    item => item.technicalId === this.selectedId()) ?? null);
  readonly canGoBack = computed(() => !!this.page()
    && this.page()!.offset > 0 && !this.loading());
  readonly canGoForward = computed(() => !!this.page()
    && this.page()!.offset + this.page()!.limit < this.page()!.total
    && !this.loading());
  readonly canForwardToPresident = computed(() =>
    !this.forwarding() && !this.forwardUncertain() &&
    eligibleForInitialOfficeForward(
      this.box(),
      this.session.currentUser()?.employeeProfile?.correspondenceRole ?? null,
      this.details()?.state ?? null,
      this.history(),
    ),
  );

  readonly canCompleteOfficeReview = computed(() => {
    if (this.box() !== 'inbox'
        || this.session.currentUser()?.employeeProfile?.correspondenceRole !== 'PRESIDENT_OFFICE'
        || this.details()?.state !== 'WITH_PRESIDENT_OFFICE') return false;
    const actions = this.history();
    if (!actions?.length) return false;
    const last = actions[actions.length - 1];
    return last.actionType === 'SEND_TO_OFFICE' && last.actorRole === 'PRESIDENT'
      && last.fromState === 'WITH_PRESIDENT'
      && last.toState === 'WITH_PRESIDENT_OFFICE';
  });

  readonly canReview = computed(() => this.box() === 'inbox'
    && this.details()?.state === 'WITH_PRESIDENT'
    && this.session.currentUser()?.employeeProfile?.correspondenceRole === 'PRESIDENT');

  ngOnInit(): void {
    // The directory is display-only; a temporary directory failure must not
    // block authenticated mailbox access or document preview.
    void this.loadDepartments();
    void this.loadPage(0);
  }

  private async loadDepartments(): Promise<void> {
    try {
      this.departments.set(await firstValueFrom(this.correspondence.getDepartments()));
    } catch {
      // Preserve the exact department code as a safe, recognizable fallback.
      this.departments.set([]);
    }
  }

  departmentName(code: string): string {
    const match = this.departments().find(dept => dept.code === code);
    if (!match) return code;
    return this.translation.language() === 'ar'
      ? (match.nameArabic || match.nameEnglish) : match.nameEnglish;
  }

  setInboxView(view: CorrespondenceInboxView): void {
    if (this.box() !== 'inbox' || this.inboxView() === view) return;
    this.inboxView.set(view);
    this.mobileDetailsOpen.set(false);
    this.selectedId.set(null);
    // Invalidate the previous page request so switching filters while loading works.
    ++this.pageVersion;
    this.loading.set(false);
    void this.loadPage(0);
  }

  async loadPage(offset: number): Promise<void> {
    if (this.loading()) return;
    this.mobileDetailsOpen.set(false);
    const version = ++this.pageVersion;
    ++this.detailVersion;
    this.closePreview();
    this.closeShare();
    this.loading.set(true);
    this.listError.set(false);
    this.details.set(null);
    this.detailsError.set(false);
    this.history.set(null);
    this.historyError.set(false);
    this.forwardUncertain.set(false);
    this.continueUncertain.set(false);
    try {
      const page = await firstValueFrom(this.api.listMailbox(this.box(), offset, PAGE_SIZE, this.inboxView()));
      if (version !== this.pageVersion) return;
      this.page.set(page);
      const id = page.items.find(item => item.technicalId === this.selectedId())?.technicalId
        ?? page.items[0]?.technicalId ?? null;
      this.selectedId.set(id);
      if (id) void this.loadDetails(id);
    } catch {
      if (version === this.pageVersion) {
        this.page.set(null);
        this.selectedId.set(null);
        this.listError.set(true);
      }
    } finally {
      if (version === this.pageVersion) this.loading.set(false);
    }
  }

  select(id: string): void {
    this.mobileDetailsOpen.set(true);
    if (id === this.selectedId()) return;
    this.closePreview();
    this.closeShare();
    this.continueNotice.set(null);
    this.selectedId.set(id);
    void this.loadDetails(id);
  }

  retryDetails(): void {
    const id = this.selectedId();
    if (id) void this.loadDetails(id);
  }

  private async loadDetails(id: string): Promise<void> {
    const version = ++this.detailVersion;
    this.details.set(null);
    this.detailsError.set(false);
    this.history.set(null);
    this.assignments.set([]);
    this.assignmentNotice.set(null);
    this.assignmentResponse.set('');
    this.archiveReason.set('');
    this.followUpReason.set('');
    this.followUpFiles.set([]);
    this.followUpUncertain.set(false);
    this.followUpNotice.set(null);
    this.continueUncertain.set(false);
    this.archiveNotice.set(null);
    this.archiveUncertain.set(false);
    this.historyError.set(false);
    this.historyLoading.set(true);
    this.detailsLoading.set(true);
    try {
      const result = await firstValueFrom(this.api.getDetails(id));
      if (version !== this.detailVersion || this.selectedId() !== id) return;
      this.details.set(result);
      this.detailsLoading.set(false);
      // Assignment API is independent of correspondence details/history.
      try {
        const assigned = await firstValueFrom(this.api.getDepartmentAssignments(id));
        if (version === this.detailVersion && this.selectedId() === id) {
          this.assignments.set(assigned.items);
        }
      } catch {
        // Existing correspondence may be pre-distribution; keep its PDFs readable.
      }
      // History failure must never block correspondence details or PDF viewing.
      try {
        const actionPage = await firstValueFrom(this.api.getHistory(id));
        if (version === this.detailVersion && this.selectedId() === id) {
          this.history.set(actionPage.items);
        }
      } catch {
        if (version === this.detailVersion) this.historyError.set(true);
      }
    } catch {
      if (version === this.detailVersion) this.detailsError.set(true);
    } finally {
      if (version === this.detailVersion) {
        this.detailsLoading.set(false);
        this.historyLoading.set(false);
      }
    }
  }

  previous(): void {
    const p = this.page();
    if (p && this.canGoBack()) void this.loadPage(Math.max(0, p.offset - p.limit));
  }

  next(): void {
    const p = this.page();
    if (p && this.canGoForward()) void this.loadPage(p.offset + p.limit);
  }

  openCompose(): void {
    void this.router.navigate(['/compose']);
  }

  openDocument(document: CorrespondenceDetails['documents'][number]): void {
    if (document.mimeType !== 'application/pdf' || !this.details()) {
      return;
    }
    this.previewDocument.set(document);
  }


  backToMailbox(): void {
    this.closeShare();
    this.closePreview();
    this.mobileDetailsOpen.set(false);
  }

  beginShare(document: CorrespondenceDetails['documents'][number]): void {
    if (document.mimeType !== 'application/pdf' || !this.details()) return;
    ++this.shareVersion;
    this.sharePreviousFocus = globalThis.document?.activeElement as HTMLElement | null;
    this.sharingDocument.set(document);
    this.shareSelectedNodeIds.set([document.nodeId]);
    this.sharedLink.set(null);
    this.shareRecipient.set('');
    this.shareSelectedEmployee.set(null);
    this.shareComment.set('');
    this.shareNotice.set(null);
    this.shareBusy.set(false);
    this.shareMatches.set([]);
    this.shareLinkChecking.set(true);
    void this.lookupExistingShareLink();
    setTimeout(() => globalThis.document?.getElementById('mailbox-share-employee')?.focus(), 0);
  }

  private async lookupExistingShareLink(): Promise<void> {
    const document = this.sharingDocument();
    const technicalId = this.selectedId();
    if (!document || !technicalId) return;
    const version = this.shareVersion;
    try {
      const link = await firstValueFrom(this.api.existingShareLink(technicalId, document.nodeId));
      if (version === this.shareVersion && technicalId === this.selectedId()) {
        this.sharedLink.set(link.available ? link.url ?? null : null);
      }
    } catch {
      if (version === this.shareVersion) this.shareNotice.set('link-error');
    } finally {
      if (version === this.shareVersion) this.shareLinkChecking.set(false);
    }
  }

  closeShare(): void {
    if (this.shareBusy()) return;
    ++this.shareVersion;
    ++this.shareLookupVersion;
    if (this.shareSearchTimer) clearTimeout(this.shareSearchTimer);
    this.shareSearchTimer = undefined;
    this.shareMatches.set([]);
    this.shareSelectedEmployee.set(null);
    this.shareSelectedNodeIds.set([]);
    this.shareSearching.set(false);
    this.shareLinkChecking.set(false);
    this.sharingDocument.set(null);
    this.sharedLink.set(null);
    this.shareNotice.set(null);
    this.sharePreviousFocus?.focus();
    this.sharePreviousFocus = null;
  }

  toggleShareDocument(nodeId: string, checked: boolean): void {
    if (this.shareBusy() || this.details()?.state === 'ARCHIVED') return;
    const selected = this.shareSelectedNodeIds();
    if (checked && selected.length < 10 && !selected.includes(nodeId)) {
      this.shareSelectedNodeIds.set([...selected, nodeId]);
    } else if (!checked) {
      this.shareSelectedNodeIds.set(selected.filter(id => id !== nodeId));
    }
    this.shareNotice.set(null);
  }

  updateShareRecipient(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.shareRecipient.set(value);
    this.shareSelectedEmployee.set(null);
    this.shareMatches.set([]);
    this.shareNotice.set(null);
    const version = ++this.shareLookupVersion;
    if (this.shareSearchTimer) clearTimeout(this.shareSearchTimer);
    this.shareSearchTimer = undefined;
    this.shareSearching.set(false);
    const query = value.trim();
    if (query.length < 2 || query.length > 60) return;
    this.shareSearching.set(true);
    this.shareSearchTimer = setTimeout(() => {
      void this.searchRecipients(query, version);
    }, 250);
  }

  private async searchRecipients(query: string, version: number): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.searchShareRecipients(query));
      if (version === this.shareLookupVersion && this.sharingDocument()) {
        this.shareMatches.set(result.employees);
      }
    } catch {
      if (version === this.shareLookupVersion) this.shareNotice.set('email-error');
    } finally {
      if (version === this.shareLookupVersion) this.shareSearching.set(false);
    }
  }

  selectShareRecipient(employee: ShareEmployeeResult): void {
    ++this.shareLookupVersion;
    if (this.shareSearchTimer) clearTimeout(this.shareSearchTimer);
    this.shareSearchTimer = undefined;
    this.shareRecipient.set('');
    this.shareSelectedEmployee.set(employee);
    this.shareSearching.set(false);
    this.shareMatches.set([]);
    this.shareNotice.set(null);
  }

  removeShareRecipient(): void {
    this.shareSelectedEmployee.set(null);
    this.shareRecipient.set('');
    this.shareNotice.set(null);
  }

  updateShareComment(event: Event): void {
    this.shareComment.set((event.target as HTMLTextAreaElement).value);
    this.shareNotice.set(null);
  }

  /** Copying a link is independent of email sending; create it on explicit copy if needed. */
  async copyShareLink(): Promise<void> {
    const document = this.sharingDocument();
    const id = this.selectedId();
    if (!document || !id || this.shareBusy() || this.shareLinkChecking()) return;
    const version = this.shareVersion;
    this.shareBusy.set(true);
    this.shareNotice.set(null);
    try {
      let url = this.sharedLink();
      if (!url && this.details()?.state !== 'ARCHIVED') {
        const result = await firstValueFrom(this.api.createShareLink(id, document.nodeId));
        url = result.url;
        if (version === this.shareVersion) this.sharedLink.set(url);
      }
      if (!url) return;
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(url);
      } else {
        window.prompt('Copy link:', url);
      }
      if (version === this.shareVersion) this.shareNotice.set('copied');
    } catch {
      if (version === this.shareVersion) this.shareNotice.set('link-error');
    } finally {
      if (version === this.shareVersion) this.shareBusy.set(false);
    }
  }

  async sendShareEmail(): Promise<void> {
    const document = this.sharingDocument();
    const id = this.selectedId();
    const employee = this.shareSelectedEmployee();
    const message = this.shareComment().trim();
    const nodeIds = this.shareSelectedNodeIds();
    if (!document || !id || !employee || !this.validShareRecipient() || this.shareBusy()
        || this.details()?.state === 'ARCHIVED' || nodeIds.length === 0 || nodeIds.length > 10
        || this.shareNotice() === 'email-uncertain' || this.shareNotice() === 'email-submitted'
        || message.length > 2000) return;
    const version = this.shareVersion;
    this.shareBusy.set(true);
    this.shareNotice.set(null);
    try {
      const result = await firstValueFrom(this.api.sendShareBatchEmail(
        id, nodeIds, employee.username, message));
      if (version === this.shareVersion) {
        const clicked = result.links.find(link => link.nodeId === document.nodeId);
        if (clicked) this.sharedLink.set(clicked.url);
        this.shareNotice.set('email-submitted');
      }
    } catch (error: unknown) {
      if (version === this.shareVersion) {
        const uncertain = error instanceof HttpErrorResponse
          && (error.status === 0 || error.status >= 500);
        this.shareNotice.set(uncertain ? 'email-uncertain' : 'email-error');
      }
    } finally {
      if (version === this.shareVersion) this.shareBusy.set(false);
    }
  }

  async revokeShareLink(): Promise<void> {
    const document = this.sharingDocument();
    const id = this.selectedId();
    if (!document || !id || !this.sharedLink() || this.shareBusy()) return;
    if (!window.confirm(this.shareText().revokeConfirm)) return;
    const version = this.shareVersion;
    this.shareBusy.set(true);
    this.shareNotice.set(null);
    try {
      await firstValueFrom(this.api.revokeShareLink(id, document.nodeId));
      if (version === this.shareVersion) {
        this.sharedLink.set(null);
        this.shareNotice.set('revoked');
      }
    } catch {
      if (version === this.shareVersion) this.shareNotice.set('revoke-error');
    } finally {
      if (version === this.shareVersion) this.shareBusy.set(false);
    }
  }

  closePreview(): void {
    this.previewDocument.set(null);
  }

  async forwardToPresident(): Promise<void> {
    const id = this.selectedId();
    if (!id || !this.canForwardToPresident()) return;
    if (!window.confirm(this.labels().confirmForwardToPresident)) return;

    this.forwarding.set(true);
    this.forwardNotice.set(null);
    try {
      await firstValueFrom(this.api.transitionCorrespondence(id, {
        action: 'SEND_TO_PRESIDENT',
      }));
      // A successful transition changes the assigned Inbox immediately.
      this.selectedId.set(null);
      await this.loadPage(0);
      this.forwardNotice.set('sent');
    } catch (error: unknown) {
      // A lost response may mean Alfresco already committed the transition.
      // Never automatically submit the same transition twice.
      const uncertain = error instanceof HttpErrorResponse &&
        (error.status === 0 || error.status >= 500);
      this.forwardUncertain.set(uncertain);
      this.forwardNotice.set(uncertain ? 'uncertain' : 'failed');
      if (!uncertain) void this.loadPage(0);
    } finally {
      this.forwarding.set(false);
    }
  }

  readonly myActionableAssignment = computed(() => {
    if (this.box() !== 'inbox' || this.details()?.state !== 'WITH_DIRECTOR'
        || this.session.currentUser()?.employeeProfile?.correspondenceRole !== 'DIRECTOR') return null;
    const code = this.session.currentUser()?.employeeProfile?.department?.code;
    return this.assignments().find(item => item.departmentCode === code
      && item.recipientType === 'TO' && item.status === 'PENDING') ?? null;
  });

  readonly canDirectorFollowUp = computed(() => {
    if (this.box() !== 'inbox' || this.details()?.state !== 'WITH_DIRECTOR'
        || this.session.currentUser()?.employeeProfile?.correspondenceRole !== 'DIRECTOR') return false;
    const department = this.session.currentUser()?.employeeProfile?.department?.code;
    const required = this.assignments().filter(item => item.recipientType === 'TO');
    return required.length > 0
      && required.every(item => item.status === 'COMPLETED')
      && required.some(item => item.departmentCode === department);
  });

  updateFollowUpReason(event: Event): void {
    this.followUpReason.set((event.target as HTMLTextAreaElement).value);
  }

  chooseFollowUpFiles(event: Event): void {
    const input = event.target as HTMLInputElement;
    const incoming = Array.from(input.files ?? []);
    // Re-selecting the same file after removal must produce a change event.
    input.value = '';
    if (this.sendingFollowUp() || this.followUpUncertain() || incoming.length === 0) return;
    const queued = queueFollowUpFiles(this.followUpFiles(), incoming);
    if (queued === null) {
      // Keep previously validated selections when an additional pick is invalid.
      this.followUpNotice.set('invalid');
      return;
    }
    this.followUpFiles.set(queued);
    this.followUpNotice.set(null);
  }

  removeFollowUpFile(file: File): void {
    if (this.sendingFollowUp() || this.followUpUncertain()) return;
    this.followUpFiles.update(files => files.filter(entry => entry !== file));
    this.followUpNotice.set(null);
  }

  async directorFollowUp(): Promise<void> {
    const id = this.selectedId();
    const reason = this.followUpReason().trim();
    if (!id || !this.canDirectorFollowUp() || this.sendingFollowUp()
        || this.followUpUncertain() || !reason || reason.length > 2000) return;
    if (!window.confirm(this.labels().directorFollowUpConfirm)) return;
    this.sendingFollowUp.set(true);
    this.followUpNotice.set(null);
    try {
      // Upload first so Office does not receive a new cycle with missing evidence.
      // Never retry a binary upload automatically following a lost response.
      for (const file of this.followUpFiles()) {
        await firstValueFrom(this.api.uploadDocument(id, file));
      }
      await firstValueFrom(this.api.continueDirectorCycle(id, reason));
      await this.loadPage(0);
    } catch (error: unknown) {
      const uncertain = error instanceof HttpErrorResponse
        && (error.status === 0 || error.status >= 500);
      this.followUpUncertain.set(uncertain);
      this.followUpNotice.set(uncertain ? 'uncertain' : 'failed');
    } finally { this.sendingFollowUp.set(false); }
  }

  updateAssignmentResponse(event: Event): void {
    this.assignmentResponse.set((event.target as HTMLTextAreaElement).value);
  }

  async completeDepartmentAssignment(): Promise<void> {
    const assignment = this.myActionableAssignment();
    const id = this.selectedId();
    const response = this.assignmentResponse().trim();
    if (!assignment || !id || !response || this.completingAssignment()) return;
    if (!window.confirm(this.labels().confirmAssignmentComplete)) return;
    this.completingAssignment.set(true);
    this.assignmentNotice.set(null);
    try {
      await firstValueFrom(this.api.completeDepartmentAssignment(id, assignment.assignmentId, response));
      await this.loadPage(0);
    } catch (error: unknown) {
      const uncertain = error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
      this.assignmentNotice.set(uncertain ? 'uncertain' : 'failed');
    } finally { this.completingAssignment.set(false); }
  }

  /** UI eligibility; the server rechecks active-cycle assignments and authorization. */
  readonly officeDistributionReady = computed(() =>
    this.box() === 'inbox'
    && this.session.currentUser()?.employeeProfile?.correspondenceRole === 'PRESIDENT_OFFICE'
    && this.details()?.state === 'WITH_DIRECTOR'
    && this.assignments().length > 0
    && this.assignments().every(item => item.recipientType !== 'TO' || item.status === 'COMPLETED'),
  );
  readonly canArchiveDistribution = computed(() => this.officeDistributionReady()
    && !this.archiving() && !this.archiveUncertain() && !this.continuingCycle() && !this.continueUncertain());
  readonly canContinueDistribution = computed(() => this.officeDistributionReady()
    && !this.continuingCycle() && !this.continueUncertain() && !this.archiving() && !this.archiveUncertain());

  async continueCorrespondence(): Promise<void> {
    const id = this.selectedId();
    if (!id || !this.canContinueDistribution()) return;
    if (!window.confirm(this.labels().confirmContinueCorrespondence)) return;
    this.continuingCycle.set(true);
    this.continueNotice.set(null);
    try {
      await firstValueFrom(this.api.continueOfficeCycle(id));
      // Refresh both the workflow history and the active assignment projection.
      // The old completed assignments remain stored in Alfresco for audit.
      await this.loadPage(0);
      this.continueNotice.set('continued');
    } catch (error: unknown) {
      // The server may have committed even if the response was lost. Do not auto-retry.
      const uncertain = error instanceof HttpErrorResponse
        && (error.status === 0 || error.status >= 500);
      this.continueUncertain.set(uncertain);
      this.continueNotice.set(uncertain ? 'uncertain' : 'failed');
    } finally {
      this.continuingCycle.set(false);
    }
  }

  updateArchiveReason(event: Event): void {
    this.archiveReason.set((event.target as HTMLTextAreaElement).value);
  }

  async archiveDistribution(): Promise<void> {
    const id = this.selectedId();
    const reason = this.archiveReason().trim();
    if (!id || !this.canArchiveDistribution() || !reason) return;
    if (!window.confirm(this.labels().confirmAdministrativeClosure)) return;
    this.archiving.set(true);
    this.archiveNotice.set(null);
    try {
      await firstValueFrom(this.api.transitionCorrespondence(id, {
        action: 'ADMINISTRATIVE_CLOSE', closureReason: reason,
      }));
      await this.loadPage(0);
    } catch (error: unknown) {
      const uncertain = error instanceof HttpErrorResponse
        && (error.status === 0 || error.status >= 500);
      this.archiveUncertain.set(uncertain);
      this.archiveNotice.set(uncertain ? 'uncertain' : 'failed');
    } finally {
      this.archiving.set(false);
    }
  }

  actionLabel(actionType: string): string {
    switch (actionType) {
      case 'SEND_TO_OFFICE': return this.labels().historyActions.sentToOffice;
      case 'SEND_TO_PRESIDENT': return this.labels().historyActions.sentToPresident;
      case 'SEND_TO_DIRECTOR': return this.labels().historyActions.sentToDirector;
      case 'DISTRIBUTE_TO_DEPARTMENTS': return this.labels().historyActions.distributedToDepartments;
      case 'COMPLETE_DEPARTMENT_ASSIGNMENT': return this.labels().historyActions.assignmentCompleted;
      case 'MARK_DONE': return this.labels().historyActions.completed;
      case 'ADMINISTRATIVE_CLOSE': return this.labels().historyActions.archived;
      case 'CONTINUE_CORRESPONDENCE': return this.labels().historyActions.continued;
      case 'DIRECTOR_CONTINUE_CORRESPONDENCE': return this.labels().historyActions.directorContinued;
      default: return actionType;
    }
  }

  openPresidentReview(): void {
    if (!this.canReview()) return;
    const id = this.selectedId();
    if (id) void this.router.navigate(['/president-review', id]);
  }

  statusLabel(state: CorrespondenceDetails['state']): string {
    switch (state) {
      case 'DRAFT': return this.labels().states.draft;
      case 'WITH_PRESIDENT_OFFICE': return this.labels().states.office;
      case 'WITH_PRESIDENT': return this.labels().states.president;
      case 'WITH_DIRECTOR': return this.labels().states.director;
      case 'ARCHIVED': return this.labels().states.archived;
    }
  }

  formatDate(value: string): string {
    const locale = this.translation.language() === 'ar' ? 'ar-SA-u-ca-gregory' : 'en-GB';
    return new Intl.DateTimeFormat(locale, {
      day: 'numeric', month: 'short', year: 'numeric',
    }).format(new Date(value));
  }

  formatSize(bytes: number): string {
    return `${(bytes / (1024 * 1024)).toFixed(2)} MiB`;
  }
}
