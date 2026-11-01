import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TarasolAccessApiService } from './tarasol-access-api.service';

describe('TarasolAccessApiService', () => {
  let api: TarasolAccessApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(TarasolAccessApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('searches only within the selected, server-authorized unit', () => {
    api.searchEmployees('admin', '118', 'Mohamed').subscribe(result => {
      expect(result.employees[0].employeeId).toBe('18284');
    });
    const req = http.expectOne(request => request.url === '/alfresco/service/badea/tarasol/admin/access' &&
      request.params.get('unitCode') === '118' && request.params.get('q') === 'Mohamed');
    expect(req.request.method).toBe('GET');
    req.flush({ employees: [{ employeeId: '18284', nameEnglish: "Mohamed N'Diack", nameArabic: null }] });
  });

  it('reads names next to immutable assignment IDs, without modifying records', () => {
    api.read('team', '118').subscribe(result => {
      expect(result.appointments[0].employeeId).toBe('18284');
      expect(result.employeesById?.['18284'].nameEnglish).toBe("Mohamed N'Diack");
    });
    const req = http.expectOne(request => request.url === '/alfresco/service/badea/tarasol/team/access' &&
      request.params.get('unitCode') === '118');
    req.flush({ appointments: [{ id: 7, unitCode: '118', employeeId: '18284', capacity: 'ACTING_MANAGER',
      startsAt: '2026-11-01', endsAt: null, revokedAt: null, active: true }], secretaries: [], delegations: [],
      employeesById: { '18284': { employeeId: '18284', nameEnglish: "Mohamed N'Diack", nameArabic: null } } });
  });
});
