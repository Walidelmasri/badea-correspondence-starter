import { describe, expect, it } from 'vitest';
import { composeRouteForRole, inboxViewFromQuery, MAILBOX_PAGE_SIZE, mailboxPageNumbers, mailboxPageOffset } from './mailbox-navigation';

describe('correspondence creation navigation', () => {
  it('opens the separate Director, Office and President creation pages', () => {
    expect(composeRouteForRole('DIRECTOR')).toBe('/director/new');
    expect(composeRouteForRole('PRESIDENT_OFFICE')).toBe('/office/new');
    expect(composeRouteForRole('PRESIDENT')).toBe('/compose');
  });
  it('never exposes the composer to an unrecognized role', () => {
    expect(composeRouteForRole('SECRETARY')).toBe('/inbox');
    expect(composeRouteForRole(null)).toBe('/inbox');
  });
  it('builds a five-number window from server totals, not a client-side array', () => {
    expect(mailboxPageNumbers(0, 10, 100)).toEqual([1, 2, 3, 4, 5]);
    expect(mailboxPageNumbers(50, 10, 100)).toEqual([4, 5, 6, 7, 8]);
    expect(mailboxPageNumbers(90, 10, 100)).toEqual([6, 7, 8, 9, 10]);
    expect(mailboxPageNumbers(0, 10, 0)).toEqual([]);
    expect(mailboxPageNumbers(10, 10, 12)).toEqual([1, 2]);
  });
  it('converts page numbers to bounded server offsets', () => {
    expect(mailboxPageOffset(5, 10, 100)).toBe(40);
    expect(mailboxPageOffset(11, 10, 100)).toBeNull();
    expect(mailboxPageOffset(0, 10, 100)).toBeNull();
    expect(mailboxPageOffset(2, 10, 0)).toBeNull();
    expect(mailboxPageOffset(3, 10, 23)).toBe(20);
  });
  it('retains ten-item mailbox pagination', () => {
    expect(MAILBOX_PAGE_SIZE).toBe(10);
  });
  it('starts Inbox on needs action and preserves deep links to every filter', () => {
    expect(inboxViewFromQuery(null)).toBe('needs_action');
    expect(inboxViewFromQuery('unknown')).toBe('needs_action');
    for (const view of ['all', 'unread', 'needs_action', 'returned', 'handled', 'archived']) {
      expect(inboxViewFromQuery(view)).toBe(view);
    }
  });
});
