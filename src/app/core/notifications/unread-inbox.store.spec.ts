import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { AuthSessionStore } from '../auth/auth-session.store';
import { CorrespondenceApiService } from '../../shared/data-access/correspondence-api.service';
import { UnreadInboxStore } from './unread-inbox.store';
import { TarasolSecretaryApiService } from '../../features/tarasol-secretary/tarasol-secretary-api.service';

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
      { provide: TarasolSecretaryApiService, useValue: {
        list: vi.fn(() => of({ items: [], total: 0, offset: 0, limit: 5 })),
      } },
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
      { provide: TarasolSecretaryApiService, useValue: {
        list: vi.fn(() => of({ items: [], total: 0, offset: 0, limit: 5 })),
      } },
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
      { provide: TarasolSecretaryApiService, useValue: {
        list: vi.fn(() => of({ items: [], total: 0, offset: 0, limit: 5 })),
      } },
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
      { provide: TarasolSecretaryApiService, useValue: {
        list: vi.fn(() => of({ items: [], total: 0, offset: 0, limit: 5 })),
      } },
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


describe('UnreadInboxStore personal workflow actions', () => {
  const returned = {
    technicalId: 'own-returned', unitCode: '117', subject: 'PDF correction',
    ownerEmployeeId: '18284', approvalStatus: 'RETURNED' as const,
    returnReason: 'Please replace attachment',
  };
  const pending = {
    technicalId: 'waiting-approval', unitCode: '118', subject: 'Manager decision',
    ownerEmployeeId: '99999', approvalStatus: 'PENDING' as const, returnReason: null,
  };
  it('shows only personally owned returned secretary actions with the reason and preserves unread mail', async () => {
    const secretaryApi = { list: vi.fn(() => of({ items: [returned, {
      ...returned, technicalId: 'someone-elses', ownerEmployeeId: 'different',
    }], total: 1, offset: 0, limit: 5 })) };
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: {
        listSecretaryInbox: vi.fn(() => of({ items: [{ technicalId: 'unread' }], total: 1 })),
      } },
      { provide: TarasolSecretaryApiService, useValue: secretaryApi },
    ] });
    const session = TestBed.inject(AuthSessionStore);
    session.setTicket('test');
    session.setCurrentUser({ ...employee, employeeProfile: {
      ...employee.employeeProfile, correspondenceRole: 'SECRETARY' as const,
      managerWorkspaces: [], secretaryUnits: [{ code: '117', nameEnglish: 'DT', nameArabic: null }],
    } });
    const notifications = TestBed.inject(UnreadInboxStore);
    TestBed.tick();
    await vi.waitFor(() => expect(notifications.actionItems().length).toBe(1));
    expect(secretaryApi.list).toHaveBeenCalledWith('117', 'mine', 0, 5, 'returned');
    expect(notifications.actionItems()[0]).toMatchObject({
      kind: 'returned', technicalId: 'own-returned', returnReason: 'Please replace attachment',
    });
    expect(notifications.total()).toBe(1);
    expect(notifications.badgeTotal()).toBe(2);
  });

  it('alerts an authorized manager about the exact pending queue without showing another unit', async () => {
    const secretaryApi = { list: vi.fn(() => of({ items: [pending], total: 1, offset: 0, limit: 5 })) };
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: {
        listMailbox: vi.fn(() => of({ items: [], total: 0 })),
      } },
      { provide: TarasolSecretaryApiService, useValue: secretaryApi },
    ] });
    const session = TestBed.inject(AuthSessionStore);
    session.setTicket('test');
    session.setCurrentUser(employee);
    session.selectWorkspace('118');
    const notifications = TestBed.inject(UnreadInboxStore);
    TestBed.tick();
    await vi.waitFor(() => expect(notifications.badgeTotal()).toBe(1));
    expect(secretaryApi.list).toHaveBeenCalledWith('118', 'pending', 0, 5);
    expect(notifications.actionItems()).toEqual([{
      kind: 'manager_pending', technicalId: 'waiting-approval', unitCode: '118',
      subject: 'Manager decision', returnReason: null,
    }]);
    session.selectWorkspace('101');
    TestBed.tick();
    await vi.waitFor(() => expect(notifications.actionItems()).toEqual([]));
  });

  it('shows unread mail even while a manager-approval queue is still loading', async () => {
    const delayedPending = new Subject<any>();
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: {
        listMailbox: vi.fn(() => of({ items: [{ technicalId: 'unread-now' }], total: 1 })),
      } },
      { provide: TarasolSecretaryApiService, useValue: {
        list: vi.fn(() => delayedPending.asObservable()),
      } },
    ] });
    const session = TestBed.inject(AuthSessionStore);
    session.setTicket('test');
    session.setCurrentUser(employee);
    session.selectWorkspace('118');
    const notifications = TestBed.inject(UnreadInboxStore);
    TestBed.tick();
    await vi.waitFor(() => expect(notifications.items().map(item => item.technicalId))
      .toEqual(['unread-now']));
    expect(notifications.total()).toBe(1);
    expect(notifications.actionTotal()).toBe(0);
    delayedPending.next({ items: [], total: 0, offset: 0, limit: 5 });
    delayedPending.complete();
  });

  it('never queries manager actions when there is no active manager appointment', async () => {
    const secretaryApi = { list: vi.fn() };
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: {
        listMailbox: vi.fn(() => of({ items: [], total: 0 })),
      } },
      { provide: TarasolSecretaryApiService, useValue: secretaryApi },
    ] });
    const session = TestBed.inject(AuthSessionStore);
    session.setTicket('test');
    session.setCurrentUser({ ...employee, employeeProfile: {
      ...employee.employeeProfile, correspondenceRole: 'DIRECTOR' as const, managerWorkspaces: [],
    } });
    const notifications = TestBed.inject(UnreadInboxStore);
    TestBed.tick();
    await vi.waitFor(() => expect(notifications.total()).toBe(0));
    expect(secretaryApi.list).not.toHaveBeenCalled();
  });

  it('drops in-flight returned actions after switching secretary units', async () => {
    const before = new Subject<any>();
    const after = new Subject<any>();
    const secretaryApi = { list: vi.fn()
      .mockReturnValueOnce(before.asObservable())
      .mockReturnValueOnce(after.asObservable()) };
    TestBed.configureTestingModule({ providers: [
      { provide: CorrespondenceApiService, useValue: {
        listSecretaryInbox: vi.fn(() => of({ items: [], total: 0 })),
      } },
      { provide: TarasolSecretaryApiService, useValue: secretaryApi },
    ] });
    const session = TestBed.inject(AuthSessionStore);
    session.setTicket('test');
    session.setCurrentUser({ ...employee, employeeProfile: {
      ...employee.employeeProfile, correspondenceRole: 'SECRETARY' as const, managerWorkspaces: [],
      secretaryUnits: [
        { code: '117', nameEnglish: 'DT', nameArabic: null },
        { code: '118', nameEnglish: 'HR', nameArabic: null },
      ],
    } });
    const notifications = TestBed.inject(UnreadInboxStore);
    TestBed.tick();
    session.selectSecretaryUnit('118');
    TestBed.tick();
    before.next({ items: [returned], total: 1 });
    before.complete();
    await Promise.resolve();
    expect(notifications.actionItems()).toEqual([]);
    after.next({ items: [], total: 0 });
    after.complete();
    await vi.waitFor(() => expect(notifications.badgeTotal()).toBe(0));
  });
});
