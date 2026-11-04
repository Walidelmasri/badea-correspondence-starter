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
    api.decide('117', technicalId, requestId, 'APPROVE_AND_CONTINUE').subscribe();
    const req = http.expectOne(item => item.url.endsWith(`/round-continuation/${technicalId}`));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ unitCode: '117', requestId, action: 'APPROVE_AND_CONTINUE' });
    req.flush({ requestId, status: 'APPROVED', currentWorkflowCycle: 4 });
  });

  it('returns the same request with an explicit reason without advancing the cycle', () => {
    api.decide('117', technicalId, requestId, 'RETURN', 'Attach the revised report').subscribe();
    const req = http.expectOne(item => item.url.endsWith(`/round-continuation/${technicalId}`));
    expect(req.request.body).toEqual({ unitCode: '117', requestId, action: 'RETURN', reason: 'Attach the revised report' });
    req.flush({ requestId, status: 'RETURNED', currentWorkflowCycle: 3 });
  });
});
