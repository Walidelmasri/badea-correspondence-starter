import { CorrespondenceGeneratedDocument } from '../../data-access/correspondence-api.service';

/** Latest available finalized packet. Each Office distribution has its own Office revision.
 * Legacy documents without Office metadata remain accessible and sort behind known revisions.
 */
export function chooseFeaturedPacket(
  documents: readonly CorrespondenceGeneratedDocument[] | null | undefined,
): CorrespondenceGeneratedDocument | null {
  let featured: CorrespondenceGeneratedDocument | null = null;
  for (const document of documents ?? []) {
    if (document.artifactType !== 'CORRESPONDENCE_PACKET') continue;
    if (!featured || document.reviewRevision > featured.reviewRevision
        || (document.reviewRevision === featured.reviewRevision
            && (document.officeRevision ?? 0) > (featured.officeRevision ?? 0))) {
      featured = document;
    }
  }
  return featured;
}
