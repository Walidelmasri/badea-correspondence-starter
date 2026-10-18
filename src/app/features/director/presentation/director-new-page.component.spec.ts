import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceApiService } from '../../../shared/data-access/correspondence-api.service';
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

  beforeEach(() => {
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
        { provide: AuthSessionStore, useValue: { currentUser: () => null } },
        {
          provide: TranslationService,
          useValue: { text: () => ({ director: labels }), language: () => 'en' },
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
