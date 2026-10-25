import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceApiService } from '../../../shared/data-access/correspondence-api.service';
import { ComposePageComponent } from './compose-page.component';

const ID = '95465de1-4e70-4840-8433-9639618545fb';
const FILE_ID = '226b7d02-8d45-407d-9093-b4787d85a2d2';
const pdf = new File(['%PDF-1.7\n'], 'letter.pdf', { type: 'application/pdf' });
const uploaded = { nodeId: FILE_ID, filename: 'letter.pdf', mimeType: 'application/pdf', sizeBytes: pdf.size };
const feedback = {
  subjectRequired: 'Subject required', loading: 'Loading', loadFailed: 'Load failed',
  notEditable: 'Not editable', saved: 'Saved', saveFailed: 'Save failed',
  saveUncertain: 'Save uncertain', invalidPdf: 'Invalid PDF', pdfOnly: 'PDF only',
  savedFile: 'Saved file', removeFile: 'Remove', retryUpload: 'Retry',
  uploadFailed: 'Upload failed', uploadUncertain: 'Upload uncertain',
  confirmDelete: 'Confirm delete', deleteFailed: 'Delete failed',
  confirmCancel: 'Confirm cancel', sendPending: 'Send pending',
  sendIncomplete: 'Incomplete', sendBlocked: 'Pending files', confirmSend: 'Confirm send',
  sent: 'Sent', sendFailed: 'Send failed', sendUncertain: 'Verify send',
};

const api = {
  createDraft: vi.fn(),
  updateDraft: vi.fn(),
  getDetails: vi.fn(),
  uploadDocument: vi.fn(),
  deleteDocument: vi.fn(),
  transitionCorrespondence: vi.fn(),
};

let existingDraftId: string | null;
let component: ComposePageComponent;
let router: { navigate: ReturnType<typeof vi.fn> };

function select(files: File[]): void {
  component.onFilesSelected({ target: { files, value: 'selected' } } as unknown as Event);
}

function fill(): void {
  component.form.patchValue({
    subject: 'Legal review', recipient: 'Legal', copyTo: 'Finance',
    entity: 'Presidency', documentDate: '2026-10-25',
    instruction: 'required-action', explanation: 'Review enclosed PDF',
  });
}

