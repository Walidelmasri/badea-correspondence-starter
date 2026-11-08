import { CorrespondenceDocumentUpload } from '../../data-access/correspondence-api.service';

export interface SourceDocumentRoundGroup {
  readonly cycle: number | null;
  readonly documents: readonly CorrespondenceDocumentUpload[];
}

/** Never infer workflow rounds from filenames or upload ordering. */
export function groupSourceDocuments(
  documents: readonly CorrespondenceDocumentUpload[],
): readonly SourceDocumentRoundGroup[] {
  const byCycle = new Map<number, CorrespondenceDocumentUpload[]>();
  const unclassified: CorrespondenceDocumentUpload[] = [];
  for (const document of documents) {
    const cycle = document.workflowCycle;
    if (cycle != null && Number.isSafeInteger(cycle) && cycle > 0) {
      const group = byCycle.get(cycle) ?? [];
      group.push(document);
      byCycle.set(cycle, group);
    } else {
      unclassified.push(document);
    }
  }
  const groups: SourceDocumentRoundGroup[] = [...byCycle.entries()]
    .sort((a, b) => b[0] - a[0])
    .map(([cycle, groupDocuments]) => ({ cycle, documents: groupDocuments }));
  if (unclassified.length) groups.push({ cycle: null, documents: unclassified });
  return groups;
}

export function sourceDocumentsForRound(
  documents: readonly CorrespondenceDocumentUpload[],
  currentWorkflowCycle: number | undefined,
): readonly CorrespondenceDocumentUpload[] {
  if (!currentWorkflowCycle || !Number.isSafeInteger(currentWorkflowCycle)) return [];
  return documents.filter(document => document.workflowCycle === currentWorkflowCycle);
}

/** Keep the active round's PDFs next to the finalized packet, without duplicating
 * them in the collapsible older/unclassified document list. Legacy PDFs stay
 * visible there because their round cannot be inferred from their filenames.
 */
export function historicalSourceDocumentGroups(
  documents: readonly CorrespondenceDocumentUpload[],
  currentWorkflowCycle: number | undefined,
): readonly SourceDocumentRoundGroup[] {
  if (currentWorkflowCycle == null || !Number.isSafeInteger(currentWorkflowCycle)
      || currentWorkflowCycle < 2) {
    return groupSourceDocuments(documents);
  }
  return groupSourceDocuments(documents.filter(
    document => document.workflowCycle !== currentWorkflowCycle,
  ));
}
