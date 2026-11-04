import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceApiService } from '../../../shared/data-access/correspondence-api.service';
import { TarasolSecretaryApiService } from '../../tarasol-secretary/tarasol-secretary-api.service';
import { DirectorNewPageComponent } from './director-new-page.component';

const ID = '03e1930c-de08-46d1-8d29-18ae7d2add1d';
const NODE_ID = '22222222-2222-2222-2222-222222222222';
const PDF = new File(['%PDF-1.7 example'], 'sample.pdf', {
  type: 'application/pdf',
});
const uploaded = {
  nodeId: NODE_ID,
  filename: 'sample.pdf',
  mimeType: 'application/pdf',
  sizeBytes: PDF.size,
};

const labels = {
  confirmSend: 'Confirm send?',
  subjectRequired: 'Subject required',
  createUncertain: 'Creation uncertain',
  createFailed: 'Creation failed',
  updateUncertain: 'Update uncertain',
  updateFailed: 'Update failed',
  invalidFile: 'Invalid file',
  uploadUncertain: 'Upload uncertain',
  uploadFailed: 'Upload failed',
  saved: 'Draft saved',
  sent: 'Sent',
  sendBlocked: 'Cannot send with failed uploads',
  sendUncertain: 'Send uncertain',
  sendFailed: 'Send failed',
};