describe('Executive Compose draft persistence', () => {
  beforeEach(() => {
    existingDraftId = null;
    Object.values(api).forEach(fn => fn.mockReset());
    api.createDraft.mockReturnValue(of({ technicalId: ID, state: 'DRAFT' }));
    api.updateDraft.mockReturnValue(of({ technicalId: ID, state: 'DRAFT' }));
    api.uploadDocument.mockReturnValue(of(uploaded));
    api.deleteDocument.mockReturnValue(of(undefined));
    api.transitionCorrespondence.mockReturnValue(of({ technicalId: ID, state: 'WITH_PRESIDENT_OFFICE' }));
    router = { navigate: vi.fn(() => Promise.resolve(true)) };
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    TestBed.configureTestingModule({
      providers: [
        { provide: CorrespondenceApiService, useValue: api },
        { provide: TranslationService, useValue: { text: () => ({ compose: { feedback } }) } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => existingDraftId } } } },
        { provide: Router, useValue: router },
      ]
    });
    component = TestBed.runInInjectionContext(() => new ComposePageComponent());
  });
  afterEach(() => vi.restoreAllMocks());

  it('sends a fully saved executive draft exactly once to President Office', async () => {
    fill();
    await component.send();
    expect(api.createDraft).toHaveBeenCalledTimes(1);
    expect(api.transitionCorrespondence).toHaveBeenCalledOnce();
    expect(api.transitionCorrespondence).toHaveBeenCalledWith(ID, { action: 'SEND_TO_OFFICE' });
    expect(component.readOnly()).toBe(true);
    expect(router.navigate).toHaveBeenCalledWith(['/sent']);
  });

  it('rejects sending incomplete forms without writing a draft', async () => {
    component.form.controls.subject.setValue('Legal review');
    await component.send();
    expect(api.createDraft).not.toHaveBeenCalled();
    expect(api.transitionCorrespondence).not.toHaveBeenCalled();
  });

  it('does not send while any PDF upload failed', async () => {
    fill();
    select([pdf]);
    api.uploadDocument.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
    await component.send();
    expect(component.queuedPdfs()[0]?.status).toBe('failed');
    expect(api.transitionCorrespondence).not.toHaveBeenCalled();
  });

  it('locks out automatic resubmission after an uncertain transition result', async () => {
    fill();
    api.transitionCorrespondence.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    await component.send();
    expect(component.sendingUncertain()).toBe(true);
    await component.send();
    expect(api.transitionCorrespondence).toHaveBeenCalledTimes(1);
  });

  it('does not finalize review documents when sending executive draft', async () => {
    fill();
    await component.send();
    expect(api.transitionCorrespondence).toHaveBeenCalledWith(ID, { action: 'SEND_TO_OFFICE' });
  });

  it('saves all executive fields together without transitioning or requiring the whole form', async () => {
    component.form.controls.subject.setValue('Legal review');
    await component.saveDraft();
    expect(api.createDraft).toHaveBeenCalledWith({
      subject: 'Legal review', bodyText: null,
      documentNumber: null, documentDate: null, sourceText: null,
      executiveCompose: { recipient: '', copyTo: '', entity: '', instruction: '', explanation: '' },
    });
    expect(component.technicalId()).toBe(ID);
    expect(api.transitionCorrespondence).not.toHaveBeenCalled();
    expect(component.notice()?.kind).toBe('success');
  });

  it('uploads only confirmed PDFs after allocating a single draft ID', async () => {
    fill();
    select([pdf]);
    await component.saveDraft();
    await component.saveDraft();
    expect(api.createDraft).toHaveBeenCalledTimes(1);
    expect(api.uploadDocument).toHaveBeenCalledTimes(1);
    expect(api.uploadDocument).toHaveBeenCalledWith(ID, pdf);
    expect(component.savedPdfs()[0]?.nodeId).toBe(FILE_ID);
    expect(component.queuedPdfs()).toHaveLength(0);
  });

  it('restores existing executive drafts and updates instead of creating another', async () => {
    api.getDetails.mockReturnValue(of({
      technicalId: ID, state: 'DRAFT', subject: 'Legal review',
      documentNumber: 'A-3', documentDate: '2026-10-25', documents: [uploaded],
      executiveCompose: {
        recipient: 'Legal', copyTo: 'Finance', entity: 'Presidency',
        instruction: 'required-action', explanation: 'Read this first'
      },
    }));
    existingDraftId = ID;
    component.ngOnInit();
    await Promise.resolve(); await Promise.resolve();
    expect(component.form.controls.recipient.value).toBe('Legal');
    expect(component.form.controls.instruction.value).toBe('required-action');
    expect(component.savedPdfs()).toHaveLength(1);
    component.form.controls.explanation.setValue('Updated explanation');
    component.form.controls.explanation.markAsDirty();
    await component.saveDraft();
    expect(api.createDraft).not.toHaveBeenCalled();
    expect(api.updateDraft).toHaveBeenCalledWith(ID, expect.objectContaining({
      executiveCompose: expect.objectContaining({ explanation: 'Updated explanation', recipient: 'Legal' }),
    }));
  });

  it('rejects opening a non-executive draft in executive Compose', async () => {
    api.getDetails.mockReturnValue(of({ technicalId: ID, state: 'DRAFT', documents: [] }));
    existingDraftId = ID;
    component.ngOnInit();
    await Promise.resolve(); await Promise.resolve();
    expect(component.readOnly()).toBe(true);
    expect(component.form.disabled).toBe(true);
    expect(api.updateDraft).not.toHaveBeenCalled();
  });

  it('does not retry a creation whose server outcome is uncertain', async () => {
    component.form.controls.subject.setValue('Legal review');
    api.createDraft.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    await component.saveDraft();
    expect(component.creationUncertain()).toBe(true);
    await component.saveDraft();
    expect(api.createDraft).toHaveBeenCalledTimes(1);
  });

  it('never automatically retries an uncertain upload', async () => {
    component.form.controls.subject.setValue('Legal review');
    select([pdf]);
    api.uploadDocument.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    await component.saveDraft();
    expect(component.queuedPdfs()[0]?.status).toBe('verify');
    await component.saveDraft();
    expect(api.uploadDocument).toHaveBeenCalledTimes(1);
    expect(api.transitionCorrespondence).not.toHaveBeenCalled();
  });

  it('rejects non-PDF files without an upload request', async () => {
    component.form.controls.subject.setValue('Legal review');
    select([new File(['data'], 'notes.docx')]);
    await component.saveDraft();
    expect(component.queuedPdfs()).toHaveLength(0);
    expect(api.uploadDocument).not.toHaveBeenCalled();
  });

  it('deletes only confirmed saved attachments', async () => {
    component.form.controls.subject.setValue('Legal review');
    select([pdf]);
    await component.saveDraft();
    await component.removeSavedPdf(uploaded);
    expect(api.deleteDocument).toHaveBeenCalledWith(ID, FILE_ID);
    expect(component.savedPdfs()).toHaveLength(0);
  });
});
