import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

const QUEUE_URL = '/alfresco/service/badea/tarasol/manager/round-continuations';
const DECISION_URL = '/alfresco/service/badea/tarasol/manager/round-continuation';
const SECRETARY_ROUND_URL = '/alfresco/service/badea/tarasol/secretary/round';

export interface PendingRoundContinuation {
  readonly technicalId: string;
  readonly requestId: string;
  readonly unitCode: string;
  readonly ownerEmployeeId: string;
  readonly subject: string;
  readonly documentNumber: string | null;
  readonly reason: string;
  readonly expectedCycle: number;
  readonly requestedAt: string;
}

export interface PendingRoundContinuationPage {
  readonly items: readonly PendingRoundContinuation[];
  readonly total: number;
  readonly offset: number;
  readonly limit: number;
}

export interface ReviewedContinuationPdfs {
  readonly documentsRevision: number;
  readonly items: readonly StagedContinuationPdf[];
}

export interface CurrentRoundContinuation {
  readonly requestId: string;
  readonly expectedCycle: number;
  readonly ownerEmployeeId: string;
  readonly reason: string;
  readonly status: 'PENDING';
}

export interface StagedContinuationPdf {
  readonly nodeId: string;
  readonly filename: string;
  readonly mimeType: 'application/pdf';
  readonly sizeBytes: number;
  readonly workflowCycle: number;
}

export interface SecretaryContinuationEligibility {
  readonly canPrepare: boolean;
  readonly canReadRequest: boolean;
  readonly requestStatus: 'PREPARING' | 'PENDING' | 'RETURNED' | 'APPROVED' | null;
  readonly nextCycle: number;
  readonly reasonCode: 'READY' | 'OWN_REQUEST' | 'DEPARTMENT_NOT_READY' | 'REQUEST_CONFLICT';
}

export interface SecretaryContinuationRequest {
  readonly requestId: string;
  readonly technicalId: string;
  readonly unitCode: string;
  readonly status: 'PREPARING' | 'PENDING' | 'RETURNED' | 'APPROVED';
  readonly expectedCycle: number;
  /** Incremented on each manager return. Required to prevent stale resubmissions. */
  readonly returnVersion: number;
  readonly reason: string;
  readonly returnReason: string | null;
  readonly requestedAt: string;
}

export interface RoundContinuationDecision {
  readonly requestId: string;
  readonly status: 'APPROVED' | 'RETURNED';
  readonly currentWorkflowCycle: number;
}

@Injectable({ providedIn: 'root' })
export class TarasolRoundContinuationApiService {
  private readonly http = inject(HttpClient);

  pending(unitCode: string, offset = 0, limit = 10): Observable<PendingRoundContinuationPage> {
    const params = new HttpParams().set('unitCode', unitCode)
      .set('offset', offset).set('limit', limit);
    return this.http.get<PendingRoundContinuationPage>(QUEUE_URL, { params });
  }

  /** Only the appointed manager may read staged PDFs; normal document APIs must not see them. */
  managerDocuments(unitCode: string, technicalId: string, requestId: string):
    Observable<ReviewedContinuationPdfs> {
    return this.http.get<ReviewedContinuationPdfs>(
      `${DECISION_URL}/${encodeURIComponent(technicalId)}/documents`,
      { params: new HttpParams().set('unitCode', unitCode).set('requestId', requestId) });
  }

  managerPreview(unitCode: string, technicalId: string, requestId: string, nodeId: string): Observable<Blob> {
    return this.http.get(
      `${DECISION_URL}/${encodeURIComponent(technicalId)}/documents/${encodeURIComponent(nodeId)}`,
      { params: new HttpParams().set('unitCode', unitCode).set('requestId', requestId), responseType: 'blob' });
  }

  /** Backend decides availability from current department assignments and request state. */
  secretaryEligibility(unitCode: string, technicalId: string): Observable<SecretaryContinuationEligibility> {
    return this.http.get<SecretaryContinuationEligibility>(
      `/alfresco/service/badea/tarasol/secretary/round-two/${encodeURIComponent(technicalId)}/eligibility`,
      { params: new HttpParams().set('unitCode', unitCode) });
  }

  /** Secretary requests and PDFs are never routed through a Director transition endpoint. */
  secretaryRequest(unitCode: string, technicalId: string): Observable<SecretaryContinuationRequest> {
    return this.http.get<SecretaryContinuationRequest>(
      `/alfresco/service/badea/tarasol/secretary/round-two/${encodeURIComponent(technicalId)}`,
      { params: new HttpParams().set('unitCode', unitCode) });
  }

