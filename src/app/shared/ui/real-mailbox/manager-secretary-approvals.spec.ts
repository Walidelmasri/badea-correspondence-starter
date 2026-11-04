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

const id = '7fc41b5b-aceb-48fc-9f36-367e4a36e207';
const pending = {
  technicalId: id, unitCode: '117', subject: 'Department request', bodyText: 'Review the attached PDF',
  ownerEmployeeId: '99999', approvalStatus: 'PENDING' as const, senderName: 'Secretary',
  documentNumber: 'DOC-117', createdAt: '2026-11-04T10:00:00Z',
  modifiedAt: '2026-11-04T11:00:00Z',
};

const managerWorkspace = {
  code: '117', nameEnglish: 'Digital Transformation', nameArabic: null,
  role: 'DIRECTOR' as const, capacity: 'MANAGER' as const,
};

describe('Manager approvals inside the existing Inbox', () => {
  let mailbox: RealMailboxComponent;
  let secretary: {
    list: ReturnType<typeof vi.fn>;
    pendingDraft: ReturnType<typeof vi.fn>;
    documents: ReturnType<typeof vi.fn>;
    change: ReturnType<typeof vi.fn>;
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
    secretary = {
      list: vi.fn(() => of({ items: [pending], total: 1, offset: 0, limit: 10 })),
      pendingDraft: vi.fn(() => of({ ...pending, documentDate: '2026-11-04', sourceText: 'Digital Transformation' })),
      documents: vi.fn(() => of({ items: [] })),
      change: vi.fn(() => of({ ok: true, approvalStatus: 'APPROVED' })),
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
      { provide: TarasolSecretaryApiService, useValue: secretary },
      { provide: TarasolRoundContinuationApiService, useValue: {
        pending: vi.fn(() => of({ items: [], total: 0, offset: 0, limit: 10 })),
      } },
      { provide: CorrespondenceApiService, useValue: {
        listMailbox: vi.fn(() => of({ items: [], total: 0, offset: 0, limit: 10 })),
        listSecretaryInbox: vi.fn(() => of({ items: [], total: 0, offset: 0, limit: 10 })),
      } },
      { provide: CorrespondenceService, useValue: { getDepartments: vi.fn(() => of([])) } },
      { provide: TranslationService, useValue: { language: () => 'en' } },
      { provide: Router, useValue: { navigate: vi.fn() } },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
      { provide: UnreadInboxStore, useValue: {} },
    ] });
    mailbox = TestBed.runInInjectionContext(() => new RealMailboxComponent());
    Object.defineProperty(mailbox, 'box', { value: () => 'inbox' });
  });

  it('loads pending secretary cases for the active manager unit and verifies PDFs and metadata', async () => {
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.pendingDocumentsLoaded()).toBe(true));
    expect(secretary.list).toHaveBeenCalledWith('117', 'pending', 0, 10);
    expect(secretary.pendingDraft).toHaveBeenCalledWith('117', id);
    expect(secretary.documents).toHaveBeenCalledWith('117', id, 'pending');
    expect(mailbox.selectedPending()?.technicalId).toBe(id);
    expect(mailbox.pendingMetadata()?.sourceText).toBe('Digital Transformation');
  });

  it('approves and forwards using the server-authorized action, never the Director transition endpoint', async () => {
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.pendingDocumentsLoaded()).toBe(true));
    secretary.list.mockReturnValue(of({ items: [], total: 0, offset: 0, limit: 10 }));
    await mailbox.processPending('APPROVE_AND_SEND');
    expect(secretary.change).toHaveBeenCalledOnce();
    expect(secretary.change).toHaveBeenCalledWith('117', 'APPROVE_AND_SEND', { technicalId: id });
    expect(mailbox.pendingNotice()).toBe('approved');
  });

  it('requires a nonblank reason before returning for revision', async () => {
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.pendingDocumentsLoaded()).toBe(true));
    await mailbox.processPending('RETURN');
    expect(secretary.change).not.toHaveBeenCalled();
    mailbox.pendingReason.set('Needs updated attachment');
    await mailbox.processPending('RETURN');
    expect(secretary.change).toHaveBeenCalledWith('117', 'RETURN', {
      technicalId: id, explanation: 'Needs updated attachment',
    });
  });

  it('blocks immediate retry when the approval response may have been lost', async () => {
    await mailbox.loadPage(0);
    await vi.waitFor(() => expect(mailbox.pendingDocumentsLoaded()).toBe(true));
    secretary.change.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    await mailbox.processPending('APPROVE_AND_SEND');
    expect(mailbox.pendingNotice()).toBe('uncertain');
    await mailbox.processPending('APPROVE_AND_SEND');
    expect(secretary.change).toHaveBeenCalledOnce();
  });

  it('preserves manager approvals for a manager who is also a secretary', async () => {
    session.secretaryMailbox = () => true;
    session.secretaryInboxUnitCode = () => '117';
    await mailbox.loadPage(0);
    expect(secretary.list).toHaveBeenCalledWith('117', 'pending', 0, 10);
  });

  it('never queries the approval queue without a selected manager appointment', async () => {
    session.activeManagerWorkspace = () => null;
    session.availableWorkspaces = () => [];
    await mailbox.loadPage(0);
    expect(secretary.list).not.toHaveBeenCalled();
  });
});
