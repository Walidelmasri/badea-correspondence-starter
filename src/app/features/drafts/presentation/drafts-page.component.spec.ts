import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceApiService } from '../../../shared/data-access/correspondence-api.service';
import { DraftsPageComponent } from './drafts-page.component';

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
      { provide: AuthSessionStore, useValue: { currentUser: () => ({ employeeProfile: { correspondenceRole: role } }) } },
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
    expect(router.navigate).toHaveBeenCalledWith(['/compose'], { queryParams: { draft: ID } });
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
