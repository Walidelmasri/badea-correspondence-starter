import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  OnInit,
  DestroyRef,
  signal,
} from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { UnreadInboxStore } from '../../../core/notifications/unread-inbox.store';
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
import { groupSourceDocuments, sourceDocumentsForRound } from './source-document-rounds';
import { CorrespondenceWorkspaceComponent } from '../correspondence-workspace/correspondence-workspace.component';
import { CorrespondenceDocumentPreviewComponent } from '../correspondence-document-preview/correspondence-document-preview.component';
import { composeRouteForRole, inboxViewFromQuery, MAILBOX_PAGE_SIZE } from './mailbox-navigation';
import { OfficeCompletionEditorComponent } from '../office-completion/office-completion-editor.component';
import { SecretaryDraft, SecretaryPdf, SecretaryPersonalDraft, TarasolSecretaryApiService } from '../../../features/tarasol-secretary/tarasol-secretary-api.service';
import { PendingRoundContinuation, TarasolRoundContinuationApiService } from '../../../features/tarasol-secretary/tarasol-round-continuation-api.service';

const PAGE_SIZE = MAILBOX_PAGE_SIZE;

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
  private readonly secretaryApi = inject(TarasolSecretaryApiService);
  private readonly roundApi = inject(TarasolRoundContinuationApiService);
  private readonly correspondence = inject(CorrespondenceService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly unreadStore = inject(UnreadInboxStore);
  readonly session = inject(AuthSessionStore);
  readonly translation = inject(TranslationService);
  readonly secretaryMode = computed(() => this.session.secretaryMailbox());
  readonly secretaryUnitCode = computed(() => this.secretaryMode()
    ? this.session.secretaryInboxUnitCode() : null);
  // A manager appointment, not Alfresco group membership alone, identifies
  // the department whose secretary submissions this Inbox may review.
  readonly managerApprovalUnit = computed(() => {
    if (this.box() !== 'inbox') return null;
    const selected = this.session.activeManagerWorkspace();
    if (selected) return selected.code;
    const workspaces = this.session.availableWorkspaces();
    // A single Office manager appointment needs no extra workspace selection.
    if (workspaces.length === 1) return workspaces[0].code;
    const code = this.session.effectiveDepartmentCode();
    return workspaces.find(unit => unit.code === code)?.code ?? null;
  });
  readonly pendingApprovals = signal<readonly SecretaryDraft[]>([]);
  readonly pendingTotal = signal(0);
  readonly pendingPageSize = PAGE_SIZE;
  readonly pendingOffset = signal(0);
  readonly pendingLoadError = signal(false);
  readonly selectedPending = signal<SecretaryDraft | null>(null);
  readonly pendingMetadata = signal<SecretaryPersonalDraft | null>(null);
  readonly pendingDocuments = signal<readonly SecretaryPdf[]>([]);
  readonly pendingDocumentsLoaded = signal(false);
  readonly pendingDocumentsError = signal(false);
  readonly pendingReason = signal('');
  readonly pendingBusy = signal(false);
  readonly pendingUncertain = signal(false);
  readonly pendingNotice = signal<'approved' | 'returned' | 'failed' | 'uncertain' | null>(null);
  readonly pendingPreviewDocument = signal<SecretaryPdf | null>(null);
  private pendingDocumentVersion = 0;

  // Separate manager queue. An active manager appointment is mandatory; Admin
  // or secretary group membership alone must never expose these requests.
  readonly roundRequests = signal<readonly PendingRoundContinuation[]>([]);
  readonly roundTotal = signal(0);
  readonly roundOffset = signal(0);
  readonly roundLoadError = signal(false);
  readonly selectedRound = signal<PendingRoundContinuation | null>(null);
  readonly roundDetails = signal<CorrespondenceDetails | null>(null);
  readonly roundHistory = signal<readonly CorrespondenceHistoryEntry[]>([]);
  readonly roundReviewReady = signal(false);
  readonly roundReviewError = signal(false);
  readonly roundReturnReason = signal('');
  readonly roundBusy = signal(false);
  readonly roundUncertain = signal(false);
  readonly roundNotice = signal<'approved' | 'returned' | 'failed' | 'uncertain' | 'stale' | null>(null);
  readonly roundPreviewDocument = signal<CorrespondenceDetails['documents'][number] | null>(null);
  private roundReviewVersion = 0;
  readonly roundLabels = computed(() => this.translation.language() === 'ar' ? {
    heading: 'طلبات متابعة السكرتارية', pending: 'بانتظار موافقة المدير',
    request: 'طلب متابعة المراسلة', round: 'الجولة التالية',
    owner: 'مقدم الطلب', reason: 'مبرر المتابعة', submitted: 'تاريخ الطلب',
    attachments: 'مستندات المراسلة السابقة', noAttachments: 'لا توجد مستندات للعرض',
    history: 'السجل', noHistory: 'لا توجد إجراءات مسجلة',
    warning: 'الطلب الحالي يضم مبرر المتابعة فقط؛ إضافة مرفقات متابعة جديدة لم تُفعّل بعد.',
    approve: 'موافقة وبدء الجولة التالية', returning: 'إرجاع للتعديل',
    returnReason: 'سبب الإرجاع (إلزامي)',
    confirm: 'هل راجعت الطلب والمستندات؟ ستبدأ الجولة التالية مباشرةً ولا يمكن التراجع عن ذلك.',
    approved: 'تمت الموافقة وبدء الجولة التالية', returned: 'أُعيد الطلب للسكرتارية',
    failed: 'تعذر تنفيذ القرار؛ تحقق من حالة الطلب قبل إعادة المحاولة',
    uncertain: 'قد يكون القرار نُفّذ. تحقق من حالة المراسلة قبل إعادة المحاولة.',
    stale: 'تغير الطلب أو رقم الجولة. أعد تحميل القائمة.',
    loadingFailed: 'تعذر تحميل طلبات المتابعة',
    reviewFailed: 'تعذر التحقق من الطلب والمستندات. لا تعتمد قبل مراجعتها.',
    previous: 'السابق', next: 'التالي',
  } : {
    heading: 'Secretary follow-up approvals', pending: 'Awaiting manager decision',
    request: 'Continuation request', round: 'Next round',
    owner: 'Requested by', reason: 'Follow-up explanation', submitted: 'Requested on',
    attachments: 'Existing correspondence documents', noAttachments: 'No documents available',
    history: 'History', noHistory: 'No recorded actions',
    warning: 'This request currently contains a reason only. New follow-up PDF staging is not yet available.',
    approve: 'Approve and Continue', returning: 'Return for Revision',
    returnReason: 'Return reason (required)',
    confirm: 'Have you reviewed the request and existing documents? Approval starts the next round immediately and cannot be undone.',
    approved: 'Approved; next round started', returned: 'Returned to secretary',
    failed: 'Decision failed. Verify request state before retrying.',
    uncertain: 'Decision might have been committed. Verify state before retrying.',
    stale: 'The request or cycle changed. Refresh before acting.',
    loadingFailed: 'Could not load continuation requests',
    reviewFailed: 'Could not verify the request and documents. Do not approve before reviewing.',
    previous: 'Previous', next: 'Next',
  });

  readonly pendingLabels = computed(() => this.translation.language() === 'ar' ? {
    heading: 'بانتظار اعتماد المدير', waiting: 'بانتظار الاعتماد',
    approve: 'اعتماد وإرسال', returning: 'إرجاع للتعديل',
    reason: 'سبب الإرجاع (إلزامي)', documents: 'المرفقات',
    noDocuments: 'لا توجد مرفقات', reviewError: 'تعذر تحميل المرفقات. لا تعتمد قبل مراجعتها.',
    approvalSuccess: 'تم الاعتماد والإرسال', returnSuccess: 'تم الإرجاع للسكرتارية',
    failed: 'تعذر إتمام الإجراء. راجع حالة المعاملة قبل المحاولة مرة أخرى.',
    uncertain: 'نتيجة الإرسال غير مؤكدة. راجع حالة المعاملة قبل إعادة المحاولة.',
    loadError: 'تعذر تحميل اعتمادات السكرتارية',
    refresh: 'تحديث', previous: 'السابق', next: 'التالي',
  } : {
    heading: 'Secretary approvals', waiting: 'Awaiting your approval',
    approve: 'Approve and Send', returning: 'Return for Revision',
    reason: 'Return reason (required)', documents: 'Documents',
    noDocuments: 'No attachments', reviewError: 'Could not load PDFs. Review them before approving.',
    approvalSuccess: 'Approved and forwarded', returnSuccess: 'Returned to secretary',
    failed: 'Action failed. Verify the correspondence state before trying again.',
    uncertain: 'Submission outcome uncertain. Verify the correspondence state before retrying.',
    loadError: 'Could not load secretary approvals',
    refresh: 'Refresh', previous: 'Previous', next: 'Next',
  });


  readonly headings = computed(() => this.box() === 'inbox'
    ? this.translation.text().inbox
    : this.translation.text().sent);
  readonly labels = computed(() => this.translation.text().mailbox);

  readonly inboxView = signal<CorrespondenceInboxView>('needs_action');
  readonly inboxViews: readonly CorrespondenceInboxView[] =
    ['all', 'unread', 'needs_action', 'handled', 'archived'];
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
  readonly historyDrawerOpen = signal(false);
  private historyPreviousFocus: HTMLElement | null = null;
  readonly historyDrawerLabels = computed(() => this.translation.language() === 'ar' ? {
    open: 'عرض سجل المراسلة', close: 'إغلاق السجل',
    attachments: 'مرفقات الدورة الحالية',
    noRoundAttachments: 'لا توجد مرفقات مصنفة لهذه الدورة. المستندات الأقدم موجودة في قسم المستندات.',
    unclassified: 'مرفقات سابقة غير مصنفة',
    round: 'الدورة',
  } : {
    open: 'View history', close: 'Close history',
    attachments: 'Current round attachments',
    noRoundAttachments: 'No classified attachments for this round. Older files remain in Documents.',
    unclassified: 'Earlier unclassified attachments',
    round: 'Round',
  });
  // Metadata comes from Alfresco, not filenames or ordering in the audit trail.
  readonly currentRoundAttachments = computed(() => sourceDocumentsForRound(
    this.details()?.documents ?? [], this.details()?.currentWorkflowCycle,
  ));
  readonly sourceDocumentGroups = computed(() => groupSourceDocuments(this.details()?.documents ?? []));
  actorDisplayName(actor: { actorNameArabic: string | null; actorNameEnglish: string }): string {
    return this.translation.language() === 'ar'
      ? (actor.actorNameArabic?.trim() || actor.actorNameEnglish)
      : actor.actorNameEnglish;
  }
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
    !this.secretaryMode() && !this.forwarding() && !this.forwardUncertain() &&
    eligibleForInitialOfficeForward(
      this.box(),
      this.session.effectiveRole(),
      this.details()?.state ?? null,
      this.history(),
    ),
  );

  readonly canCompleteOfficeReview = computed(() => {
    if (this.box() !== 'inbox'
        || this.session.effectiveRole() !== 'PRESIDENT_OFFICE'
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
    && this.session.effectiveRole() === 'PRESIDENT');

  ngOnInit(): void {
    // The directory is display-only; a temporary directory failure must not
    // block authenticated mailbox access or document preview.
    void this.loadDepartments();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.inboxView.set(this.box() === 'inbox'
        ? inboxViewFromQuery(params.get('view')) : 'all');
      // Invalidate any in-flight page fetch when opening a notification.
      ++this.pageVersion;
      this.loading.set(false);
      void this.loadPage(0);
    });
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
    this.pendingOffset.set(0);
    this.roundOffset.set(0);
    this.mobileDetailsOpen.set(false);
    this.selectedId.set(null);
    // Invalidate the previous page request so switching filters while loading works.
    ++this.pageVersion;
    this.loading.set(false);
    void this.loadPage(0);
  }

  async loadPage(offset: number): Promise<void> {
    if (this.loading()) return;
    this.closeHistory();
    this.mobileDetailsOpen.set(false);
    const version = ++this.pageVersion;
    ++this.detailVersion;
    this.closePreview();
    this.closePendingPreview();
    this.closeShare();
    this.selectedPending.set(null);
    this.clearRoundSelection();
    this.pendingDocuments.set([]);
    this.pendingMetadata.set(null);
    this.pendingDocumentsLoaded.set(false);
    ++this.pendingDocumentVersion;
    this.loading.set(true);
    this.listError.set(false);
    this.details.set(null);
    this.detailsError.set(false);
    this.history.set(null);
    this.historyError.set(false);
    this.forwardUncertain.set(false);
    this.continueUncertain.set(false);
    try {
      const unit = this.secretaryUnitCode();
      const managerUnit = this.managerApprovalUnit();
      const showPending = !!managerUnit && (this.inboxView() === 'needs_action' || this.inboxView() === 'all');
      // Never hold the normal Inbox hostage to a potentially expensive
      // continuation-request scan. The manager queue is an independent load.
      void this.loadRoundQueue(managerUnit, showPending, version);
      const [page, pending] = await Promise.all([
        firstValueFrom(this.secretaryMode()
          ? this.box() === 'sent'
            ? this.api.listSecretarySent(unit!, offset, PAGE_SIZE)
            : this.api.listSecretaryInbox(unit!, offset, PAGE_SIZE, this.inboxView())
          : this.api.listMailbox(this.box(), offset, PAGE_SIZE, this.inboxView())),
        showPending ? firstValueFrom(this.secretaryApi.list(managerUnit!, 'pending',
          this.pendingOffset(), PAGE_SIZE)).then(result => ({ result, failed: false }))
          .catch(() => ({ result: null, failed: true }))
          : Promise.resolve({ result: null, failed: false }),
      ]);
      if (version !== this.pageVersion) return;
      this.pendingApprovals.set(pending.result?.items ?? []);
      this.pendingTotal.set(pending.result?.total ?? 0);
      this.pendingLoadError.set(pending.failed);
      this.page.set(page);
      const requestedId = this.box() === 'inbox'
        ? this.route.snapshot.queryParamMap.get('open') : null;
      const id = page.items.find(item => item.technicalId === requestedId)?.technicalId
        ?? page.items.find(item => item.technicalId === this.selectedId())?.technicalId
        ?? page.items[0]?.technicalId ?? null;
      // A manager's pending secretary item is an actual Inbox action, and
      // appears before ordinary correspondence in Needs Action / All.
      if (!requestedId && pending.result?.items.length) {
        this.selectPending(pending.result.items[0]);
      } else {
        this.selectedId.set(id);
        if (id) void this.loadDetails(id, id === requestedId &&
          !!page.items.find(item => item.technicalId === id)?.unread);
      }
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

  private async loadRoundQueue(managerUnit: string | null, eligible: boolean,
    version: number): Promise<void> {
    this.roundRequests.set([]);
    this.roundTotal.set(0);
    this.roundLoadError.set(false);
    if (!eligible || !managerUnit) return;
    try {
      const page = await firstValueFrom(this.roundApi.pending(managerUnit,
        this.roundOffset(), PAGE_SIZE));
      if (version !== this.pageVersion) return;
      this.roundRequests.set(page.items);
      this.roundTotal.set(page.total);
    } catch {
      if (version === this.pageVersion) this.roundLoadError.set(true);
    }
  }

  select(id: string): void {
    this.closePendingPreview();
    this.selectedPending.set(null);
    this.clearRoundSelection();
    this.pendingDocuments.set([]);
    this.pendingMetadata.set(null);
    this.pendingDocumentsLoaded.set(false);
    ++this.pendingDocumentVersion;
    this.closeHistory();
    this.mobileDetailsOpen.set(true);
    if (id === this.selectedId()) {
      if (this.details()?.technicalId === id) void this.acknowledgeRead(id);
      else void this.loadDetails(id, true);
      return;
    }
    this.closePreview();
    this.closeShare();
    this.continueNotice.set(null);
    this.selectedId.set(id);
    void this.loadDetails(id, true);
  }

  retryDetails(): void {
    const id = this.selectedId();
    if (id) void this.loadDetails(id);
  }

  selectPending(item: SecretaryDraft): void {
    if (!this.managerApprovalUnit() || this.managerApprovalUnit() !== item.unitCode
        || item.approvalStatus !== 'PENDING') return;
    this.closeHistory();
    this.closePreview();
    this.closeShare();
    this.closePendingPreview();
    this.clearRoundSelection();
    ++this.detailVersion;
    this.selectedId.set(null);
    this.details.set(null);
    this.history.set(null);
    this.mobileDetailsOpen.set(true);
    this.selectedPending.set(item);
    this.pendingReason.set('');
    this.pendingDocuments.set([]);
    this.pendingMetadata.set(null);
    this.pendingDocumentsLoaded.set(false);
    this.pendingDocumentsError.set(false);
    void this.loadPendingDocuments(item);
  }

  private async loadPendingDocuments(item: SecretaryDraft): Promise<void> {
    const version = ++this.pendingDocumentVersion;
    try {
      const [metadata, documents] = await Promise.all([
        firstValueFrom(this.secretaryApi.pendingDraft(item.unitCode, item.technicalId)),
        firstValueFrom(this.secretaryApi.documents(item.unitCode, item.technicalId, 'pending')),
      ]);
      if (version !== this.pendingDocumentVersion
          || this.selectedPending()?.technicalId !== item.technicalId) return;
      this.pendingMetadata.set(metadata);
      this.pendingDocuments.set(documents.items);
      this.pendingDocumentsLoaded.set(true);
    } catch {
      if (version === this.pendingDocumentVersion) this.pendingDocumentsError.set(true);
    }
  }

  updatePendingReason(event: Event): void {
    this.pendingReason.set((event.target as HTMLTextAreaElement).value);
  }

  async processPending(action: 'APPROVE_AND_SEND' | 'RETURN'): Promise<void> {
    const item = this.selectedPending();
    if (!item || item.approvalStatus !== 'PENDING'
        || item.unitCode !== this.managerApprovalUnit()
        || this.pendingBusy() || this.pendingUncertain()
        || !this.pendingDocumentsLoaded()) return;
    const reason = this.pendingReason().trim();
    if (action === 'RETURN' && !reason) return;
    this.pendingBusy.set(true);
    this.pendingNotice.set(null);
    try {
      const fields: Record<string, string | null> = { technicalId: item.technicalId };
      if (action === 'RETURN') fields['explanation'] = reason;
      await firstValueFrom(this.secretaryApi.change(item.unitCode, action, fields));
      this.pendingOffset.set(0);
      await this.loadPage(0);
      this.pendingNotice.set(action === 'RETURN' ? 'returned' : 'approved');
    } catch (error) {
      // Alfresco transaction may have committed despite a lost response.
      // Never silently retry either action.
      const uncertain = error instanceof HttpErrorResponse
        && (error.status === 0 || error.status >= 500);
      this.pendingUncertain.set(uncertain);
      this.pendingNotice.set(uncertain ? 'uncertain' : 'failed');
    } finally {
      this.pendingBusy.set(false);
    }
  }

  pendingPrevious(): void {
    if (this.pendingOffset() < PAGE_SIZE || this.loading()) return;
    this.pendingOffset.update(value => value - PAGE_SIZE);
    void this.loadPage(0);
  }

  pendingNext(): void {
    if (this.pendingOffset() + PAGE_SIZE >= this.pendingTotal() || this.loading()) return;
    this.pendingOffset.update(value => value + PAGE_SIZE);
    void this.loadPage(0);
  }

  openPendingDocument(document: SecretaryPdf): void {
    if (document.mimeType !== 'application/pdf' || !this.selectedPending()) return;
    this.pendingPreviewDocument.set(document);
  }

  closePendingPreview(): void { this.pendingPreviewDocument.set(null); }

  private clearRoundSelection(): void {
    ++this.roundReviewVersion;
    this.selectedRound.set(null);
    this.roundDetails.set(null);
    this.roundHistory.set([]);
    this.roundReviewReady.set(false);
    this.roundReviewError.set(false);
    this.roundPreviewDocument.set(null);
    this.roundReturnReason.set('');
  }

  selectRound(item: PendingRoundContinuation): void {
    if (this.box() !== 'inbox' || this.managerApprovalUnit() !== item.unitCode
        || this.roundBusy()) return;
    this.closeHistory();
    this.closePreview();
    this.closePendingPreview();
    this.closeShare();
    ++this.pendingDocumentVersion;
    this.selectedPending.set(null);
    this.clearRoundSelection();
    ++this.detailVersion;
    this.selectedId.set(null);
    this.details.set(null);
    this.history.set(null);
    this.mobileDetailsOpen.set(true);
    this.selectedRound.set(item);
    void this.loadRoundReview(item);
  }

  private async loadRoundReview(item: PendingRoundContinuation): Promise<void> {
    const version = ++this.roundReviewVersion;
    try {
      // Use the manager's existing correspondence authorization, even if this
      // employee also happens to have a secretary assignment in another unit.
      const [current, details, history] = await Promise.all([
        firstValueFrom(this.roundApi.current(item.unitCode, item.technicalId)),
        firstValueFrom(this.api.getDetails(item.technicalId)),
        firstValueFrom(this.api.getHistory(item.technicalId)),
      ]);
      if (version !== this.roundReviewVersion
          || this.selectedRound()?.requestId !== item.requestId) return;
      if (current.status !== 'PENDING' || current.requestId !== item.requestId
          || current.expectedCycle !== item.expectedCycle
          || current.ownerEmployeeId !== item.ownerEmployeeId
          || details.currentWorkflowCycle !== item.expectedCycle
          || details.state !== 'WITH_DIRECTOR') {
        this.roundReviewError.set(true);
        return;
      }
      this.roundDetails.set(details);
      this.roundHistory.set(history.items);
      this.roundReviewReady.set(true);
    } catch {
      if (version === this.roundReviewVersion) this.roundReviewError.set(true);
    }
  }

  updateRoundReturnReason(event: Event): void {
    this.roundReturnReason.set((event.target as HTMLTextAreaElement).value);
  }

  async processRound(action: 'APPROVE_AND_CONTINUE' | 'RETURN'): Promise<void> {
    const item = this.selectedRound();
    const reason = this.roundReturnReason().trim();
    if (!item || this.managerApprovalUnit() !== item.unitCode
        || !this.roundReviewReady() || this.roundReviewError()
        || this.roundBusy() || this.roundUncertain()
        || (action === 'RETURN' && (!reason || reason.length > 2000))) return;
    if (action === 'APPROVE_AND_CONTINUE' && !window.confirm(this.roundLabels().confirm)) return;
    this.roundBusy.set(true);
    this.roundNotice.set(null);
    try {
      await firstValueFrom(this.roundApi.decide(item.unitCode, item.technicalId,
        item.requestId, action, action === 'RETURN' ? reason : undefined));
      // Invalidate the decided request before refreshing. The new queue may
      // auto-select another pending request; a busy guard must not block it.
      this.clearRoundSelection();
      this.roundBusy.set(false);
      this.roundOffset.set(0);
      await this.loadPage(0);
      this.roundNotice.set(action === 'RETURN' ? 'returned' : 'approved');
    } catch (error: unknown) {
      const uncertain = error instanceof HttpErrorResponse
        && (error.status === 0 || error.status >= 500);
      const stale = error instanceof HttpErrorResponse && error.status === 409;
      this.roundUncertain.set(uncertain);
      this.roundNotice.set(uncertain ? 'uncertain' : stale ? 'stale' : 'failed');
      if (stale) this.roundReviewReady.set(false);
    } finally {
      this.roundBusy.set(false);
    }
  }

  roundPrevious(): void {
    if (this.roundOffset() < PAGE_SIZE || this.loading() || this.roundBusy()) return;
    this.roundOffset.update(value => value - PAGE_SIZE);
    void this.loadPage(0);
  }

  roundNext(): void {
    if (this.roundOffset() + PAGE_SIZE >= this.roundTotal()
        || this.loading() || this.roundBusy()) return;
    this.roundOffset.update(value => value + PAGE_SIZE);
    void this.loadPage(0);
  }

  openRoundDocument(document: CorrespondenceDetails['documents'][number]): void {
    if (!this.roundReviewReady() || document.mimeType !== 'application/pdf') return;
    this.roundPreviewDocument.set(document);
  }

  closeRoundPreview(): void { this.roundPreviewDocument.set(null); }



  private async loadDetails(id: string, openedByUser = false): Promise<void> {
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
      const unit = this.secretaryUnitCode();
      const result = await firstValueFrom(this.secretaryMode()
        ? this.box() === 'sent'
          ? this.api.getSecretarySentDetails(unit!, id)
          : this.api.getSecretaryInboxDetails(unit!, id)
        : this.api.getDetails(id));
      if (version !== this.detailVersion || this.selectedId() !== id) return;
      this.details.set(result);
      this.detailsLoading.set(false);
      if (openedByUser) void this.acknowledgeRead(id);
      // Assignment API is independent of correspondence details/history.
      if (!this.secretaryMode()) {
        try {
          const assigned = await firstValueFrom(this.api.getDepartmentAssignments(id));
          if (version === this.detailVersion && this.selectedId() === id) {
            this.assignments.set(assigned.items);
          }
        } catch {
          // Existing correspondence may be pre-distribution; keep its PDFs readable.
        }
      }
      // History failure must never block correspondence details or PDF viewing.
      try {
        const actionPage = await firstValueFrom(this.secretaryMode()
          ? this.box() === 'sent'
            ? this.api.getSecretarySentHistory(unit!, id)
            : this.api.getSecretaryInboxHistory(unit!, id)
          : this.api.getHistory(id));
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

  private async acknowledgeRead(id: string): Promise<void> {
    // Secretary read receipts do not yet have a separate authorized write API.
    if (this.box() !== 'inbox' || this.secretaryMode()) return;
    const item = this.page()?.items.find(value => value.technicalId === id);
    if (!item?.unread || !item.incomingSequence) return;
    try {
      const marked = await firstValueFrom(this.api.markRead(id, item.incomingSequence));
      if (marked.readThroughSequence < item.incomingSequence) return;
      this.page.update(page => page ? {
        ...page,
        items: page.items.map(value => value.technicalId === id &&
          value.incomingSequence <= marked.readThroughSequence
          ? { ...value, unread: false } : value),
      } : page);
      void this.unreadStore.refresh();
    } catch {
      // Keep Unread visible if the receipt could not be persisted.
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
    if (this.secretaryMode()) {
      void this.router.navigate(['/secretary/new']);
      return;
    }
    void this.router.navigate([composeRouteForRole(this.session.effectiveRole())]);
  }

  openDocument(document: CorrespondenceDetails['documents'][number]): void {
    if (document.mimeType !== 'application/pdf' || !this.details()) {
      return;
    }
    this.previewDocument.set(document);
  }


  openHistory(): void {
    if (!this.selectedId() || this.historyDrawerOpen()) return;
    this.historyPreviousFocus = globalThis.document?.activeElement as HTMLElement | null;
    this.historyDrawerOpen.set(true);
    setTimeout(() => globalThis.document?.getElementById('mailbox-history-close')?.focus(), 0);
  }

  closeHistory(): void {
    if (!this.historyDrawerOpen()) return;
    this.historyDrawerOpen.set(false);
    const previous = this.historyPreviousFocus;
    this.historyPreviousFocus = null;
    setTimeout(() => previous?.isConnected && previous.focus(), 0);
  }

  onHistoryKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeHistory();
    } else if (event.key === 'Tab') {
      // The close button is the only keyboard action inside this read-only drawer.
      // Keep focus inside the modal on desktop, iPad keyboards, and accessibility tools.
      const close = globalThis.document?.getElementById('mailbox-history-close');
      if (close) {
        event.preventDefault();
        close.focus();
      }
    }
  }

  backToMailbox(): void {
    this.closeHistory();
    this.closeShare();
    this.closePreview();
    this.mobileDetailsOpen.set(false);
  }

  beginShare(document: CorrespondenceDetails['documents'][number]): void {
    if (this.secretaryMode() || document.mimeType !== 'application/pdf' || !this.details()) return;
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
        || this.session.effectiveRole() !== 'DIRECTOR') return null;
    const code = this.session.effectiveDepartmentCode();
    return this.assignments().find(item => item.departmentCode === code
      && item.recipientType === 'TO' && item.status === 'PENDING') ?? null;
  });

  readonly canDirectorFollowUp = computed(() => {
    if (this.box() !== 'inbox' || this.details()?.state !== 'WITH_DIRECTOR'
        || this.session.effectiveRole() !== 'DIRECTOR') return false;
    const department = this.session.effectiveDepartmentCode();
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
      // Stage evidence under the NEXT cycle, without silently assigning it to
      // the previous round. The backend deduplicates an identical retry.
      for (const file of this.followUpFiles()) {
        await firstValueFrom(this.api.uploadDocument(id, file, true));
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
    && this.session.effectiveRole() === 'PRESIDENT_OFFICE'
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

  secretarySentLabel(item: CorrespondenceMailboxPage['items'][number]): string {
    if (item.approvalStatus === 'PENDING') return this.translation.language() === 'ar'
      ? 'بانتظار موافقة المدير' : 'Awaiting manager approval';
    return this.statusLabel(item.state);
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
