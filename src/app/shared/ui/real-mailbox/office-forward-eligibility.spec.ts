import { describe, expect, it } from 'vitest';
import { eligibleForInitialOfficeForward as eligible } from './office-forward-eligibility';
import { CorrespondenceHistoryEntry } from '../../data-access/correspondence-api.service';

const initial: CorrespondenceHistoryEntry = {
  sequence: 1, actionType: 'SEND_TO_OFFICE', actorRole: 'DIRECTOR',
  actorNameEnglish: 'Director', actorNameArabic: null,
  fromState: 'DRAFT', toState: 'WITH_PRESIDENT_OFFICE',
  actedAt: '2026-10-18T12:00:00Z', explanation: null,
};
const returned: CorrespondenceHistoryEntry = {
  ...initial, sequence: 3, actorRole: 'PRESIDENT', fromState: 'WITH_PRESIDENT',
};

describe('Office first-handoff eligibility', () => {
  it('allows Office Inbox after Director sends it', () => {
    expect(eligible('inbox', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE', [initial])).toBe(true);
  });
  it('rejects correspondence returned by President', () => {
    expect(eligible('inbox', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE', [initial, returned])).toBe(false);
  });
  it('rejects unknown history', () => {
    expect(eligible('inbox', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE', null)).toBe(false);
  });
  it('rejects Director trying to forward', () => {
    expect(eligible('inbox', 'DIRECTOR', 'WITH_PRESIDENT_OFFICE', [initial])).toBe(false);
  });
  it('rejects forwarding from Sent', () => {
    expect(eligible('sent', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE', [initial])).toBe(false);
  });
  it('rejects correspondence already with President', () => {
    expect(eligible('inbox', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT', [initial])).toBe(false);
  });
});
