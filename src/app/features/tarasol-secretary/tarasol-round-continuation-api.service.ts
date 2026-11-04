import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

const QUEUE_URL = '/alfresco/service/badea/tarasol/manager/round-continuations';
const DECISION_URL = '/alfresco/service/badea/tarasol/manager/round-continuation';

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

export interface CurrentRoundContinuation {
  readonly requestId: string;
  readonly expectedCycle: number;
  readonly ownerEmployeeId: string;
  readonly reason: string;
  readonly status: 'PENDING';
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

  current(unitCode: string, technicalId: string): Observable<CurrentRoundContinuation> {
    return this.http.get<CurrentRoundContinuation>(
      `${DECISION_URL}/${encodeURIComponent(technicalId)}`,
      { params: new HttpParams().set('unitCode', unitCode) },
    );
  }

  decide(unitCode: string, technicalId: string, requestId: string,
    action: 'APPROVE_AND_CONTINUE' | 'RETURN', reason?: string): Observable<RoundContinuationDecision> {
    return this.http.post<RoundContinuationDecision>(
      `${DECISION_URL}/${encodeURIComponent(technicalId)}`,
      action === 'RETURN'
        ? { unitCode, requestId, action, reason }
        : { unitCode, requestId, action },
    );
  }
}
