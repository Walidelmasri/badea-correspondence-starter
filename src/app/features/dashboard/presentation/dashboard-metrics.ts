import {
  CorrespondenceDraftPage,
  CorrespondenceInboxView,
  CorrespondenceMailboxPage,
  CorrespondenceMailboxSummary,
} from '../../../shared/data-access/correspondence-api.service';

export type DashboardWorkloadKey = Exclude<CorrespondenceInboxView, 'all'>;

export interface DashboardWorkloadSegment {
  readonly key: DashboardWorkloadKey;
  readonly total: number;
  readonly percentage: number;
}

export interface DashboardSnapshot {
  readonly received: number;
  readonly needsAction: number;
  readonly handled: number;
  readonly archived: number;
  // Null when the authorized personal Sent/Drafts APIs are not available.
  readonly sent: number | null;
  readonly drafts: number | null;
  readonly urgent: readonly CorrespondenceMailboxSummary[];
  readonly recent: readonly CorrespondenceMailboxSummary[];
  readonly segments: readonly DashboardWorkloadSegment[];
}

/**
 * Totals are computed by Alfresco's role-aware mailbox queries, not from the
 * small preview pages. Never extrapolate counts from paginated items.
 */
/** Secretary Dashboard uses the existing visual template, but must never
 * request Director-only Sent/Drafts or fabricate totals for those boxes. */
export function buildSecretaryDashboardSnapshot(
  all: CorrespondenceMailboxPage,
  needsAction: CorrespondenceMailboxPage,
  handled: CorrespondenceMailboxPage,
  archived: CorrespondenceMailboxPage,
  returnedCount = 0,
): DashboardSnapshot {
  const pages = [all, needsAction, handled, archived];
  if (pages.some(page => !Number.isSafeInteger(page.total) || page.total < 0)
      || !Number.isSafeInteger(returnedCount) || returnedCount < 0) {
    throw new Error('Invalid secretary dashboard total');
  }
  const parts = [
    { key: 'needs_action' as const, total: needsAction.total + returnedCount },
    { key: 'handled' as const, total: handled.total },
    { key: 'archived' as const, total: archived.total },
  ];
  const denominator = parts.reduce((sum, part) => sum + part.total, 0);
  return {
    received: all.total,
    needsAction: needsAction.total + returnedCount,
    handled: handled.total,
    archived: archived.total,
    sent: null,
    drafts: null,
    urgent: needsAction.items.slice(0, 4),
    recent: needsAction.items.slice(0, 7),
    segments: parts.map(part => ({
      ...part,
      percentage: denominator === 0 ? 0 : 100 * part.total / denominator,
    })),
  };
}

export function buildDashboardSnapshot(
  all: CorrespondenceMailboxPage,
  needsAction: CorrespondenceMailboxPage,
  handled: CorrespondenceMailboxPage,
  archived: CorrespondenceMailboxPage,
  sent: CorrespondenceMailboxPage,
  drafts: CorrespondenceDraftPage,
): DashboardSnapshot {
  const parts = [
    { key: 'needs_action' as const, total: needsAction.total },
    { key: 'handled' as const, total: handled.total },
    { key: 'archived' as const, total: archived.total },
  ];
  for (const page of [all, needsAction, handled, archived, sent, drafts]) {
    if (!Number.isSafeInteger(page.total) || page.total < 0) {
      throw new Error('Invalid mailbox dashboard total');
    }
  }
  // Independent mailbox reads are not one database snapshot; use the sum of
  // the distribution buckets to avoid incorrect percentages during transitions.
  const denominator = parts.reduce((sum, part) => sum + part.total, 0);
  return {
    received: all.total,
    needsAction: needsAction.total,
    handled: handled.total,
    archived: archived.total,
    sent: sent.total,
    drafts: drafts.total,
    urgent: needsAction.items.slice(0, 4),
    recent: needsAction.items.slice(0, 7),
    segments: parts.map(part => ({
      ...part,
      percentage: denominator === 0 ? 0 : 100 * part.total / denominator,
    })),
  };
}
