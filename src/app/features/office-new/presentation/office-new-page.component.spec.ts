import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceApiService } from '../../../shared/data-access/correspondence-api.service';
import { OfficeNewPageComponent } from './office-new-page.component';

const ID = 'e87b1d62-3719-4bc5-ad5e-5043a1ca89a7';
const feedback = {
  sendIncomplete: 'Incomplete', sendBlocked: 'Cannot send', confirmSend: 'Send?',
  sent: 'Sent', saveFailed: 'Save failed', saved: 'Saved',
};

describe('President Office institutional intake', () => {
  const api = {
    createDraft: vi.fn(), updateDraft: vi.fn(), uploadDocument: vi.fn(),
    deleteDocument: vi.fn(), getDetails: vi.fn(), transitionCorrespondence: vi.fn(),
  };
  const router = { navigate: vi.fn(() => Promise.resolve(true)) };

  beforeEach(() => {
    Object.values(api).forEach(fn => fn.mockReset());
    api.createDraft.mockReturnValue(of({ technicalId: ID, state: 'DRAFT' }));
    api.transitionCorrespondence.mockReturnValue(of({ technicalId: ID, state: 'WITH_PRESIDENT' }));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: api },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
      { provide: Router, useValue: router },
      { provide: TranslationService, useValue: { text: () => ({ compose: { feedback } }) } },
    ] });
  });

  it('fixes the institutional destination without using the employee department', async () => {
    const office = TestBed.runInInjectionContext(() => new OfficeNewPageComponent());
    office.ngOnInit();
    expect(office.form.controls.recipient.value).toBe('President');
    expect(office.form.controls.instruction.value).toBe('information-and-action');
    office.form.patchValue({ subject: 'Office intake', documentDate: '2026-11-02' });
    await office.send();
    expect(api.createDraft).toHaveBeenCalledWith(expect.objectContaining({
      subject: 'Office intake',
      executiveCompose: expect.objectContaining({ recipient: 'President' }),
    }));
    expect(api.transitionCorrespondence).toHaveBeenCalledWith(ID, { action: 'SEND_TO_PRESIDENT' });
  });
});
