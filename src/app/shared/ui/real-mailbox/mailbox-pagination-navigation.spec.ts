import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { UnreadInboxStore } from '../../../core/notifications/unread-inbox.store';
import { CorrespondenceService } from '../../../core/correspondence/correspondence.service';
import { TranslationService } from '../../../core/i18n/translation.service';
import { TarasolSecretaryApiService } from '../../../features/tarasol-secretary/tarasol-secretary-api.service';
import { TarasolRoundContinuationApiService } from '../../../features/tarasol-secretary/tarasol-round-continuation-api.service';
import { CorrespondenceApiService } from '../../data-access/correspondence-api.service';
import { RealMailboxComponent } from './real-mailbox.component';

const items = Array.from({ length: 32 }, (_, index) => ({
  technicalId: `test-case-${index + 1}`, subject: `Case ${index + 1}`,
  state: 'WITH_DIRECTOR' as const, modifiedAt: '2026-11-12T08:00:00Z',
  createdAt: '2026-11-12T08:00:00Z', originDepartmentCode: '117',
  documentNumber: null, senderName: 'Sender', needsAction: true,
  unread: false, incomingSequence: 0,
}));

describe('mailbox server pages and one-shot notification links', () => {
  let mailbox: RealMailboxComponent;
  let correspondence: { listMailbox: ReturnType<typeof vi.fn>; getDetails: ReturnType<typeof vi.fn> };
  let subject: BehaviorSubject<ParamMap>;
  let route: { snapshot: { queryParamMap: ParamMap }; queryParamMap: BehaviorSubject<ParamMap> };
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    subject = new BehaviorSubject(convertToParamMap({ view: 'all', open: 'test-case-16' }));
    route = { snapshot: { queryParamMap: subject.value }, queryParamMap: subject };
    navigate = vi.fn(async (_commands: unknown, options: { queryParams?: Record<string, string | null> }) => {
      const old = route.snapshot.queryParamMap;
      const merged: Record<string, string> = {};
      for (const key of old.keys) {
        const value = old.get(key);
        if (value !== null) merged[key] = value;
      }
      for (const [key, value] of Object.entries(options?.queryParams ?? {})) {
        if (value === null) delete merged[key];
        else merged[key] = value;
      }
      route.snapshot.queryParamMap = convertToParamMap(merged);
      subject.next(route.snapshot.queryParamMap);
      return true;
    });
    correspondence = {
      listMailbox: vi.fn((_box: string, offset: number, limit: number) => of({
        items: items.slice(offset, offset + limit), offset, limit, total: items.length,
      })),
      getDetails: vi.fn((id: string) => of({ technicalId: id, state: 'WITH_DIRECTOR',
        documents: [], generatedDocuments: [] })),
    };
    TestBed.configureTestingModule({ providers: [
      { provide: AuthSessionStore, useValue: {
        secretaryMailbox: () => false, secretaryInboxUnitCode: () => null,
        activeManagerWorkspace: () => null, availableWorkspaces: () => [],
        effectiveDepartmentCode: () => '117', effectiveRole: () => 'DIRECTOR',
      } },
      { provide: CorrespondenceApiService, useValue: {
        ...correspondence,
        getDepartmentAssignments: vi.fn(() => of({ items: [] })),
        getHistory: vi.fn(() => of({ items: [], total: 0 })),
      } },
      { provide: CorrespondenceService, useValue: { getDepartments: vi.fn(() => of([])) } },
      { provide: TarasolSecretaryApiService, useValue: { list: vi.fn() } },
      { provide: TarasolRoundContinuationApiService, useValue: { pending: vi.fn() } },
      { provide: TranslationService, useValue: { language: () => 'en' } },
      { provide: Router, useValue: { navigate } },
      { provide: ActivatedRoute, useValue: route },
      { provide: UnreadInboxStore, useValue: { refresh: vi.fn() } },
    ] });
    mailbox = TestBed.runInInjectionContext(() => new RealMailboxComponent());
    Object.defineProperty(mailbox, 'box', { value: () => 'inbox' });
  });

  it('opens a later authorized case then removes the one-time URL instruction', async () => {
    mailbox.ngOnInit();
    await vi.waitFor(() => expect(mailbox.selectedId()).toBe('test-case-16'));
    expect(mailbox.page()?.offset).toBe(10);
    expect(mailbox.page()?.items).toHaveLength(10);
    expect(route.snapshot.queryParamMap.get('open')).toBeNull();
    expect(correspondence.listMailbox).toHaveBeenCalledWith('inbox', 10, 10, 'all');

    mailbox.select('test-case-13');
    expect(mailbox.selectedId()).toBe('test-case-13');
    expect(mailbox.notificationTargetMissing()).toBe(false);
    await vi.waitFor(() => expect(mailbox.details()?.technicalId).toBe('test-case-13'));
  });

  it('switches filters and real server pages without reopening the emailed case', async () => {
    mailbox.ngOnInit();
    await vi.waitFor(() => expect(mailbox.page()?.offset).toBe(10));
    mailbox.setInboxView('handled');
    await vi.waitFor(() => expect(mailbox.page()?.offset).toBe(0));
    expect(route.snapshot.queryParamMap.get('view')).toBe('handled');
    expect(route.snapshot.queryParamMap.get('open')).toBeNull();
    mailbox.goToMailboxPage(3);
    await vi.waitFor(() => expect(mailbox.page()?.offset).toBe(20));
    expect(mailbox.page()?.items).toHaveLength(10);
    expect(correspondence.listMailbox).toHaveBeenCalledWith('inbox', 20, 10, 'handled');
    expect(mailbox.notificationTargetMissing()).toBe(false);
  });

  it('ignores a late email lookup after the user switches Inbox filters', async () => {
    const delayed = new Subject<{ items: typeof items; offset: number; limit: number; total: number }>();
    correspondence.listMailbox.mockImplementation((_box: string, offset: number, limit: number,
        view: string) => offset === 10 && view === 'all' ? delayed : of({
      items: items.slice(offset, offset + limit), offset, limit, total: items.length,
    }));
    mailbox.ngOnInit();
    await vi.waitFor(() => expect(correspondence.listMailbox).toHaveBeenCalledWith('inbox', 10, 10, 'all'));
    mailbox.setInboxView('handled');
    await vi.waitFor(() => expect(mailbox.inboxView()).toBe('handled'));
    delayed.next({ items: items.slice(10, 20), offset: 10, limit: 10, total: items.length });
    delayed.complete();
    await vi.waitFor(() => expect(mailbox.page()?.offset).toBe(0));
    expect(mailbox.selectedId()).not.toBe('test-case-16');
    expect(mailbox.notificationTargetMissing()).toBe(false);
    expect(route.snapshot.queryParamMap.get('open')).toBeNull();
  });

  it('clears an unavailable-notification error when the user opens another case', async () => {
    route.snapshot.queryParamMap = convertToParamMap({ open: 'not-in-mailbox', view: 'all' });
    subject.next(route.snapshot.queryParamMap);
    mailbox.ngOnInit();
    await vi.waitFor(() => expect(mailbox.notificationTargetMissing()).toBe(true));
    mailbox.select('test-case-1');
    expect(mailbox.notificationTargetMissing()).toBe(false);
    expect(mailbox.selectedId()).toBe('test-case-1');
    expect(route.snapshot.queryParamMap.get('open')).toBeNull();
  });
});
