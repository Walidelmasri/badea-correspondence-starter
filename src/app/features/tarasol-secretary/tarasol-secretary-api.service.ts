import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface SecretaryDraft {
  readonly technicalId: string;
  readonly unitCode: string;
  readonly subject: string;
  readonly bodyText: string | null;
  readonly ownerEmployeeId: string;
  readonly approvalStatus: 'DRAFT' | 'PENDING' | 'RETURNED' | 'APPROVED';
  readonly returnReason?: string | null;
  readonly documentNumber: string | null;
  readonly createdAt: string;
  readonly modifiedAt: string;
}

export interface SecretaryPersonalDraft extends SecretaryDraft {
  readonly documentNumber: string | null;
  readonly documentDate: string | null;
  readonly sourceText: string | null;
}

export interface SecretaryPdf {
  readonly nodeId: string;
  readonly filename: string;
  readonly mimeType: string;
  readonly sizeBytes: number;
}

interface DraftList {
  readonly items: readonly SecretaryDraft[];
  readonly total: number;
  readonly offset: number;
  readonly limit: number;
}
interface WorkflowResponse {
  readonly ok: boolean;
  readonly technicalId?: string;
  readonly approvalStatus: string;
  readonly state?: string;
}

@Injectable({ providedIn: 'root' })
export class TarasolSecretaryApiService {
  private readonly http = inject(HttpClient);
  private readonly url = '/alfresco/service/badea/tarasol/secretary/workflow';

  personalDraft(unitCode: string, technicalId: string): Observable<SecretaryPersonalDraft> {
    return this.http.get<SecretaryPersonalDraft>(this.url, {
      params: new HttpParams().set('unitCode', unitCode)
        .set('view', 'mine').set('technicalId', technicalId),
    });
  }

  uploadPdf(unitCode: string, technicalId: string, file: File): Observable<{nodeId: string; filename: string}> {
    if (file.type !== 'application/pdf') {
      throw new Error('Only PDFs are supported');
    }
    const params = new HttpParams().set('unitCode', unitCode).set('filename', file.name);
    return this.http.post<{nodeId: string; filename: string}>(
      `/alfresco/service/badea/tarasol/secretary/${encodeURIComponent(technicalId)}/upload`,
      file, { params, headers: { 'Content-Type': 'application/pdf' } });
  }

  deletePdf(unitCode: string, technicalId: string, nodeId: string): Observable<void> {
    return this.http.delete<void>(
      `/alfresco/service/badea/tarasol/secretary/${encodeURIComponent(technicalId)}/documents/${encodeURIComponent(nodeId)}`,
      { params: new HttpParams().set('unitCode', unitCode) });
  }

  documents(unitCode: string, technicalId: string, view: 'mine' | 'pending'):
    Observable<{ readonly items: readonly SecretaryPdf[] }> {
    return this.http.get<{ readonly items: readonly SecretaryPdf[] }>(
      `/alfresco/service/badea/tarasol/secretary/${encodeURIComponent(technicalId)}/documents`,
      { params: new HttpParams().set('unitCode', unitCode).set('view', view) });
  }

  preview(unitCode: string, technicalId: string, nodeId: string, view: 'mine' | 'pending'):
    Observable<Blob> {
    return this.http.get(
      `/alfresco/service/badea/tarasol/secretary/${encodeURIComponent(technicalId)}/documents/${encodeURIComponent(nodeId)}`,
      { params: new HttpParams().set('unitCode', unitCode).set('view', view), responseType: 'blob' });
  }

  list(unitCode: string, view: 'mine' | 'pending', offset = 0, limit = 20,
    personalBox?: 'drafts'): Observable<DraftList> {
    const params = new HttpParams().set('unitCode', unitCode).set('view', view)
      .set('offset', offset).set('limit', limit);
    return this.http.get<DraftList>(this.url, {
      params: personalBox ? params.set('box', personalBox) : params,
    });
  }

  change(unitCode: string, action: 'CREATE_DRAFT' | 'REVISE' | 'SUBMIT' | 'RETURN' | 'APPROVE_AND_SEND',
    fields: Record<string, string | null>): Observable<WorkflowResponse> {
    return this.http.post<WorkflowResponse>(this.url, { unitCode, action, ...fields });
  }
}
