import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TarasolDelegationApiService } from './tarasol-delegation-api.service';

describe('TarasolDelegationApiService', () => {
  let api: TarasolDelegationApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(TarasolDelegationApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('only requests the authenticated employee\'s exact-grant handoffs for one unit', () => {
    api.handoffs('118').subscribe(result => expect(result.items.length).toBe(1));
    const req = http.expectOne(request => request.url === '/alfresco/service/badea/tarasol/delegation/cases'
      && request.params.get('unitCode') === '118');
    expect(req.request.method).toBe('GET');
    req.flush({ items: [{ technicalId: '7fc41b5b-aceb-48fc-9f36-367e4a36e207',
      subject: 'Delegated report', state: 'WITH_DIRECTOR' }] });
  });
});
