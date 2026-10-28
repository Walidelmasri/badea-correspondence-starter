import {
  CorrespondenceDetails,
  CorrespondenceHistoryEntry,
  CorrespondenceMailboxBox,
} from '../../data-access/correspondence-api.service';

/** UI affordance only. The Alfresco repository enforces this rule again. */
export function eligibleForInitialOfficeForward(
  box: CorrespondenceMailboxBox,
  role: string | null,
  state: CorrespondenceDetails['state'] | null,
  history: readonly CorrespondenceHistoryEntry[] | null,
): boolean {
  if (role !== 'PRESIDENT_OFFICE' ||
      state !== 'WITH_PRESIDENT_OFFICE' || !history?.length) return false;
  const last = history[history.length - 1];
  if (last.toState !== 'WITH_PRESIDENT_OFFICE') return false;
  // An Office-created draft is first submitted to the shared Office Inbox.
  // Authorized Office members can forward it from Inbox; its actual creator
  // can also reach it from personal Sent. Backend assignment checks still apply.
  if (last.actionType === 'SEND_TO_OFFICE' &&
      last.actorRole === 'PRESIDENT_OFFICE' && last.fromState === 'DRAFT') {
    return box === 'inbox' || box === 'sent';
  }
  if (box !== 'inbox') return false;
  return (last.actionType === 'SEND_TO_OFFICE' && last.actorRole === 'DIRECTOR')
    || (last.actionType === 'CONTINUE_CORRESPONDENCE' && last.actorRole === 'PRESIDENT_OFFICE'
      && last.fromState === 'WITH_DIRECTOR')
    || (last.actionType === 'DIRECTOR_CONTINUE_CORRESPONDENCE' && last.actorRole === 'DIRECTOR'
      && last.fromState === 'WITH_DIRECTOR');
}
