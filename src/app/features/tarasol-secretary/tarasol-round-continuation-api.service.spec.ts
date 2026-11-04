import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TarasolRoundContinuationApiService } from './tarasol-round-continuation-api.service';

const technicalId = '7fc41b5b-aceb-48fc-9f36-367e4a36e207';
const requestId = '7ac41b5b-aceb-48fc-9f36-367e4a36e207';

describe('TarasolRoundContinuationApiService', () => {
  let api: TarasolRoundContinuationApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(TarasolRoundContinuationApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());


  it('lists staged manager PDFs with the precise request ID and unit', () => {
    api.managerDocuments('117', technicalId, requestId).subscribe(result => expect(result.items).toEqual([]));
    const req = http.expectOne(item => item.url.endsWith(`/round-continuation/${technicalId}/documents`));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('requestId')).toBe(requestId);
    expect(req.request.params.get('unitCode')).toBe('117');
    req.flush({ documentsRevision: 0, items: [] });
  });

  it('previews manager-staged PDF through the manager-specific endpoint', () => {
    api.managerPreview('117', technicalId, requestId, 'pdf-node').subscribe(blob => expect(blob.type).toBe('application/pdf'));
    const req = http.expectOne(item => item.url.endsWith(`/round-continuation/${technicalId}/documents/pdf-node`));
    expect(req.request.responseType).toBe('blob');
    expect(req.request.params.get('requestId')).toBe(requestId);
    expect(req.request.params.get('unitCode')).toBe('117');
    req.flush(new Blob(['%PDF-1.5'], { type: 'application/pdf' }));
  });

  it('stages secretary PDFs against an explicit request and cannot send through Director route', () => {
    api.uploadSecretaryDocument('117', technicalId, requestId,
      new File(['%PDF-1.5'], 'follow-up.pdf', { type: 'application/pdf' })).subscribe();
    const req = http.expectOne(item => item.url.endsWith(`/secretary/round/${technicalId}/requests/${requestId}/documents`));
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('unitCode')).toBe('117');
    expect(req.request.params.get('filename')).toBe('follow-up.pdf');
    expect(req.request.headers.get('Content-Type')).toBe('application/pdf');
    req.flush({ nodeId: 'pdf-node', filename: 'follow-up.pdf', mimeType: 'application/pdf', sizeBytes: 8, workflowCycle: 4 });
  });

  it('loads and creates a secretary follow-up request for the selected unit', () => {
    api.secretaryRequest('117', technicalId).subscribe(result => expect(result.requestId).toBe(requestId));
    const current = http.expectOne(item => item.url.endsWith(`/secretary/round-two/${technicalId}`));
    expect(current.request.method).toBe('GET');
    expect(current.request.params.get('unitCode')).toBe('117');
    current.flush({ requestId, technicalId, unitCode: '117', status: 'PENDING',
      expectedCycle: 3, reason: 'Review', returnReason: null, requestedAt: '2026-11-04' });
    api.submitSecretaryRequest('117', technicalId, 'Additional documents').subscribe();
    const submitted = http.expectOne(item => item.url.endsWith(`/secretary/round-two/${technicalId}`));
    expect(submitted.request.method).toBe('POST');
    expect(submitted.request.params.get('unitCode')).toBe('117');
    expect(submitted.request.body).toEqual({ reason: 'Additional documents' });
    submitted.flush({ requestId, technicalId, unitCode: '117', status: 'PREPARING',
      expectedCycle: 3, reason: 'Additional documents', returnReason: null, requestedAt: '2026-11-04' });
  });

  it('publishes a prepared request only through the explicit submit endpoint', () => {
    api.submitPreparedSecretaryRequest('117', technicalId, requestId).subscribe();
    const req = http.expectOne(item => item.url.endsWith(`/secretary/round-two/${technicalId}/submit`));
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('unitCode')).toBe('117');
    expect(req.request.body).toEqual({ requestId });
    req.flush({ requestId, technicalId, unitCode: '117', status: 'PENDING',
      expectedCycle: 3, reason: 'Prepared', returnReason: null, requestedAt: '2026-11-04' });
  });

  it('revises only the returned request and resubmits with the exact manager return version', () => {
    api.reviseSecretaryRequest('117', technicalId, requestId, 3, 'Corrected reason').subscribe();
    const revised = http.expectOne(item => item.url.endsWith(`/secretary/round-two/${technicalId}`));
    expect(revised.request.method).toBe('PUT');
    expect(revised.request.params.get('unitCode')).toBe('117');
    expect(revised.request.body).toEqual({ requestId, returnVersion: 3, reason: 'Corrected reason' });
    revised.flush({ requestId, technicalId, unitCode: '117', status: 'RETURNED',
      returnVersion: 3, expectedCycle: 4, reason: 'Corrected reason',
      returnReason: 'Replace the PDF', requestedAt: '2026-11-04' });

    api.resubmitSecretaryRequest('117', technicalId, requestId, 3).subscribe();
    const resubmitted = http.expectOne(item => item.url.endsWith(`/secretary/round-two/${technicalId}/resubmit`));
    expect(resubmitted.request.method).toBe('POST');
    expect(resubmitted.request.params.get('unitCode')).toBe('117');
    expect(resubmitted.request.body).toEqual({ requestId, returnVersion: 3 });
    resubmitted.flush({ requestId, technicalId, unitCode: '117', status: 'PENDING',
      returnVersion: 3, expectedCycle: 4, reason: 'Corrected reason',
      returnReason: 'Replace the PDF', requestedAt: '2026-11-04' });
  });

  it('previews staged PDFs using the secretary-only endpoint', () => {
    api.secretaryPreview('117', technicalId, requestId, 'pdf-node').subscribe(result =>
      expect(result.type).toBe('application/pdf'));
    const req = http.expectOne(item => item.url.endsWith(
      `/secretary/round/${technicalId}/requests/${requestId}/documents/pdf-node`));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('unitCode')).toBe('117');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['%PDF-1.5'], { type: 'application/pdf' }));
  });

  it('rejects non-PDF uploads locally without issuing any HTTP call', () => {
    expect(() => api.uploadSecretaryDocument('117', technicalId, requestId,
      new File(['data'], 'not-a-pdf.txt', { type: 'text/plain' }))).toThrow('PDF required');
    http.expectNone(item => item.url.includes('/secretary/round/'));
  });

  it("lists and removes only a secretary request’s staged PDFs", () => {
    api.secretaryDocuments('117', technicalId, requestId).subscribe();
    const list = http.expectOne(item => item.url.endsWith(`/secretary/round/${technicalId}/requests/${requestId}/documents`));
    expect(list.request.method).toBe('GET');
    expect(list.request.params.get('unitCode')).toBe('117');
    list.flush({ items: [] });
    api.deleteSecretaryDocument('117', technicalId, requestId, 'pdf-node').subscribe();
    const deleted = http.expectOne(item => item.url.endsWith(`/secretary/round/${technicalId}/requests/${requestId}/documents/pdf-node`));
    expect(deleted.request.method).toBe('DELETE');
    expect(deleted.request.params.get('unitCode')).toBe('117');
    deleted.flush(null);
  });

  it('reads pending requests only for the explicitly authorized manager unit', () => {
    api.pending('117', 10, 10).subscribe(result => expect(result.total).toBe(12));
    const req = http.expectOne(item => item.url.endsWith('/manager/round-continuations'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('unitCode')).toBe('117');
    expect(req.request.params.get('offset')).toBe('10');
    expect(req.request.params.get('limit')).toBe('10');
    req.flush({ items: [], total: 12, offset: 10, limit: 10 });
  });

  it('verifies the current request ID and cycle before a decision', () => {
    api.current('117', technicalId).subscribe(result => expect(result.expectedCycle).toBe(3));
    const req = http.expectOne(item => item.url.endsWith(`/round-continuation/${technicalId}`));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('unitCode')).toBe('117');
    req.flush({ requestId, expectedCycle: 3, ownerEmployeeId: '300', reason: 'Additional review', status: 'PENDING' });
  });

  it('approves Round N with only exact server-required fields', () => {
    api.decide('117', technicalId, requestId, 'APPROVE_AND_CONTINUE', undefined, 7).subscribe();
    const req = http.expectOne(item => item.url.endsWith(`/round-continuation/${technicalId}`));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ unitCode: '117', requestId,
      action: 'APPROVE_AND_CONTINUE', documentsRevision: 7 });
    req.flush({ requestId, status: 'APPROVED', currentWorkflowCycle: 4 });
  });

  it('refuses manager approval without a reviewed document revision', () => {
    expect(() => api.decide('117', technicalId, requestId, 'APPROVE_AND_CONTINUE'))
      .toThrow('reviewed document revision');
    http.expectNone(item => item.url.endsWith(`/round-continuation/${technicalId}`));
  });

  it('returns the same request with an explicit reason without advancing the cycle', () => {
    api.decide('117', technicalId, requestId, 'RETURN', 'Attach the revised report').subscribe();
    const req = http.expectOne(item => item.url.endsWith(`/round-continuation/${technicalId}`));
    expect(req.request.body).toEqual({ unitCode: '117', requestId, action: 'RETURN', reason: 'Attach the revised report' });
    req.flush({ requestId, status: 'RETURNED', currentWorkflowCycle: 3 });
  });
});
