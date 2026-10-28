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
  it('allows any authorized Office member to forward Office-created Inbox correspondence', () => {
    const officeOrigin: CorrespondenceHistoryEntry = {
      ...initial, actorRole: 'PRESIDENT_OFFICE',
    };
    expect(eligible('inbox', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE', [officeOrigin])).toBe(true);
    // Only the actual sender can see the item in personal Sent (server-enforced).
    expect(eligible('sent', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE', [officeOrigin])).toBe(true);
    expect(eligible('inbox', 'DIRECTOR', 'WITH_PRESIDENT_OFFICE', [officeOrigin])).toBe(false);
    expect(eligible('inbox', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT', [officeOrigin])).toBe(false);
  });
  it('rejects an unrelated Office-origin event', () => {
    const officeOrigin: CorrespondenceHistoryEntry = {
      ...initial, actorRole: 'PRESIDENT_OFFICE', fromState: 'WITH_DIRECTOR',
    };
    expect(eligible('inbox', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE', [officeOrigin])).toBe(false);
  });
  it('allows Office Inbox after Director sends it', () => {
    expect(eligible('inbox', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE', [initial])).toBe(true);
  });
  it('allows Office to forward after starting another workflow cycle', () => {
    const continued: CorrespondenceHistoryEntry = {
      ...initial, sequence: 5, actionType: 'CONTINUE_CORRESPONDENCE',
      actorRole: 'PRESIDENT_OFFICE', fromState: 'WITH_DIRECTOR',
    };
    expect(eligible('inbox', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE',
      [initial, returned, continued])).toBe(true);
    expect(eligible('sent', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE',
      [initial, returned, continued])).toBe(false);
  });
  it('allows an assigned Director to initiate a new round for Office review', () => {
    const followUp: CorrespondenceHistoryEntry = {
      ...initial, sequence: 6, actionType: 'DIRECTOR_CONTINUE_CORRESPONDENCE',
      actorRole: 'DIRECTOR', fromState: 'WITH_DIRECTOR',
    };
    expect(eligible('inbox', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE',
      [initial, returned, followUp])).toBe(true);
    expect(eligible('sent', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE',
      [initial, returned, followUp])).toBe(false);
  });
  it('rejects a malformed or unrelated continuation event', () => {
    const continued: CorrespondenceHistoryEntry = {
      ...initial, sequence: 5, actionType: 'CONTINUE_CORRESPONDENCE',
      actorRole: 'DIRECTOR', fromState: 'WITH_DIRECTOR',
    };
    expect(eligible('inbox', 'PRESIDENT_OFFICE', 'WITH_PRESIDENT_OFFICE',
      [initial, continued])).toBe(false);
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