describe('Director Send', () => {
  const api = {
    createDraft: vi.fn(),
    updateDraft: vi.fn(),
    uploadDocument: vi.fn(),
    getDetails: vi.fn(),
    transitionCorrespondence: vi.fn(),
  };
  let page: DirectorNewPageComponent;
  let calls: string[];
  let language: 'en' | 'ar';
  let department: { code: string; nameEnglish: string; nameArabic: string | null } | null;
  let workspace: { code: string; nameEnglish: string; nameArabic: string | null; role?: string } | null;
  let roles: string[];
  let managerWorkspaces: { code: string; capacity: string }[];

  beforeEach(() => {
    language = 'en';
    department = null;
    workspace = null;
    roles = [];
    managerWorkspaces = [];
    calls = [];
    Object.values(api).forEach((mock) => mock.mockReset());
    api.createDraft.mockImplementation(() => {
      calls.push('create');
      return of({ technicalId: ID, state: 'DRAFT' });
    });
    api.uploadDocument.mockImplementation(() => {
      calls.push('upload');
      return of(uploaded);
    });
    api.getDetails.mockImplementation(() => {
      calls.push('verify');
      return of({
        technicalId: ID,
        state: 'DRAFT',
        subject: 'Test correspondence',
        documentNumber: null,
        documentDate: null,
        bodyText: null,
        sourceText: null,
        originDepartmentCode: '117',
        createdByNameEnglish: 'Test',
        createdByNameArabic: null,
        documents: [uploaded],
      });
    });
    api.transitionCorrespondence.mockImplementation(() => {
      calls.push('transition');
      return of({ technicalId: ID, state: 'WITH_PRESIDENT_OFFICE' });
    });

    vi.spyOn(window, 'confirm').mockReturnValue(true);

    TestBed.configureTestingModule({
      providers: [
        { provide: CorrespondenceApiService, useValue: api },
        { provide: TarasolSecretaryApiService, useValue: {} },
        { provide: AuthSessionStore, useValue: {
          currentUser: () => ({ employeeProfile: {
            department, correspondenceRoles: roles, managerWorkspaces,
          } }),
          activeWorkspace: () => workspace,
        } },
        {
          provide: TranslationService,
          useValue: { text: () => ({ director: labels }), language: () => language },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => null } } },
        },
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
      ],
    });
    page = TestBed.runInInjectionContext(() => new DirectorNewPageComponent());
    page.form.controls.subject.setValue('Test correspondence');
  });

  afterEach(() => vi.restoreAllMocks());

  it('prefills the selected acting HR workspace rather than the SAP home department', () => {
    department = { code: '108', nameEnglish: 'Regional Offices', nameArabic: 'المكاتب الإقليمية' };
    workspace = { code: '118', nameEnglish: 'Human Resources', nameArabic: 'الموارد البشرية' };
    page.ngOnInit();
    expect(page.form.controls.sourceText.value).toBe('Human Resources');
  });

  it('prefills the selected acting HR workspace in Arabic', () => {
    department = { code: '108', nameEnglish: 'Regional Offices', nameArabic: 'المكاتب الإقليمية' };
    workspace = { code: '118', nameEnglish: 'Human Resources', nameArabic: 'الموارد البشرية' };
    language = 'ar';
    page.ngOnInit();
    expect(page.form.controls.sourceText.value).toBe('الموارد البشرية');
  });

  it('preserves a manually entered source despite an active acting workspace', () => {
    department = { code: '108', nameEnglish: 'Regional Offices', nameArabic: 'المكاتب الإقليمية' };
    workspace = { code: '118', nameEnglish: 'Human Resources', nameArabic: 'الموارد البشرية' };
    language = 'ar';
    page.form.controls.sourceText.setValue('Custom source');
    page.ngOnInit();
    expect(page.form.controls.sourceText.value).toBe('Custom source');
  });

  it('retains the legacy home-department default when no workspace is selected', () => {
    department = { code: '117', nameEnglish: 'Digital Transformation', nameArabic: 'التحول الرقمي' };
    page.ngOnInit();
    expect(page.form.controls.sourceText.value).toBe('Digital Transformation');
  });

  function selectPdf(): void {
    const input = { files: [PDF], value: 'selected' } as unknown as HTMLInputElement;
    page.onFilesSelected({ target: input } as unknown as Event);
  }

  it('persists draft and PDF before sending and locks the form on success', async () => {
    selectPdf();
    await page.sendDraft();
    expect(calls).toEqual(['create', 'upload', 'verify', 'transition']);
    expect(api.transitionCorrespondence).toHaveBeenCalledWith(ID, {
      action: 'SEND_TO_OFFICE',
    });
    expect(page.readOnly()).toBe(true);
    expect(page.form.disabled).toBe(true);
  });

  it('routes a verified dual-manager Director draft straight to President', async () => {
    department = { code: '108', nameEnglish: 'Regional Offices', nameArabic: null };
    workspace = { code: '118', nameEnglish: 'Human Resources', nameArabic: null, role: 'DIRECTOR' };
    roles = ['DIRECTOR', 'PRESIDENT_OFFICE'];
    managerWorkspaces = [{ code: '101', capacity: 'MANAGER' }, { code: '118', capacity: 'ACTING_MANAGER' }];
    api.getDetails.mockImplementation(() => of({
      technicalId: ID, state: 'DRAFT', documents: [], originDepartmentCode: '118',
    }));
    api.transitionCorrespondence.mockReturnValue(of({ technicalId: ID, state: 'WITH_PRESIDENT' }));
    expect(page.directPresidentEligible()).toBe(true);
    page.form.controls.recipient.setValue('President');
    page.form.controls.instruction.setValue('required-action');
    page.form.controls.explanation.setValue('Please review');
    await page.sendDraft();
    expect(api.createDraft).toHaveBeenCalledWith(expect.objectContaining({
      executiveCompose: expect.objectContaining({
        recipient: 'President', instruction: 'required-action', explanation: 'Please review',
      }),
    }));
    expect(api.transitionCorrespondence).toHaveBeenCalledWith(ID, { action: 'SEND_TO_PRESIDENT' });
    expect(page.readOnly()).toBe(true);
  });

  it('never bypasses the Office without both organizational role and appointment', async () => {
    workspace = { code: '118', nameEnglish: 'Human Resources', nameArabic: null, role: 'DIRECTOR' };
    roles = ['DIRECTOR', 'PRESIDENT_OFFICE'];
    managerWorkspaces = [{ code: '118', capacity: 'ACTING_MANAGER' }];
    await page.sendDraft();
    expect(api.createDraft).toHaveBeenCalledWith(expect.not.objectContaining({
      executiveCompose: expect.anything(),
    }));
    expect(api.transitionCorrespondence).toHaveBeenCalledWith(ID, { action: 'SEND_TO_OFFICE' });
  });

  it('Save Draft never sends', async () => {
    selectPdf();
    await page.saveDraft();
    expect(api.transitionCorrespondence).not.toHaveBeenCalled();
    expect(page.readOnly()).toBe(false);
  });

  it('never sends after a definite PDF upload failure', async () => {
    selectPdf();
    api.uploadDocument.mockImplementation(() =>
      throwError(() => new HttpErrorResponse({ status: 400 }))
    );
    await page.sendDraft();
    expect(api.transitionCorrespondence).not.toHaveBeenCalled();
    expect(page.documents()[0]?.status).toBe('failed');
  });

  it('never sends after an uncertain PDF upload', async () => {
    selectPdf();
    api.uploadDocument.mockImplementation(() =>
      throwError(() => new HttpErrorResponse({ status: 0 }))
    );
    await page.sendDraft();
    expect(api.transitionCorrespondence).not.toHaveBeenCalled();
    expect(page.documents()[0]?.status).toBe('verify');
  });

  it('blocks duplicate Send attempts after an uncertain transition', async () => {
    api.getDetails.mockImplementation(() => of({
      technicalId: ID, state: 'DRAFT', subject: 'Test correspondence',
      documentNumber: null, documentDate: null, bodyText: null,
      sourceText: null, originDepartmentCode: '117',
      createdByNameEnglish: 'Test', createdByNameArabic: null,
      documents: [],
    }));
    api.transitionCorrespondence.mockImplementation(() =>
      throwError(() => new HttpErrorResponse({ status: 0 }))
    );
    await page.sendDraft();
    expect(page.submissionUncertain()).toBe(true);
    await page.sendDraft();
    expect(api.transitionCorrespondence).toHaveBeenCalledTimes(1);
  });

  it('cancelling the confirmation does not persist or send', async () => {
    vi.mocked(window.confirm).mockReturnValue(false);
    await page.sendDraft();
    expect(api.createDraft).not.toHaveBeenCalled();
    expect(api.transitionCorrespondence).not.toHaveBeenCalled();
  });
});


