import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TarasolSecretaryApiService } from './tarasol-secretary-api.service';

describe('TarasolSecretaryApiService', () => {
  let api: TarasolSecretaryApiService;
  let http: HttpTestingController;
  const id = '7fc41b5b-aceb-48fc-9f36-367e4a36e207';
  const pdf = '9fc41b5b-aceb-48fc-9f36-367e4a36e207';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(TarasolSecretaryApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('lists only an explicitly selected unit and bounded page', () => {
    api.list('118', 'mine', 20, 20).subscribe(result => {
      expect(result.items[0].approvalStatus).toBe('APPROVED');
      expect(result.total).toBe(21);
    });
    const request = http.expectOne(item => item.url === '/alfresco/service/badea/tarasol/secretary/workflow');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('unitCode')).toBe('118');
    expect(request.request.params.get('view')).toBe('mine');
    expect(request.request.params.get('offset')).toBe('20');
    expect(request.request.params.get('limit')).toBe('20');
    request.flush({ items: [{ technicalId: id, unitCode: '118', subject: 'Report', bodyText: null,
      ownerEmployeeId: '300', approvalStatus: 'APPROVED', returnReason: null }], total: 21, offset: 20, limit: 20 });
  });

  it('deletes only the named draft attachment under its own isolated endpoint', () => {
    api.deletePdf('118', id, pdf).subscribe();
    const req = http.expectOne(`/alfresco/service/badea/tarasol/secretary/${id}/documents/${pdf}?unitCode=118`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
