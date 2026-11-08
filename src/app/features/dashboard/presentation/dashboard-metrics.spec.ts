import { describe, expect, it } from 'vitest';
import {
  CorrespondenceDraftPage,
  CorrespondenceMailboxPage,
  CorrespondenceMailboxSummary,
} from '../../../shared/data-access/correspondence-api.service';
import { buildDashboardSnapshot, buildSecretaryDashboardSnapshot } from './dashboard-metrics';

const inboxPage = (total: number): CorrespondenceMailboxPage =>
  ({ total, offset: 0, limit: 1, items: [] });
const draftPage = (total: number): CorrespondenceDraftPage =>
  ({ total, offset: 0, limit: 1, items: [] });

describe('dashboard metrics', () => {
  it('uses backend totals, not the limited correspondence previews', () => {
    const snapshot = buildDashboardSnapshot(
      inboxPage(210), inboxPage(25), inboxPage(165),
      inboxPage(20), inboxPage(80), draftPage(6),
    );
    expect(snapshot.received).toBe(210);
    expect(snapshot.needsAction).toBe(25);
    expect(snapshot.sent).toBe(80);
    expect(snapshot.drafts).toBe(6);
    expect(snapshot.segments.reduce((sum, segment) =>
      sum + segment.percentage, 0)).toBeCloseTo(100);
  });

  it('uses action-needed items for the main dashboard list without changing total received', () => {
    const item = (id: string): CorrespondenceMailboxSummary => ({
      technicalId: id, subject: id, documentNumber: null,
      createdAt: '2026-11-04T09:00:00Z', modifiedAt: '2026-11-04T09:00:00Z',
      state: 'WITH_DIRECTOR', originDepartmentCode: '117',
      senderName: 'Test employee', needsAction: true, unread: false, incomingSequence: 1,
    });
    const all = { ...inboxPage(20), items: [item('handled-old')] };
    const actionItems = Array.from({ length: 7 }, (_, index) => item(`action-${index}`));
    const actionable = { ...inboxPage(7), items: actionItems };
    const snapshot = buildDashboardSnapshot(
      all, actionable, inboxPage(13), inboxPage(0), inboxPage(4), draftPage(1),
    );
    expect(snapshot.received).toBe(20);
    expect(snapshot.needsAction).toBe(7);
    expect(snapshot.urgent.map(entry => entry.technicalId)).toEqual(
      ['action-0', 'action-1', 'action-2', 'action-3']);
    expect(snapshot.recent.map(entry => entry.technicalId)).toEqual(
      actionItems.map(entry => entry.technicalId));
  });

  it('uses secretary Inbox totals without inventing personal Sent and Draft counts', () => {
    const snapshot = buildSecretaryDashboardSnapshot(
      inboxPage(17), inboxPage(4), inboxPage(11), inboxPage(2),
    );
    expect(snapshot.received).toBe(17);
    expect(snapshot.needsAction).toBe(4);
    expect(snapshot.sent).toBeNull();
    expect(snapshot.drafts).toBeNull();
    const returned = buildSecretaryDashboardSnapshot(
      inboxPage(17), inboxPage(4), inboxPage(11), inboxPage(2), 2,
    );
    expect(returned.needsAction).toBe(6);
    expect(returned.received).toBe(17);
    expect(returned.segments[0].total).toBe(6);
    expect(snapshot.segments.reduce((total, segment) => total + segment.percentage, 0))
      .toBeCloseTo(100);
    expect(() => buildSecretaryDashboardSnapshot(
      inboxPage(2), inboxPage(-1), inboxPage(1), inboxPage(0),
    )).toThrow();
  });

  it('handles an empty repository without inventing metrics', () => {
    const empty = inboxPage(0);
    const snapshot = buildDashboardSnapshot(
      empty, empty, empty, empty, empty, draftPage(0),
    );
    expect(snapshot.received).toBe(0);
    expect(snapshot.segments.every(segment => segment.percentage === 0)).toBe(true);
  });

  it('rejects invalid server totals rather than displaying misleading results', () => {
    expect(() => buildDashboardSnapshot(
      inboxPage(1), inboxPage(-1), inboxPage(0),
      inboxPage(0), inboxPage(0), draftPage(0),
    )).toThrow('Invalid mailbox dashboard total');
  });
});
