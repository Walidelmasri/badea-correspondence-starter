import {
  CorrespondenceDetails,
  CorrespondenceGeneratedDocument,
} from '../../../shared/data-access/correspondence-api.service';

/** The President can view completed prior artifacts, but must not edit or carry
 * them into the current round. A round cannot be inferred from a filename.
 * While WITH_PRESIDENT in Round N, these finalized artifacts predate this review.
 */
export function previousFinalizedPdfDocuments(
  details: CorrespondenceDetails | null,
): readonly CorrespondenceGeneratedDocument[] {
  if (!details || details.state !== 'WITH_PRESIDENT' ||
      !Number.isSafeInteger(details.currentWorkflowCycle) ||
      (details.currentWorkflowCycle ?? 0) < 2) {
    return [];
  }

  const seen = new Set<string>();
  return (details.generatedDocuments ?? [])
    .filter((document) => {
      if (document.artifactType !== 'CORRESPONDENCE_PACKET' &&
          document.artifactType !== 'ANNOTATED_PDF') return false;
      if (document.mimeType !== 'application/pdf' || document.sizeBytes <= 0 ||
          !document.nodeId || seen.has(document.nodeId)) return false;
      seen.add(document.nodeId);
      return true;
    })
    .sort((left, right) =>
      right.reviewRevision - left.reviewRevision ||
      (right.officeRevision ?? 0) - (left.officeRevision ?? 0) ||
      Number(right.artifactType === 'CORRESPONDENCE_PACKET') -
        Number(left.artifactType === 'CORRESPONDENCE_PACKET') ||
      left.nodeId.localeCompare(right.nodeId),
    );
}
