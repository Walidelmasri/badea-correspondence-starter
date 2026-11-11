import { describe, expect, it, vi } from 'vitest';
import { findAuthorizedNotificationPage } from './mailbox-navigation';

type Page = {
  readonly items: readonly { technicalId: string }[];
  readonly offset: number;
  readonly limit: number;
  readonly total: number;
};
const first: Page = {
  items: [{ technicalId: 'unrelated-1' }], offset: 0, limit: 1, total: 3,
};

describe('notification correspondence deep links', () => {
  it('finds an authorized case on a later mailbox page without selecting another item', async () => {
    const fetch = vi.fn(async (offset: number): Promise<Page> => ({
      items: [{ technicalId: offset === 1 ? 'unrelated-2' : 'requested-id' }],
      offset, limit: 1, total: 3,
    }));
    const result = await findAuthorizedNotificationPage(first, 'requested-id', fetch, () => true);
    expect(result.items[0].technicalId).toBe('requested-id');
    expect(result.offset).toBe(2);
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('does not invent access to a case not returned by the authorized mailbox', async () => {
    const fetch = vi.fn(async (offset: number): Promise<Page> => ({
      items: [{ technicalId: 'other-' + offset }], offset, limit: 1, total: 3,
    }));
    const result = await findAuthorizedNotificationPage(first, 'not-authorized', fetch, () => true);
    expect(result).toBe(first);
  });

  it('stops looking when an inbox view changes before an async page completes', async () => {
    let current = true;
    const fetch = vi.fn(async (offset: number): Promise<Page> => {
      current = false;
      return { items: [{ technicalId: 'requested-id' }], offset, limit: 1, total: 3 };
    });
    const result = await findAuthorizedNotificationPage(first, 'requested-id', fetch, () => current);
    expect(result).toBe(first);
  });
});
