import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { UnreadInboxStore } from '../../../core/notifications/unread-inbox.store';
import { CorrespondenceService } from '../../../core/correspondence/correspondence.service';
import { TranslationService } from '../../../core/i18n/translation.service';
import { TarasolSecretaryApiService } from '../../../features/tarasol-secretary/tarasol-secretary-api.service';
import { TarasolRoundContinuationApiService } from '../../../features/tarasol-secretary/tarasol-round-continuation-api.service';
import { CorrespondenceApiService } from '../../data-access/correspondence-api.service';
import { RealMailboxComponent } from './real-mailbox.component';

const technicalId = '7fc41b5b-aceb-48fc-9f36-367e4a36e207';
const requestId = '5fc41b5b-aceb-48fc-9f36-367e4a36e207';
const item = { technicalId, requestId, unitCode: '117', ownerEmployeeId: '300',
  subject: 'Round N follow-up', documentNumber: '117/2026/0042',
  reason: 'Please review further evidence', expectedCycle: 3, requestedAt: '2026-11-04T10:00:00Z' };
const managerWorkspace = { code: '117', nameEnglish: 'Digital Transformation', nameArabic: null,
  role: 'DIRECTOR' as const, capacity: 'MANAGER' as const };

describe('Manager Round N continuation requests in the existing Inbox', () => {
  let mailbox: RealMailboxComponent;
  let rounds: {
    pending: ReturnType<typeof vi.fn>;
    current: ReturnType<typeof vi.fn>;
    decide: ReturnType<typeof vi.fn>;
    managerDocuments: ReturnType<typeof vi.fn>;
    managerPreview: ReturnType<typeof vi.fn>;
  };
  let correspondence: {
    listMailbox: ReturnType<typeof vi.fn>;
    listSecretaryInbox: ReturnType<typeof vi.fn>;
    getDetails: ReturnType<typeof vi.fn>;
    getHistory: ReturnType<typeof vi.fn>;
  };
  let session: {
    secretaryMailbox: () => boolean;
    secretaryInboxUnitCode: () => string | null;
    activeManagerWorkspace: () => typeof managerWorkspace | null;
    effectiveDepartmentCode: () => string;
    availableWorkspaces: () => typeof managerWorkspace[];
    effectiveRole: () => string;
  };

  beforeEach(() => {
    rounds = {
      pending: vi.fn(() => of({ items: [item], total: 1, offset: 0, limit: 10 })),
      current: vi.fn(() => of({ requestId, expectedCycle: 3,
        ownerEmployeeId: '300', reason: item.reason, status: 'PENDING' })),
      decide: vi.fn(() => of({ requestId, status: 'APPROVED', currentWorkflowCycle: 4 })),
      managerDocuments: vi.fn(() => of({ documentsRevision: 0, items: [] })),
      managerPreview: vi.fn(() => of(new Blob(['%PDF-1.5'], { type: 'application/pdf' }))),
    };
    correspondence = {
      listMailbox: vi.fn(() => of({ items: [], total: 0, offset: 0, limit: 10 })),
      listSecretaryInbox: vi.fn(() => of({ items: [], total: 0, offset: 0, limit: 10 })),
      getDetails: vi.fn(() => of({ technicalId, state: 'WITH_DIRECTOR', currentWorkflowCycle: 3,
        subject: item.subject, bodyText: 'Existing correspondence', documents: [], generatedDocuments: [] })),
      getHistory: vi.fn(() => of({ items: [], total: 0 })),
    };
    session = {
      secretaryMailbox: () => false,
      secretaryInboxUnitCode: () => null,
      activeManagerWorkspace: () => managerWorkspace,
      effectiveDepartmentCode: () => '117',
      availableWorkspaces: () => [managerWorkspace],
      effectiveRole: () => 'DIRECTOR',
    };
    TestBed.configureTestingModule({ providers: [
      { provide: AuthSessionStore, useValue: session },
      { provide: TarasolSecretaryApiService, useValue: {
        list: vi.fn(() => of({ items: [], total: 0, offset: 0, limit: 10 })) } },
      { provide: TarasolRoundContinuationApiService, useValue: rounds },
      { provide: CorrespondenceApiService, useValue: correspondence },
      { provide: CorrespondenceService, useValue: { getDepartments: vi.fn(() => of([])) } },
      { provide: TranslationService, useValue: { language: () => 'en' } },
      { provide: Router, useValue: { navigate: vi.fn() } },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
      { provide: UnreadInboxStore, useValue: {} },
    ] });
    mailbox = TestBed.runInInjectionContext(() => new RealMailboxComponent());
    Object.defineProperty(mailbox, 'box', { value: () => 'inbox' });
  });

  async function selectPendingRound(): Promise<void> {
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.roundRequests().length).toBe(1));
    mailbox.selectRound(item);
    await vi.waitFor(() => expect(mailbox.roundReviewReady() || mailbox.roundReviewError()).toBe(true));
  }


  it('includes separately staged Round 4 PDFs in the manager review without altering existing documents', async () => {
    const pdf = { nodeId: 'staged-pdf-id', filename: 'Round4.pdf',
      mimeType: 'application/pdf' as const, sizeBytes: 891, workflowCycle: 4 };
    rounds.managerDocuments.mockReturnValue(of({ documentsRevision: 2, items: [pdf] }));
    await selectPendingRound();
    expect(mailbox.roundReviewReady()).toBe(true);
    expect(mailbox.roundStagedDocuments()).toEqual([pdf]);
    expect(mailbox.roundStagedRevision()).toBe(2);
    mailbox.openRoundStagedDocument(pdf);
    expect(mailbox.roundStagedPreview()?.nodeId).toBe(pdf.nodeId);
    expect(correspondence.getDetails).toHaveBeenCalledWith(technicalId);
    expect(mailbox.roundDetails()?.documents).toEqual([]);
  });

  it('does not approve if the manager cannot list the staged PDFs', async () => {
    rounds.managerDocuments.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    await selectPendingRound();
    expect(mailbox.roundReviewError()).toBe(true);
    expect(mailbox.roundStagedLoaded()).toBe(false);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    await mailbox.processRound('APPROVE_AND_CONTINUE');
    expect(rounds.decide).not.toHaveBeenCalled();
    confirm.mockRestore();
  });

  it('rejects a PDF staged for the wrong cycle and prevents manager approval', async () => {
    rounds.managerDocuments.mockReturnValue(of({ documentsRevision: 0, items: [
      { nodeId: 'staged-pdf-id', filename: 'wrong.pdf', mimeType: 'application/pdf',
        sizeBytes: 300, workflowCycle: 3 },
    ] }));
    await selectPendingRound();
    expect(mailbox.roundReviewError()).toBe(true);
    expect(mailbox.roundReviewReady()).toBe(false);
    expect(rounds.decide).not.toHaveBeenCalled();
  });

  it('rechecks the staged PDF set before approving and blocks unreviewed uploads', async () => {
    await selectPendingRound();
    rounds.managerDocuments.mockReturnValue(of({ documentsRevision: 0, items: [
      { nodeId: 'new-document', filename: 'late.pdf', mimeType: 'application/pdf',
        workflowCycle: 4, sizeBytes: 200 },
    ] }));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    await mailbox.processRound('APPROVE_AND_CONTINUE');
    expect(rounds.decide).not.toHaveBeenCalled();
    expect(mailbox.roundNotice()).toBe('stale');
    expect(mailbox.roundReviewReady()).toBe(false);
    confirm.mockRestore();
  });

  it('rejects a changed revision even when filenames and PDF IDs look identical', async () => {
    await selectPendingRound();
    rounds.managerDocuments.mockReturnValue(of({ documentsRevision: 1, items: [] }));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    await mailbox.processRound('APPROVE_AND_CONTINUE');
    expect(rounds.decide).not.toHaveBeenCalled();
    expect(mailbox.roundNotice()).toBe('stale');
    confirm.mockRestore();
  });

  it('rejects manager document listings without an authoritative revision', async () => {
    rounds.managerDocuments.mockReturnValue(of({ items: [] }));
    await selectPendingRound();
    expect(mailbox.roundReviewReady()).toBe(false);
    expect(mailbox.roundReviewError()).toBe(true);
  });

  it('ignores PDF responses from an old manager selection', async () => {
    const delayed = new Subject<{ documentsRevision: number; items: { nodeId: string; filename: string;
      mimeType: 'application/pdf'; sizeBytes: number; workflowCycle: number }[] }>();
    rounds.managerDocuments.mockReturnValue(delayed.asObservable());
    await mailbox.loadPage(0);
    mailbox.selectRound(item);
    mailbox.selectRound({ ...item, requestId: 'different-request', expectedCycle: 3 });
    expect(mailbox.roundStagedLoaded()).toBe(false);
    delayed.next({ documentsRevision: 0, items: [{ nodeId: 'old', filename: 'old.pdf', mimeType: 'application/pdf', sizeBytes: 10, workflowCycle: 4 }] });
    delayed.complete();
    await vi.waitFor(() => expect(mailbox.roundReviewError()).toBe(true));
    expect(mailbox.roundReviewReady()).toBe(false);
    expect(mailbox.roundStagedDocuments()).toEqual([]);
  });

  it('does not block ordinary Inbox loading while the Round N queue is slow', async () => {
    const slow = new Subject<{ items: typeof item[]; total: number; offset: number; limit: number }>();
    rounds.pending.mockReturnValue(slow.asObservable());
    await mailbox.loadPage(0);
    expect(mailbox.loading()).toBe(false);
    expect(mailbox.listError()).toBe(false);
    expect(mailbox.page()?.total).toBe(0);
    slow.next({ items: [item], total: 1, offset: 0, limit: 10 });
    slow.complete();
    await vi.waitFor(() => expect(mailbox.roundRequests().length).toBe(1));
  });

  it('discovers manager requests and rechecks the current cycle before allowing approval', async () => {
    await selectPendingRound();
    expect(mailbox.roundReviewReady()).toBe(true);
    expect(rounds.pending).toHaveBeenCalledWith('117', 0, 10);
    expect(rounds.current).toHaveBeenCalledWith('117', technicalId);
    expect(correspondence.getDetails).toHaveBeenCalledWith(technicalId);
    expect(correspondence.getHistory).toHaveBeenCalledWith(technicalId);
    expect(rounds.managerDocuments).toHaveBeenCalledWith('117', technicalId, requestId);
    expect(mailbox.roundStagedLoaded()).toBe(true);
    expect(mailbox.selectedRound()?.requestId).toBe(requestId);
  });

  it('never fetches a manager queue without active manager authority', async () => {
    session.activeManagerWorkspace = () => null;
    session.availableWorkspaces = () => [];
    await mailbox.loadPage(0);
    expect(rounds.pending).not.toHaveBeenCalled();
  });

  it('rejects a stale cycle before offering an approval action', async () => {
    rounds.current.mockReturnValue(of({ requestId, expectedCycle: 2,
      ownerEmployeeId: '300', reason: item.reason, status: 'PENDING' }));
    await selectPendingRound();
    expect(mailbox.roundReviewError()).toBe(true);
    await mailbox.processRound('APPROVE_AND_CONTINUE');
    expect(rounds.decide).not.toHaveBeenCalled();
  });

  it('returns with a reason and never invokes Director continuation directly', async () => {
    await selectPendingRound();
    expect(mailbox.roundReviewReady()).toBe(true);
    await mailbox.processRound('RETURN');
    expect(rounds.decide).not.toHaveBeenCalled();
    mailbox.roundReturnReason.set('Please correct the summary');
    rounds.pending.mockReturnValue(of({ items: [], total: 0, offset: 0, limit: 10 }));
    await mailbox.processRound('RETURN');
    expect(rounds.decide).toHaveBeenCalledWith('117', technicalId, requestId, 'RETURN',
      'Please correct the summary', undefined);
  });

  it('blocks repeat approval on uncertain HTTP 500 result', async () => {
    await selectPendingRound();
    expect(mailbox.roundReviewReady()).toBe(true);
    rounds.decide.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    await mailbox.processRound('APPROVE_AND_CONTINUE');
    expect(mailbox.roundNotice()).toBe('uncertain');
    await mailbox.processRound('APPROVE_AND_CONTINUE');
    expect(rounds.decide).toHaveBeenCalledOnce();
    vi.restoreAllMocks();
  });

  it('requires explicit confirmation before committing the next round', async () => {
    await selectPendingRound();
    expect(mailbox.roundReviewReady()).toBe(true);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    await mailbox.processRound('APPROVE_AND_CONTINUE');
    expect(rounds.decide).not.toHaveBeenCalled();
    confirm.mockRestore();
  });

  it('shows a cycle conflict and disables the stale approval', async () => {
    await selectPendingRound();
    expect(mailbox.roundReviewReady()).toBe(true);
    rounds.decide.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    await mailbox.processRound('APPROVE_AND_CONTINUE');
    expect(mailbox.roundNotice()).toBe('stale');
    expect(mailbox.roundReviewReady()).toBe(false);
    await mailbox.processRound('APPROVE_AND_CONTINUE');
    expect(rounds.decide).toHaveBeenCalledOnce();
    confirm.mockRestore();
  });

  it('does not approve when original correspondence details cannot be verified', async () => {
    correspondence.getDetails.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    await selectPendingRound();
    expect(mailbox.roundReviewError()).toBe(true);
    await mailbox.processRound('APPROVE_AND_CONTINUE');
    expect(rounds.decide).not.toHaveBeenCalled();
  });

  it('refreshes the manager queue after approved continuation without repeating the decision', async () => {
    await selectPendingRound();
    expect(mailbox.roundReviewReady()).toBe(true);
    rounds.pending.mockReturnValue(of({ items: [], total: 0, offset: 0, limit: 10 }));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    await mailbox.processRound('APPROVE_AND_CONTINUE');
    expect(rounds.decide).toHaveBeenCalledOnce();
    expect(rounds.decide).toHaveBeenCalledWith('117', technicalId, requestId,
      'APPROVE_AND_CONTINUE', undefined, 0);
    expect(mailbox.roundRequests()).toEqual([]);
    expect(mailbox.selectedRound()).toBeNull();
    expect(mailbox.roundNotice()).toBe('approved');
    confirm.mockRestore();
  });

  it('preserves manager approvals even when the manager also has a secretary role', async () => {
    session.secretaryMailbox = () => true;
    session.secretaryInboxUnitCode = () => '117';
    // The ordinary mailbox uses secretary scope, but manager review requires manager APIs.
    correspondence.listMailbox.mockReturnValue(of({ items: [], total: 0, offset: 0, limit: 10 }));
    await mailbox.loadPage(0);
    expect(rounds.pending).toHaveBeenCalledWith('117', 0, 10);
  });
});