describe('Secretary uses the Director form with approval-only submission', () => {
  const documents = [{ nodeId: NODE_ID, filename: PDF.name,
    mimeType: 'application/pdf', sizeBytes: PDF.size }];
  let page: DirectorNewPageComponent;
  let pendingDraft: string | null;
  const directorApi = {
    createDraft: vi.fn(), updateDraft: vi.fn(), transitionCorrespondence: vi.fn(),
    uploadDocument: vi.fn(), deleteDocument: vi.fn(), getDetails: vi.fn(),
  };
  const secretaryApi = {
    change: vi.fn(), uploadPdf: vi.fn(), deletePdf: vi.fn(),
    personalDraft: vi.fn(), documents: vi.fn(),
  };

  beforeEach(() => {
    pendingDraft = null;
    Object.values(directorApi).forEach(mock => mock.mockReset());
    Object.values(secretaryApi).forEach(mock => mock.mockReset());
    secretaryApi.change.mockImplementation((_unit, action) => of(action === 'CREATE_DRAFT'
      ? { technicalId: ID, approvalStatus: 'DRAFT', ok: true }
      : { approvalStatus: action === 'SUBMIT' ? 'PENDING' : 'DRAFT', ok: true }));
    secretaryApi.uploadPdf.mockReturnValue(of({ nodeId: NODE_ID, filename: PDF.name }));
    secretaryApi.personalDraft.mockReturnValue(of({
      technicalId: ID, unitCode: '117', ownerEmployeeId: '99999',
      subject: 'Report', bodyText: 'Body', documentNumber: null,
      documentDate: '2026-11-04', sourceText: 'Digital Transformation', approvalStatus: 'DRAFT',
    }));
    secretaryApi.documents.mockReturnValue(of({ items: documents }));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const secretaryUnit = { code: '117', nameEnglish: 'Digital Transformation', nameArabic: null };
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: directorApi },
      { provide: TarasolSecretaryApiService, useValue: secretaryApi },
      { provide: AuthSessionStore, useValue: {
        currentUser: () => ({ employeeProfile: {
          correspondenceRole: 'TARASOL_ADMIN', secretaryUnits: [secretaryUnit],
          department: secretaryUnit, managerWorkspaces: [], correspondenceRoles: [],
        } }),
        activeWorkspace: () => null,
        secretaryInboxUnitCode: () => '117',
      } },
      { provide: TranslationService, useValue: {
        language: () => 'en', text: () => ({ director: labels }),
      } },
      { provide: ActivatedRoute, useValue: {
        snapshot: { data: { secretary: true }, queryParamMap: {
          get: (name: string) => name === 'draft' ? pendingDraft : null,
        } },
      } },
      { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
    ] });
    page = TestBed.runInInjectionContext(() => new DirectorNewPageComponent());
    page.form.controls.subject.setValue('Correspondence from secretary');
    page.form.controls.documentDate.setValue('2026-11-04');
    page.form.controls.sourceText.setValue('Digital Transformation');
  });
  afterEach(() => vi.restoreAllMocks());

  it('saves a full personal draft without calling any Director write API', async () => {
    page.onFilesSelected({ target: { files: [PDF], value: 'chosen' } } as unknown as Event);
    await page.saveDraft();
    expect(secretaryApi.change).toHaveBeenCalledWith('117', 'CREATE_DRAFT', expect.objectContaining({
      subject: 'Correspondence from secretary', documentDate: '2026-11-04',
      sourceText: 'Digital Transformation',
    }));
    expect(secretaryApi.uploadPdf).toHaveBeenCalledWith('117', ID, PDF);
    expect(directorApi.createDraft).not.toHaveBeenCalled();
    expect(directorApi.uploadDocument).not.toHaveBeenCalled();
    expect(directorApi.transitionCorrespondence).not.toHaveBeenCalled();
    expect(page.technicalId()).toBe(ID);
  });

  it('submits only for manager approval after confirming persisted attachments', async () => {
    page.onFilesSelected({ target: { files: [PDF], value: 'chosen' } } as unknown as Event);
    await page.sendDraft();
    expect(secretaryApi.personalDraft).toHaveBeenCalledWith('117', ID);
    expect(secretaryApi.documents).toHaveBeenCalledWith('117', ID, 'mine');
    expect(secretaryApi.change).toHaveBeenLastCalledWith('117', 'SUBMIT', { technicalId: ID });
    expect(directorApi.transitionCorrespondence).not.toHaveBeenCalled();
    expect(page.readOnly()).toBe(true);
  });

  it('blocks approval submission if PDF upload was not confirmed', async () => {
    secretaryApi.uploadPdf.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    page.onFilesSelected({ target: { files: [PDF], value: 'chosen' } } as unknown as Event);
    await page.sendDraft();
    expect(secretaryApi.change).not.toHaveBeenCalledWith('117', 'SUBMIT', expect.anything());
    expect(page.documents()[0].status).toBe('verify');
  });

  it('does not retry an uncertain manager-approval submission', async () => {
    secretaryApi.documents.mockReturnValue(of({ items: [] }));
    secretaryApi.change.mockImplementation((_unit, action) => action === 'SUBMIT'
      ? throwError(() => new HttpErrorResponse({ status: 0 }))
      : of({ technicalId: ID, approvalStatus: 'DRAFT', ok: true }));
    await page.sendDraft();
    expect(page.submissionUncertain()).toBe(true);
    await page.sendDraft();
    const submits = secretaryApi.change.mock.calls.filter(([, action]) => action === 'SUBMIT');
    expect(submits.length).toBe(1);
    expect(directorApi.transitionCorrespondence).not.toHaveBeenCalled();
  });

  it('loads returned drafts for editing without querying Director details', async () => {
    pendingDraft = ID;
    secretaryApi.personalDraft.mockReturnValue(of({ technicalId: ID, unitCode: '117',
      ownerEmployeeId: '99999', subject: 'Returned draft', bodyText: 'Needs correction',
      documentNumber: 'DOC-117', documentDate: '2026-11-02', sourceText: 'Department',
      approvalStatus: 'RETURNED', returnReason: 'Please revise the PDF',
    }));
    page.ngOnInit();
    await vi.waitFor(() => expect(page.loading()).toBe(false));
    expect(page.form.controls.subject.value).toBe('Returned draft');
    expect(page.form.controls.documentDate.value).toBe('2026-11-02');
    expect(page.readOnly()).toBe(false);
    expect(directorApi.getDetails).not.toHaveBeenCalled();
    page.form.controls.subject.setValue('Corrected draft');
    page.form.controls.subject.markAsDirty();
    await page.saveDraft();
    expect(secretaryApi.change).toHaveBeenCalledWith('117', 'REVISE', expect.objectContaining({
      technicalId: ID, subject: 'Corrected draft', documentNumber: 'DOC-117',
    }));
  });
});
