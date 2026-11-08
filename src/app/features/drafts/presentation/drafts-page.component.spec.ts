import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceApiService } from '../../../shared/data-access/correspondence-api.service';
import { DraftsPageComponent } from './drafts-page.component';
import { TarasolSecretaryApiService } from '../../tarasol-secretary/tarasol-secretary-api.service';

const ID = 'e87b1d62-3719-4bc5-ad5e-5043a1ca89a7';
const summary = {
  technicalId: ID, subject: 'Review', documentNumber: null,
  createdAt: '2026-10-25T08:00:00Z', modifiedAt: '2026-10-25T09:00:00Z',
};

describe('Drafts navigation for executive-originated correspondence', () => {
  const api = { listDrafts: vi.fn(), getDetails: vi.fn() };
  const router = { navigate: vi.fn(() => Promise.resolve(true)) };
  let role: 'PRESIDENT' | 'PRESIDENT_OFFICE';
  let page: DraftsPageComponent;

  beforeEach(() => {
    role = 'PRESIDENT_OFFICE';
    router.navigate.mockClear();
    api.listDrafts.mockReset();
    api.getDetails.mockReset();
    api.listDrafts.mockReturnValue(of({ items: [summary], total: 1, offset: 0, limit: 25 }));
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: api },
      { provide: TarasolSecretaryApiService, useValue: {} },
      { provide: AuthSessionStore, useValue: {
        currentUser: () => ({ employeeProfile: { correspondenceRole: role } }),
        effectiveRole: () => role,
        secretaryMailbox: () => false,
        secretaryInboxUnitCode: () => null,
      } },
      { provide: TranslationService, useValue: { text: () => ({ drafts: {} }) } },
      { provide: Router, useValue: router },
    ] });
    page = TestBed.runInInjectionContext(() => new DraftsPageComponent());
  });

  it('opens an Office-created DRAFT in Compose, not the Office-completion editor', async () => {
    api.getDetails.mockReturnValue(of({
      ...summary, state: 'DRAFT', documents: [], executiveCompose: {
        recipient: 'Legal', copyTo: '', entity: '', instruction: '', explanation: '',
      },
    }));
    await page.loadPage(0);
    await Promise.resolve(); await Promise.resolve();
    expect(page.details()?.state).toBe('DRAFT');
    page.openDraft(ID);
    expect(router.navigate).toHaveBeenCalledWith(['/office/new'], { queryParams: { draft: ID } });
  });

  it('retains the existing President review route for WITH_PRESIDENT work', async () => {
    role = 'PRESIDENT';
    api.getDetails.mockReturnValue(of({ ...summary, state: 'WITH_PRESIDENT', documents: [] }));
    await page.loadPage(0);
    await Promise.resolve(); await Promise.resolve();
    page.openDraft(ID);
    expect(router.navigate).toHaveBeenCalledWith(['/president-review', ID]);
  });
});


describe('Personal secretary Drafts in the existing Drafts page', () => {
  const api = { listDrafts: vi.fn(), getDetails: vi.fn() };
  const secretary = {
    list: vi.fn(), personalDraft: vi.fn(), documents: vi.fn(),
  };
  const router = { navigate: vi.fn(() => Promise.resolve(true)) };
  let page: DraftsPageComponent;

  beforeEach(() => {
    vi.clearAllMocks();
    api.listDrafts.mockReturnValue(of({ items: [], total: 0, offset: 0, limit: 25 }));
    secretary.list.mockReturnValue(of({ items: [{ ...summary,
      unitCode: '117', ownerEmployeeId: '99999', approvalStatus: 'RETURNED',
      bodyText: 'Please revise', returnReason: 'Fix document',
    }], total: 1, offset: 0, limit: 10 }));
    secretary.personalDraft.mockReturnValue(of({
      technicalId: ID, unitCode: '117', subject: 'Review', bodyText: 'Please revise',
      ownerEmployeeId: '99999', approvalStatus: 'RETURNED', documentNumber: '117-1',
      documentDate: '2026-11-04', sourceText: 'Department',
    }));
    secretary.documents.mockReturnValue(of({ items: [{ nodeId: ID,
      filename: 'attachment.pdf', mimeType: 'application/pdf', sizeBytes: 1024 }] }));
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: api },
      { provide: TarasolSecretaryApiService, useValue: secretary },
      { provide: AuthSessionStore, useValue: {
        secretaryMailbox: () => true,
        secretaryInboxUnitCode: () => '117',
        currentUser: () => ({ employeeProfile: { correspondenceRole: 'TARASOL_ADMIN',
          nameEnglish: 'Secretary User', nameArabic: null } }),
        effectiveRole: () => 'TARASOL_ADMIN',
      } },
      { provide: TranslationService, useValue: { text: () => ({ drafts: {} }) } },
      { provide: Router, useValue: router },
    ] });
    page = TestBed.runInInjectionContext(() => new DraftsPageComponent());
  });

  it('uses only the owner-scoped secretary list and detail/PDF endpoints', async () => {
    await page.loadPage(0);
    await vi.waitFor(() => expect(page.details()?.documents[0]?.filename).toBe('attachment.pdf'));
    expect(secretary.list).toHaveBeenCalledWith('117', 'mine', 0, 10, 'drafts');
    expect(secretary.personalDraft).toHaveBeenCalledWith('117', ID);
    expect(secretary.documents).toHaveBeenCalledWith('117', ID, 'mine');
    expect(api.listDrafts).not.toHaveBeenCalled();
    expect(api.getDetails).not.toHaveBeenCalled();
    expect(page.details()?.documents[0]?.filename).toBe('attachment.pdf');
    expect(page.selectedDraft()?.approvalStatus).toBe('RETURNED');
    expect(page.selectedDraft()?.returnReason).toBe('Fix document');
  });

  it('opens the existing Director compose component in secretary mode', async () => {
    page.openDraft(ID);
    expect(router.navigate).toHaveBeenCalledWith(['/secretary/new'],
      { queryParams: { draft: ID } });
    page.openCompose();
    expect(router.navigate).toHaveBeenCalledWith(['/secretary/new']);
  });
});
