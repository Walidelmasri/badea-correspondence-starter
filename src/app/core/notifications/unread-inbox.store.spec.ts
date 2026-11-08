import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { AuthSessionStore } from '../auth/auth-session.store';
import { CorrespondenceApiService } from '../../shared/data-access/correspondence-api.service';
import { UnreadInboxStore } from './unread-inbox.store';

const employee = {
  id: 'ndjack', firstName: 'Mohamed', displayName: 'Mohamed N\'Diack', enabled: true,
  capabilities: { isGuest: false, isAdmin: false, isMutable: false },
  employeeProfile: {
    employeeId: '18284', username: 'ndjack', nameEnglish: 'Mohamed N\'Diack',
    nameArabic: null, department: null, correspondenceRole: 'PRESIDENT_OFFICE' as const,
    managerWorkspaces: [
      { code: '101', nameEnglish: 'Office', nameArabic: null,
        capacity: 'MANAGER' as const, role: 'PRESIDENT_OFFICE' as const },
      { code: '118', nameEnglish: 'HR', nameArabic: null,
        capacity: 'ACTING_MANAGER' as const, role: 'DIRECTOR' as const },
    ],
  },
};

describe('UnreadInboxStore workspace isolation', () => {
  it('drops an in-flight Office response when the user switches to acting HR', async () => {
    const office = new Subject<any>();
    const hr = new Subject<any>();
    const api = { listMailbox: vi.fn()
      .mockReturnValueOnce(office.asObservable())
      .mockReturnValueOnce(hr.asObservable()) };
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: api },
    ] });
    const session = TestBed.inject(AuthSessionStore);
    session.setTicket('test');
    session.setCurrentUser(employee);
    const notifications = TestBed.inject(UnreadInboxStore);
    TestBed.tick();
    expect(api.listMailbox).toHaveBeenCalledTimes(1);

    session.selectWorkspace('118');
    TestBed.tick();
    expect(api.listMailbox).toHaveBeenCalledTimes(2);
    expect(notifications.total()).toBe(0);
    expect(notifications.items()).toEqual([]);

    office.next({ items: [{ technicalId: 'office-sensitive' }], total: 1 });
    office.complete();
    await Promise.resolve();
    expect(notifications.items()).toEqual([]);
    expect(notifications.total()).toBe(0);

    hr.next({ items: [{ technicalId: 'hr-authorized' }], total: 1 });
    hr.complete();
    await Promise.resolve();
    expect(notifications.items().map(item => item.technicalId)).toEqual(['hr-authorized']);
    expect(notifications.total()).toBe(1);
  });

  it('loads unread messages from the assigned secretary unit, not the Director endpoint', async () => {
    const api = { listMailbox: vi.fn(), listSecretaryInbox: vi.fn(() => of({
      items: [{ technicalId: 'secretary-unread' }], total: 1,
    })) };
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: api },
    ] });
    const session = TestBed.inject(AuthSessionStore);
    session.setTicket('test');
    session.setCurrentUser({ ...employee, employeeProfile: {
      ...employee.employeeProfile, correspondenceRole: 'SECRETARY' as const,
      managerWorkspaces: [],
      secretaryUnits: [{ code: '117', nameEnglish: 'DT', nameArabic: null }],
    } });
    const notifications = TestBed.inject(UnreadInboxStore);
    TestBed.tick();
    await Promise.resolve();
    expect(notifications.available()).toBe(true);
    expect(api.listSecretaryInbox).toHaveBeenCalledWith('117', 0, 5, 'unread');
    expect(api.listMailbox).not.toHaveBeenCalled();
    expect(notifications.items().map(item => item.technicalId)).toEqual(['secretary-unread']);
  });

  it('does not show late notifications from the previous secretary unit', async () => {
    const original = new Subject<any>();
    const switched = new Subject<any>();
    const api = { listMailbox: vi.fn(), listSecretaryInbox: vi.fn()
      .mockReturnValueOnce(original.asObservable())
      .mockReturnValueOnce(switched.asObservable()) };
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: api },
    ] });
    const session = TestBed.inject(AuthSessionStore);
    session.setTicket('test');
    session.setCurrentUser({ ...employee, employeeProfile: {
      ...employee.employeeProfile, correspondenceRole: 'SECRETARY' as const,
      managerWorkspaces: [], secretaryUnits: [
        { code: '117', nameEnglish: 'DT', nameArabic: null },
        { code: '118', nameEnglish: 'HR', nameArabic: null },
      ],
    } });
    const notifications = TestBed.inject(UnreadInboxStore);
    TestBed.tick();
    expect(api.listSecretaryInbox).toHaveBeenCalledWith('117', 0, 5, 'unread');
    session.selectSecretaryUnit('118');
    TestBed.tick();
    expect(api.listSecretaryInbox).toHaveBeenCalledWith('118', 0, 5, 'unread');
    original.next({ items: [{ technicalId: 'wrong-unit' }], total: 1 });
    original.complete();
    await Promise.resolve();
    expect(notifications.total()).toBe(0);
    switched.next({ items: [{ technicalId: 'correct-unit' }], total: 1 });
    switched.complete();
    await Promise.resolve();
    expect(notifications.items().map(item => item.technicalId)).toEqual(['correct-unit']);
    expect(api.listMailbox).not.toHaveBeenCalled();
  });

  it('immediately invalidates a stale notification panel when switching capacity', async () => {
    const api = { listMailbox: vi.fn().mockReturnValue(of({
      items: [{ technicalId: 'office-sensitive' }], total: 1,
    })) };
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: api },
    ] });
    const session = TestBed.inject(AuthSessionStore);
    session.setTicket('test');
    session.setCurrentUser(employee);
    const notifications = TestBed.inject(UnreadInboxStore);
    TestBed.tick();
    await Promise.resolve();
    notifications.toggle();
    expect(notifications.open()).toBe(true);

    session.selectWorkspace('118');
    notifications.invalidateForWorkspaceChange();
    expect(notifications.open()).toBe(false);
    expect(notifications.items()).toEqual([]);
    expect(notifications.total()).toBe(0);
  });
});
