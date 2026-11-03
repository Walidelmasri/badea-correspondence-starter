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
