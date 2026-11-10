import { describe, expect, it } from 'vitest';
import {
  CorrespondenceDetails,
  CorrespondenceGeneratedDocument,
} from '../../../shared/data-access/correspondence-api.service';
import { previousFinalizedPdfDocuments } from './previous-finalized-documents';

const packet: CorrespondenceGeneratedDocument = {
  nodeId: 'packet-1', filename: 'final-r1.pdf', mimeType: 'application/pdf',
  sizeBytes: 1200, artifactType: 'CORRESPONDENCE_PACKET',
  sourceDocumentNodeId: null, reviewRevision: 3, officeRevision: 1,
};
const ink: CorrespondenceGeneratedDocument = {
  nodeId: 'annotated-1', filename: 'annotated-r1.pdf', mimeType: 'application/pdf',
  sizeBytes: 600, artifactType: 'ANNOTATED_PDF',
  sourceDocumentNodeId: 'source-1', reviewRevision: 3,
};
function details(cycle: number, generated: readonly CorrespondenceGeneratedDocument[]): CorrespondenceDetails {
  return {
    technicalId: 'case-1', subject: 'Case', bodyText: null, documentNumber: null,
    documentDate: null, sourceText: null, originDepartmentCode: '117',
    state: 'WITH_PRESIDENT', currentWorkflowCycle: cycle,
    createdByNameEnglish: 'Originator', createdByNameArabic: null,
    documents: [], generatedDocuments: generated,
  };
}

describe('President review previous finalized PDFs', () => {
  it('keeps Round 1 free of historical references', () => {
    expect(previousFinalizedPdfDocuments(details(1, [packet, ink]))).toEqual([]);
  });

  it('offers immutable packet and annotated PDF references in Round 2+', () => {
    const result = previousFinalizedPdfDocuments(details(2, [ink, packet]));
    expect(result.map((doc) => doc.nodeId)).toEqual(['packet-1', 'annotated-1']);
    expect(result[0]).toBe(packet);
  });

  it('excludes unreviewable generated artifacts and duplicate document IDs', () => {
    const result = previousFinalizedPdfDocuments(details(3, [
      packet,
      { ...packet },
      { ...ink, artifactType: 'HANDWRITTEN_EXPLANATION' },
      { ...ink, mimeType: 'image/png' },
      ink,
    ]));
    expect(result.map((doc) => doc.nodeId)).toEqual(['packet-1', 'annotated-1']);
  });

  it('fails closed without a reliable round or President workflow context', () => {
    expect(previousFinalizedPdfDocuments(null)).toEqual([]);
    expect(previousFinalizedPdfDocuments({ ...details(3, [packet]), currentWorkflowCycle: undefined })).toEqual([]);
    expect(previousFinalizedPdfDocuments({ ...details(3, [packet]), state: 'WITH_PRESIDENT_OFFICE' })).toEqual([]);
  });

  it('sorts the latest finalized review first without relying on filenames', () => {
    const newer: CorrespondenceGeneratedDocument = {
      ...packet, nodeId: 'newer-packet', filename: 'alpha.pdf',
      reviewRevision: 5, officeRevision: 1,
    };
    expect(previousFinalizedPdfDocuments(details(3, [packet, newer])).map(
      (doc) => doc.nodeId,
    )).toEqual(['newer-packet', 'packet-1']);
  });
});
