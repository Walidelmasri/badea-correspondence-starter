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
