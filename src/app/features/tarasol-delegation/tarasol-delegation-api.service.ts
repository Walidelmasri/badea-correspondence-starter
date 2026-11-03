import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface DelegatedCase {
  readonly technicalId: string;
  readonly unitCode: string;
  readonly subject: string;
  readonly state: string;
  readonly documentNumber: string | null;
  readonly bodyText: string | null;
  readonly documents: readonly {
    readonly nodeId: string;
    readonly filename: string;
    readonly mimeType: string;
    readonly sizeBytes: number;
  }[];
}

@Injectable({ providedIn: 'root' })
export class TarasolDelegationApiService {
  private readonly http = inject(HttpClient);
  private url(technicalId: string): string {
    return `/alfresco/service/badea/tarasol/delegation/case/${encodeURIComponent(technicalId)}`;
  }
  handoffs(unitCode: string): Observable<{ readonly items: readonly {
    technicalId: string; subject: string; state: string;
  }[] }> {
    return this.http.get<{ readonly items: readonly {
      technicalId: string; subject: string; state: string;
    }[] }>(`/alfresco/service/badea/tarasol/delegation/cases`, {
      params: new HttpParams().set('unitCode', unitCode),
    });
  }

  open(unitCode: string, technicalId: string): Observable<DelegatedCase> {
    return this.http.get<DelegatedCase>(this.url(technicalId), {
      params: new HttpParams().set('unitCode', unitCode),
    });
  }
  pdf(unitCode: string, technicalId: string, nodeId: string): Observable<Blob> {
    return this.http.get(`${this.url(technicalId)}/documents/${encodeURIComponent(nodeId)}`, {
      params: new HttpParams().set('unitCode', unitCode), responseType: 'blob',
    });
  }
  act(unitCode: string, technicalId: string, action: 'SEND_TO_OFFICE' | 'MARK_DONE'):
    Observable<{ok: boolean; state: string}> {
    return this.http.post<{ok: boolean; state: string}>(this.url(technicalId), {
      unitCode, action,
    });
  }
}