  submitSecretaryRequest(unitCode: string, technicalId: string, reason: string):
    Observable<SecretaryContinuationRequest> {
    return this.http.post<SecretaryContinuationRequest>(
      `/alfresco/service/badea/tarasol/secretary/round-two/${encodeURIComponent(technicalId)}`,
      { reason },
      { params: new HttpParams().set('unitCode', unitCode) });
  }

  /** Only this call publishes the completed PREPARING request to the manager queue. */
  submitPreparedSecretaryRequest(unitCode: string, technicalId: string, requestId: string):
    Observable<SecretaryContinuationRequest> {
    return this.http.post<SecretaryContinuationRequest>(
      `/alfresco/service/badea/tarasol/secretary/round-two/${encodeURIComponent(technicalId)}/submit`,
      { requestId },
      { params: new HttpParams().set('unitCode', unitCode) });
  }

  reviseSecretaryRequest(unitCode: string, technicalId: string, requestId: string,
    returnVersion: number, reason: string): Observable<SecretaryContinuationRequest> {
    return this.http.put<SecretaryContinuationRequest>(
      `/alfresco/service/badea/tarasol/secretary/round-two/${encodeURIComponent(technicalId)}`,
      { requestId, returnVersion, reason },
      { params: new HttpParams().set('unitCode', unitCode) });
  }

  resubmitSecretaryRequest(unitCode: string, technicalId: string, requestId: string,
    returnVersion: number): Observable<SecretaryContinuationRequest> {
    return this.http.post<SecretaryContinuationRequest>(
      `/alfresco/service/badea/tarasol/secretary/round-two/${encodeURIComponent(technicalId)}/resubmit`,
      { requestId, returnVersion },
      { params: new HttpParams().set('unitCode', unitCode) });
  }

  secretaryDocuments(unitCode: string, technicalId: string, requestId: string):
    Observable<{ readonly items: readonly StagedContinuationPdf[] }> {
    return this.http.get<{ readonly items: readonly StagedContinuationPdf[] }>(
      `${SECRETARY_ROUND_URL}/${encodeURIComponent(technicalId)}/requests/${encodeURIComponent(requestId)}/documents`,
      { params: new HttpParams().set('unitCode', unitCode) });
  }

  uploadSecretaryDocument(unitCode: string, technicalId: string, requestId: string, file: File):
    Observable<StagedContinuationPdf> {
    if (file.type !== 'application/pdf' || file.size === 0) throw new Error('PDF required');
    return this.http.post<StagedContinuationPdf>(
      `${SECRETARY_ROUND_URL}/${encodeURIComponent(technicalId)}/requests/${encodeURIComponent(requestId)}/documents`,
      file, { params: new HttpParams().set('unitCode', unitCode).set('filename', file.name),
        headers: { 'Content-Type': 'application/pdf' } });
  }

  deleteSecretaryDocument(unitCode: string, technicalId: string, requestId: string, nodeId: string):
    Observable<void> {
    return this.http.delete<void>(
      `${SECRETARY_ROUND_URL}/${encodeURIComponent(technicalId)}/requests/${encodeURIComponent(requestId)}/documents/${encodeURIComponent(nodeId)}`,
      { params: new HttpParams().set('unitCode', unitCode) });
  }

  secretaryPreview(unitCode: string, technicalId: string, requestId: string, nodeId: string): Observable<Blob> {
    return this.http.get(
      `${SECRETARY_ROUND_URL}/${encodeURIComponent(technicalId)}/requests/${encodeURIComponent(requestId)}/documents/${encodeURIComponent(nodeId)}`,
      { params: new HttpParams().set('unitCode', unitCode), responseType: 'blob' });
  }

  current(unitCode: string, technicalId: string): Observable<CurrentRoundContinuation> {
    return this.http.get<CurrentRoundContinuation>(
      `${DECISION_URL}/${encodeURIComponent(technicalId)}`,
      { params: new HttpParams().set('unitCode', unitCode) },
    );
  }

  decide(unitCode: string, technicalId: string, requestId: string,
    action: 'APPROVE_AND_CONTINUE' | 'RETURN', reason?: string,
    documentsRevision?: number): Observable<RoundContinuationDecision> {
    if (action === 'APPROVE_AND_CONTINUE'
        && (documentsRevision === undefined || !Number.isSafeInteger(documentsRevision) || documentsRevision < 0)) {
      throw new Error('A reviewed document revision is required before approval');
    }
    return this.http.post<RoundContinuationDecision>(
      `${DECISION_URL}/${encodeURIComponent(technicalId)}`,
      action === 'RETURN'
        ? { unitCode, requestId, action, reason }
        : { unitCode, requestId, action, documentsRevision },
    );
  }
}
