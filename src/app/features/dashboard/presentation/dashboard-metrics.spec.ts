import { describe, expect, it } from 'vitest';
import {
  CorrespondenceDraftPage,
  CorrespondenceMailboxPage,
} from '../../../shared/data-access/correspondence-api.service';
import { buildDashboardSnapshot } from './dashboard-metrics';

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
