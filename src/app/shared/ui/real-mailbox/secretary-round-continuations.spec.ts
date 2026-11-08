import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
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
const inboxItem = { technicalId, subject: 'Digital Transformation follow-up',
  state: 'WITH_DIRECTOR', modifiedAt: '2026-11-04T09:00:00Z',
  senderName: 'Director', documentNumber: '117/2026/42', unread: false };
const pending = { technicalId, requestId, unitCode: '117',
  status: 'PENDING' as const, expectedCycle: 3, returnVersion: 0,
  reason: 'We need another round', returnReason: null,
  requestedAt: '2026-11-04T09:00:00Z' };
const preparing = { ...pending, status: 'PREPARING' as const };
const returned = { ...pending, status: 'RETURNED' as const, returnVersion: 2,
  returnReason: 'Please replace the wrong PDF' };
const pdf = { nodeId: 'stage-1', filename: 'round-4.pdf',
  mimeType: 'application/pdf' as const, sizeBytes: 12, workflowCycle: 4 };

describe('Secretary Round N continuation in the existing Inbox', () => {
  let mailbox: RealMailboxComponent;
  let rounds: {
    secretaryEligibility: ReturnType<typeof vi.fn>;
    secretaryRequest: ReturnType<typeof vi.fn>;
    secretaryDocuments: ReturnType<typeof vi.fn>;
    submitSecretaryRequest: ReturnType<typeof vi.fn>;
    submitPreparedSecretaryRequest: ReturnType<typeof vi.fn>;
    reviseSecretaryRequest: ReturnType<typeof vi.fn>;
    resubmitSecretaryRequest: ReturnType<typeof vi.fn>;
    uploadSecretaryDocument: ReturnType<typeof vi.fn>;
    deleteSecretaryDocument: ReturnType<typeof vi.fn>;
  };
  let correspondence: {
    listSecretaryInbox: ReturnType<typeof vi.fn>;
    getSecretaryInboxDetails: ReturnType<typeof vi.fn>;
    getSecretaryInboxHistory: ReturnType<typeof vi.fn>;
    markSecretaryInboxRead: ReturnType<typeof vi.fn>;
    markRead: ReturnType<typeof vi.fn>;
    continueDirectorCycle: ReturnType<typeof vi.fn>;
    transitionCorrespondence: ReturnType<typeof vi.fn>;
  };
  let secretary: { secretaryMailbox: () => boolean; secretaryInboxUnitCode: () => string | null;
    activeManagerWorkspace: () => null; availableWorkspaces: () => never[];
    effectiveDepartmentCode: () => string; effectiveRole: () => string };

  beforeEach(() => {
    rounds = {
      secretaryEligibility: vi.fn(() => of({ canPrepare: false, canReadRequest: true,
        requestStatus: 'RETURNED', nextCycle: 4, reasonCode: 'OWN_REQUEST' })),
      secretaryRequest: vi.fn(() => of(returned)),
      secretaryDocuments: vi.fn(() => of({ items: [pdf] })),
      submitSecretaryRequest: vi.fn(() => of(preparing)),
      submitPreparedSecretaryRequest: vi.fn(() => of(pending)),
      reviseSecretaryRequest: vi.fn(() => of({ ...returned, reason: 'Corrected follow-up' })),
      resubmitSecretaryRequest: vi.fn(() => of({ ...returned, status: 'PENDING' as const })),
      uploadSecretaryDocument: vi.fn(() => of(pdf)),
      deleteSecretaryDocument: vi.fn(() => of(undefined)),
    };
    correspondence = {
      listSecretaryInbox: vi.fn(() => of({ items: [inboxItem], total: 1, offset: 0, limit: 10 })),
      getSecretaryInboxDetails: vi.fn(() => of({ technicalId, subject: inboxItem.subject,
        state: 'WITH_DIRECTOR', currentWorkflowCycle: 3, bodyText: 'Existing text',
        documents: [], generatedDocuments: [] })),
      getSecretaryInboxHistory: vi.fn(() => of({ items: [], total: 0 })),
      markSecretaryInboxRead: vi.fn(() => of({ readThroughSequence: 7 })),
      markRead: vi.fn(),
      continueDirectorCycle: vi.fn(), transitionCorrespondence: vi.fn(),
    };
    secretary = {
      secretaryMailbox: () => true,
      secretaryInboxUnitCode: () => '117',
      activeManagerWorkspace: () => null, availableWorkspaces: () => [],
      effectiveDepartmentCode: () => '117', effectiveRole: () => 'SECRETARY',
    };
    TestBed.configureTestingModule({ providers: [
      { provide: AuthSessionStore, useValue: secretary },
      { provide: TarasolRoundContinuationApiService, useValue: rounds },
      { provide: TarasolSecretaryApiService, useValue: {} },
      { provide: CorrespondenceApiService, useValue: correspondence },
      { provide: CorrespondenceService, useValue: { getDepartments: vi.fn(() => of([])) } },
      { provide: TranslationService, useValue: { language: () => 'en' } },
      { provide: Router, useValue: { navigate: vi.fn() } },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
      { provide: UnreadInboxStore, useValue: { refresh: vi.fn() } },
    ] });
    mailbox = TestBed.runInInjectionContext(() => new RealMailboxComponent());
    Object.defineProperty(mailbox, 'box', { value: () => 'inbox' });
  });

  async function loadReturned(): Promise<void> {
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.secretaryRoundRequest()?.status).toBe('RETURNED'));
    expect(mailbox.secretaryRoundDocumentsLoaded()).toBe(true);
  }

  async function loadPending(): Promise<void> {
    rounds.secretaryEligibility.mockReturnValue(of({ canPrepare: false, canReadRequest: true,
      requestStatus: 'PENDING', nextCycle: 4, reasonCode: 'OWN_REQUEST' }));
    rounds.secretaryRequest.mockReturnValue(of(pending));
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.secretaryRoundRequest()?.status).toBe('PENDING'));
    expect(mailbox.secretaryRoundDocumentsLoaded()).toBe(true);
  }

  it('marks an explicitly opened secretary Inbox correspondence read without Director authorization', async () => {
    correspondence.listSecretaryInbox.mockReturnValue(of({
      items: [{ ...inboxItem, unread: true, incomingSequence: 7 }],
      total: 1, offset: 0, limit: 10,
    }));
    await mailbox.loadPage(0);
    mailbox.select(technicalId);
    await vi.waitFor(() => expect(mailbox.page()?.items[0]?.unread).toBe(false));
    expect(correspondence.markSecretaryInboxRead).toHaveBeenCalledWith('117', technicalId, 7);
    expect(correspondence.markRead).not.toHaveBeenCalled();
  });

  it('keeps unread visible if the secretary read receipt is rejected', async () => {
    correspondence.listSecretaryInbox.mockReturnValue(of({
      items: [{ ...inboxItem, unread: true, incomingSequence: 7 }],
      total: 1, offset: 0, limit: 10,
    }));
    correspondence.markSecretaryInboxRead.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    await mailbox.loadPage(0);
    mailbox.select(technicalId);
    await vi.waitFor(() => expect(correspondence.markSecretaryInboxRead).toHaveBeenCalledOnce());
    expect(mailbox.page()?.items[0]?.unread).toBe(true);
    expect(correspondence.markRead).not.toHaveBeenCalled();
  });

  it('shows only the secretary-owned returned request, its manager reason and staged Round 4 PDFs', async () => {
    await loadReturned();
    expect(mailbox.canSecretaryRound()).toBe(true);
    expect(rounds.secretaryRequest).toHaveBeenCalledWith('117', technicalId);
    expect(rounds.secretaryDocuments).toHaveBeenCalledWith('117', technicalId, requestId);
    expect(mailbox.secretaryRoundRequest()?.returnReason).toBe(returned.returnReason);
    expect(mailbox.secretaryRoundDocuments()).toEqual([pdf]);
    mailbox.openSecretaryRoundPdf(pdf);
    expect(mailbox.secretaryRoundPreview()?.nodeId).toBe(pdf.nodeId);
    expect(mailbox.details()?.documents).toEqual([]);
  });

  it('saves a returned explanation without modifying the workflow or sending to a manager yet', async () => {
    await loadReturned();
    mailbox.secretaryRoundReason.set('Corrected follow-up');
    await mailbox.saveSecretaryRoundRevision();
    expect(rounds.reviseSecretaryRequest).toHaveBeenCalledWith('117', technicalId,
      requestId, 2, 'Corrected follow-up');
    expect(rounds.resubmitSecretaryRequest).not.toHaveBeenCalled();
    expect(mailbox.secretaryRoundNotice()).toBe('saved');
    expect(correspondence.continueDirectorCycle).not.toHaveBeenCalled();
  });

  it('revises first and then resubmits the same return version; never invokes Director continuation', async () => {
    await loadReturned();
    mailbox.secretaryRoundReason.set('Corrected follow-up');
    rounds.secretaryRequest.mockReturnValue(of({ ...returned, reason: 'Corrected follow-up', status: 'PENDING' }));
    rounds.secretaryEligibility.mockReturnValue(of({ canPrepare: false, canReadRequest: true,
      requestStatus: 'PENDING', nextCycle: 4, reasonCode: 'OWN_REQUEST' }));
    await mailbox.resubmitSecretaryRound();
    expect(rounds.reviseSecretaryRequest).toHaveBeenCalledWith('117', technicalId,
      requestId, 2, 'Corrected follow-up');
    expect(rounds.resubmitSecretaryRequest).toHaveBeenCalledWith('117', technicalId, requestId, 2);
    expect(mailbox.secretaryRoundRequest()?.status).toBe('PENDING');
    expect(mailbox.secretaryRoundNotice()).toBe('resubmitted');
    expect(correspondence.continueDirectorCycle).not.toHaveBeenCalled();
    expect(correspondence.transitionCorrespondence).not.toHaveBeenCalled();
  });

  it('uploads a validated staged PDF to its exact request, never into official documents', async () => {
    await loadReturned();
    const file = new File(['%PDF-1.7\n'], 'replacement.pdf', { type: 'application/pdf' });
    const input = { files: [file], value: 'replacement.pdf' } as unknown as HTMLInputElement;
    rounds.secretaryDocuments.mockReturnValue(of({ items: [pdf] }));
    await mailbox.uploadSecretaryRoundFiles({ target: input } as unknown as Event);
    expect(rounds.uploadSecretaryDocument).toHaveBeenCalledWith('117', technicalId, requestId, file);
    expect(input.value).toBe('');
    expect(mailbox.secretaryRoundNotice()).toBe('uploaded');
  });

  it('deletes only an explicitly selected staged PDF and reloads the list', async () => {
    await loadReturned();
    rounds.secretaryDocuments.mockReturnValue(of({ items: [] }));
    await mailbox.deleteSecretaryRoundFile(pdf);
    expect(rounds.deleteSecretaryDocument).toHaveBeenCalledWith('117', technicalId, requestId, pdf.nodeId);
    expect(mailbox.secretaryRoundDocuments()).toEqual([]);
    expect(mailbox.secretaryRoundNotice()).toBe('deleted');
  });

  it('prepares and explicitly submits only when PDF editing is complete', async () => {
    rounds.secretaryEligibility.mockReturnValue(of({ canPrepare: false, canReadRequest: true,
      requestStatus: 'PREPARING', nextCycle: 4, reasonCode: 'OWN_REQUEST' }));
    rounds.secretaryRequest.mockReturnValue(of(preparing));
    rounds.reviseSecretaryRequest.mockReturnValue(of({ ...preparing, reason: 'Fixed draft' }));
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.secretaryRoundRequest()?.status).toBe('PREPARING'));
    expect(mailbox.secretaryRoundDocumentsLoaded()).toBe(true);
    mailbox.secretaryRoundReason.set('Fixed draft');
    rounds.secretaryRequest.mockReturnValue(of({ ...pending, reason: 'Fixed draft' }));
    rounds.secretaryEligibility.mockReturnValue(of({ canPrepare: false, canReadRequest: true,
      requestStatus: 'PENDING', nextCycle: 4, reasonCode: 'OWN_REQUEST' }));
    await mailbox.submitPreparedSecretaryRound();
    expect(rounds.reviseSecretaryRequest).toHaveBeenCalledWith('117', technicalId,
      requestId, 0, 'Fixed draft');
    expect(rounds.submitPreparedSecretaryRequest).toHaveBeenCalledWith('117', technicalId, requestId);
    expect(mailbox.secretaryRoundRequest()?.status).toBe('PENDING');
    expect(mailbox.secretaryRoundNotice()).toBe('submitted');
    expect(correspondence.continueDirectorCycle).not.toHaveBeenCalled();
  });

  it('rejects a pending request when the server eligibility snapshot still says returned', async () => {
    rounds.secretaryRequest.mockReturnValue(of(pending));
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.secretaryRoundError()).toBe(true));
    expect(mailbox.secretaryRoundRequest()).toBeNull();
    expect(rounds.secretaryDocuments).not.toHaveBeenCalled();
    await mailbox.resubmitSecretaryRound();
    expect(rounds.resubmitSecretaryRequest).not.toHaveBeenCalled();
  });

  it('does not write or submit a pending request while manager approval is active', async () => {
    await loadPending();
    await mailbox.uploadSecretaryRoundFiles({ target: {
      files: [new File(['%PDF-1.5'], 'late.pdf', { type: 'application/pdf' })],
      value: 'late.pdf',
    } } as unknown as Event);
    await mailbox.deleteSecretaryRoundFile(pdf);
    await mailbox.submitPreparedSecretaryRound();
    expect(rounds.uploadSecretaryDocument).not.toHaveBeenCalled();
    expect(rounds.deleteSecretaryDocument).not.toHaveBeenCalled();
    expect(rounds.submitPreparedSecretaryRequest).not.toHaveBeenCalled();
  });

  it('does not revise or resubmit a pending request without a manager return', async () => {
    await loadPending();
    mailbox.secretaryRoundReason.set('Unauthorized pending edit');
    await mailbox.saveSecretaryRoundRevision();
    await mailbox.resubmitSecretaryRound();
    expect(rounds.reviseSecretaryRequest).not.toHaveBeenCalled();
    expect(rounds.resubmitSecretaryRequest).not.toHaveBeenCalled();
  });

  it('rejects a PDF from another workflow round even when its request ID is valid', async () => {
    rounds.secretaryDocuments.mockReturnValue(of({ items: [{ ...pdf, workflowCycle: 3 }] }));
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.secretaryRoundError()).toBe(true));
    expect(mailbox.secretaryRoundDocumentsLoaded()).toBe(false);
    await mailbox.deleteSecretaryRoundFile(pdf);
    expect(rounds.deleteSecretaryDocument).not.toHaveBeenCalled();
  });

  it('refuses to reuse an old return token after HTTP 409', async () => {
    await loadReturned();
    rounds.resubmitSecretaryRequest.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    await mailbox.resubmitSecretaryRound();
    expect(mailbox.secretaryRoundNotice()).toBe('stale');
    await mailbox.resubmitSecretaryRound();
    expect(rounds.resubmitSecretaryRequest).toHaveBeenCalledOnce();
  });

  it('does not retry a lost resubmission response without a fresh status check', async () => {
    await loadReturned();
    rounds.resubmitSecretaryRequest.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    await mailbox.resubmitSecretaryRound();
    expect(mailbox.secretaryRoundUncertain()).toBe(true);
    await mailbox.resubmitSecretaryRound();
    expect(rounds.resubmitSecretaryRequest).toHaveBeenCalledOnce();
  });

  it('checks server eligibility before loading a personal request and denies writes on 403', async () => {
    rounds.secretaryEligibility.mockReturnValue(of({ canPrepare: true, canReadRequest: false,
      requestStatus: null, nextCycle: 4, reasonCode: 'READY' }));
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(rounds.secretaryEligibility).toHaveBeenCalled());
    expect(rounds.secretaryRequest).not.toHaveBeenCalled();
    expect(mailbox.canSecretaryRound()).toBe(true);
    rounds.secretaryEligibility.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    mailbox.refreshSecretaryRound();
    await vi.waitFor(() => expect(mailbox.secretaryRoundEligibilityError()).toBe(true));
    expect(mailbox.canSecretaryRound()).toBe(false);
    await mailbox.createSecretaryRound();
    expect(rounds.submitSecretaryRequest).not.toHaveBeenCalled();
  });

  it('hides the follow-up panel when department work is incomplete', async () => {
    rounds.secretaryEligibility.mockReturnValue(of({ canPrepare: false, canReadRequest: false,
      requestStatus: null, nextCycle: 4, reasonCode: 'DEPARTMENT_NOT_READY' }));
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(rounds.secretaryEligibility).toHaveBeenCalled());
    expect(mailbox.canSecretaryRound()).toBe(false);
    expect(rounds.secretaryRequest).not.toHaveBeenCalled();
    mailbox.secretaryRoundReason.set('Cannot start this yet');
    await mailbox.createSecretaryRound();
    expect(rounds.submitSecretaryRequest).not.toHaveBeenCalled();
  });

  it('does not silently offer create if a previously eligible request disappeared', async () => {
    rounds.secretaryRequest.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.secretaryRoundError()).toBe(true));
    expect(mailbox.secretaryRoundRequest()).toBeNull();
    await mailbox.createSecretaryRound();
    expect(rounds.submitSecretaryRequest).not.toHaveBeenCalled();
  });

  it('rejects mismatched rounds rather than offering stale PDF edits', async () => {
    rounds.secretaryRequest.mockReturnValue(of({ ...returned, expectedCycle: 2 }));
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.secretaryRoundError()).toBe(true));
    expect(rounds.secretaryDocuments).not.toHaveBeenCalled();
    expect(mailbox.secretaryRoundDocumentsLoaded()).toBe(false);
  });

  it('does not offer secretary actions to an ordinary Director account', async () => {
    secretary.secretaryMailbox = () => false;
    await mailbox.loadPage(0);
    expect(mailbox.canSecretaryRound()).toBe(false);
    expect(rounds.secretaryRequest).not.toHaveBeenCalled();
    await mailbox.createSecretaryRound();
    expect(rounds.submitSecretaryRequest).not.toHaveBeenCalled();
  });

  it('creates a new request only after explicit backend eligibility', async () => {
    rounds.secretaryEligibility
      .mockReturnValueOnce(of({ canPrepare: true, canReadRequest: false,
        requestStatus: null, nextCycle: 4, reasonCode: 'READY' }))
      .mockReturnValueOnce(of({ canPrepare: false, canReadRequest: true,
        requestStatus: 'PREPARING', nextCycle: 4, reasonCode: 'OWN_REQUEST' }));
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.canSecretaryRound()).toBe(true));
    expect(rounds.secretaryRequest).not.toHaveBeenCalled();
    mailbox.secretaryRoundReason.set('Review evidence in next round');
    rounds.secretaryRequest.mockReturnValue(of({ ...preparing, reason: 'Review evidence in next round' }));
    await mailbox.createSecretaryRound();
    expect(rounds.submitSecretaryRequest).toHaveBeenCalledWith('117', technicalId,
      'Review evidence in next round');
    expect(mailbox.secretaryRoundRequest()?.status).toBe('PREPARING');
    expect(mailbox.secretaryRoundNotice()).toBe('prepared');
    expect(correspondence.continueDirectorCycle).not.toHaveBeenCalled();
  });
});
