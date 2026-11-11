/** Central route resolution for the correspondence creation action.
 * Unknown roles do not open a composer. This does not replace route guards. */
export type ComposeActorRole = 'DIRECTOR' | 'PRESIDENT_OFFICE' | 'PRESIDENT' | string | null | undefined;

export const MAILBOX_PAGE_SIZE = 10;

export function composeRouteForRole(role: ComposeActorRole): string {
  switch (role) {
    case 'DIRECTOR': return '/director/new';
    case 'PRESIDENT_OFFICE': return '/office/new';
    case 'PRESIDENT': return '/compose';
    default: return '/inbox';
  }
}

/** Default Inbox to actionable correspondence while honoring explicit filter links. */
export function inboxViewFromQuery(value: string | null): 'all' | 'unread' | 'needs_action' | 'handled' | 'archived' {
  switch (value) {
    case 'all':
    case 'unread':
    case 'needs_action':
    case 'handled':
    case 'archived':
      return value;
    default:
      return 'needs_action';
  }
}

/** Find a deep-linked case using only the caller's existing authorized list API. */
export async function findAuthorizedNotificationPage<T extends {
  readonly items: readonly { readonly technicalId: string }[];
  readonly total: number;
  readonly offset: number;
  readonly limit: number;
}>(first: T, technicalId: string, fetchPage: (offset: number) => Promise<T>,
  isCurrent: () => boolean): Promise<T> {
  if (first.items.some(item => item.technicalId === technicalId)) return first;
  const step = first.limit > 0 ? first.limit : MAILBOX_PAGE_SIZE;
  for (let offset = first.offset + step; offset < first.total; offset += step) {
    if (!isCurrent()) return first;
    const next = await fetchPage(offset);
    if (!isCurrent()) return first;
    if (next.items.some(item => item.technicalId === technicalId)) return next;
  }
  return first;
}
