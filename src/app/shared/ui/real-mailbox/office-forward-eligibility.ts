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
  if (box !== 'inbox' || role !== 'PRESIDENT_OFFICE' ||
      state !== 'WITH_PRESIDENT_OFFICE' || !history?.length) return false;
  const last = history[history.length - 1];
  return last.actionType === 'SEND_TO_OFFICE' &&
    last.actorRole === 'DIRECTOR' &&
    last.toState === 'WITH_PRESIDENT_OFFICE';
}
