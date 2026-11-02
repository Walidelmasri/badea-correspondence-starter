import { TestBed } from '@angular/core/testing';
import { Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceService } from '../../../core/correspondence/correspondence.service';
import { CorrespondenceApiService } from '../../../shared/data-access/correspondence-api.service';
import { OfficeNewPageComponent } from './office-new-page.component';
import { ComposePageComponent } from '../../compose/presentation/compose-page.component';

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
  const correspondence = { getDepartments: vi.fn() };
  const router = { navigate: vi.fn(() => Promise.resolve(true)) };

  beforeEach(() => {
    Object.values(api).forEach(fn => fn.mockReset());
    api.createDraft.mockReturnValue(of({ technicalId: ID, state: 'DRAFT' }));
    correspondence.getDepartments.mockReset();
    correspondence.getDepartments.mockReturnValue(of([
      { code: '117', nameEnglish: 'Digital Transformation Department', nameArabic: 'إدارة التحول الرقمي' },
      { code: '103', nameEnglish: 'Internal Audit', nameArabic: 'إدارة التدقيق الداخلي' },
    ]));
    api.transitionCorrespondence.mockReturnValue(of({ technicalId: ID, state: 'WITH_PRESIDENT' }));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: api },
      { provide: CorrespondenceService, useValue: correspondence },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
      { provide: Router, useValue: router },
      { provide: TranslationService, useValue: { text: () => ({ compose: { feedback } }) } },
    ] });
  });

  it('fixes the institutional destination without using the employee department', async () => {
    const office = TestBed.runInInjectionContext(() => new OfficeNewPageComponent());
    office.ngOnInit();
    expect(office.form.controls.recipient.value).toBe('President');
    expect(office.form.controls.instruction.value).toBeNull();
    expect(office.form.controls.instruction.hasValidator(Validators.required)).toBe(false);
    office.form.patchValue({ subject: 'Office intake', documentDate: '2026-11-02' });
    await office.send();
    expect(api.createDraft).toHaveBeenCalledWith(expect.objectContaining({
      subject: 'Office intake',
      executiveCompose: expect.objectContaining({ recipient: 'President', copyTo: '', instruction: '' }),
    }));
    expect(api.transitionCorrespondence).toHaveBeenCalledWith(ID, { action: 'SEND_TO_PRESIDENT' });
  });

  it('suggests real BADEA departments while accepting a freely typed country', async () => {
    const office = TestBed.runInInjectionContext(() => new OfficeNewPageComponent());
    office.ngOnInit();
    await vi.waitFor(() => expect(office.departments()).toHaveLength(2));
    expect(office.departments()[0].code).toBe('117');
    office.form.patchValue({
      subject: 'Foreign mission', documentDate: '2026-11-02', entity: 'Kenya',
    });
    await office.send();
    expect(api.createDraft).toHaveBeenCalledWith(expect.objectContaining({
      executiveCompose: expect.objectContaining({ entity: 'Kenya', instruction: '' }),
    }));
  });

  it('persists a selected department name as descriptive metadata, not a routing target', async () => {
    const office = TestBed.runInInjectionContext(() => new OfficeNewPageComponent());
    office.ngOnInit();
    office.form.patchValue({
      subject: 'Department note', documentDate: '2026-11-02',
      entity: 'إدارة التحول الرقمي', instruction: null,
    });
    await office.send();
    expect(api.createDraft).toHaveBeenCalledWith(expect.objectContaining({
      executiveCompose: expect.objectContaining({ entity: 'إدارة التحول الرقمي' }),
    }));
    expect(api.transitionCorrespondence).toHaveBeenCalledWith(ID, { action: 'SEND_TO_PRESIDENT' });
  });

  it('does not block free entry if the department directory is unavailable', async () => {
    correspondence.getDepartments.mockReturnValue(throwError(() => new Error('unavailable')));
    const office = TestBed.runInInjectionContext(() => new OfficeNewPageComponent());
    office.ngOnInit();
    await vi.waitFor(() => expect(correspondence.getDepartments).toHaveBeenCalled());
    office.form.patchValue({
      subject: 'International letter', documentDate: '2026-11-02', entity: 'Senegal',
    });
    await office.send();
    expect(api.transitionCorrespondence).toHaveBeenCalledWith(ID, { action: 'SEND_TO_PRESIDENT' });
  });

  it('allows clearing an action but does not remove the President editor validator', () => {
    const president = TestBed.runInInjectionContext(() => new ComposePageComponent());
    expect(president.form.controls.instruction.hasValidator(Validators.required)).toBe(true);
    const office = TestBed.runInInjectionContext(() => new OfficeNewPageComponent());
    expect(office.form.controls.instruction.hasValidator(Validators.required)).toBe(false);
    office.form.controls.instruction.setValue('required-action');
    office.clearInstruction();
    expect(office.form.controls.instruction.value).toBeNull();
    expect(president.form.controls.instruction.hasValidator(Validators.required)).toBe(true);
  });

  it('clears legacy hidden CC when saving an Office draft, without changing destination', async () => {
    const office = TestBed.runInInjectionContext(() => new OfficeNewPageComponent());
    office.ngOnInit();
    office.form.patchValue({
      subject: 'Office request', documentDate: '2026-11-02', copyTo: 'Previous copy',
    });
    await office.send();
    expect(api.createDraft).toHaveBeenCalledWith(expect.objectContaining({
      executiveCompose: expect.objectContaining({ copyTo: '', recipient: 'President' }),
    }));
  });
});
