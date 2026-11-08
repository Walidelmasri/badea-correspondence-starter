import { describe, expect, it } from 'vitest';
import { CorrespondenceDocumentUpload } from '../../data-access/correspondence-api.service';
import { groupSourceDocuments, historicalSourceDocumentGroups, sourceDocumentsForRound } from './source-document-rounds';

function doc(name: string, cycle?: number | null): CorrespondenceDocumentUpload {
  return { nodeId: name, filename: name, mimeType: 'application/pdf', sizeBytes: 40,
    ...(cycle !== undefined ? { workflowCycle: cycle } : {}) };
}

describe('source PDF workflow round grouping', () => {
  const first = doc('Round 1.pdf', 1);
  const second = doc('unrelated-name.pdf', 2);
  const historical = doc('Round 2.pdf');
  const files = [first, historical, second];

  it('uses persisted round metadata rather than the filename', () => {
    expect(groupSourceDocuments(files)).toEqual([
      { cycle: 2, documents: [second] },
      { cycle: 1, documents: [first] },
      { cycle: null, documents: [historical] },
    ]);
  });

  it('shows only the current round beside the latest follow-up', () => {
    expect(sourceDocumentsForRound(files, 2)).toEqual([second]);
    expect(sourceDocumentsForRound(files, 3)).toEqual([]);
    expect(sourceDocumentsForRound(files, undefined)).toEqual([]);
  });

  it('separates only current round PDFs from prior and unclassified source attachments', () => {
    expect(historicalSourceDocumentGroups(files, 2)).toEqual([
      { cycle: 1, documents: [first] },
      { cycle: null, documents: [historical] },
    ]);
    expect(historicalSourceDocumentGroups(files, 3)).toEqual(groupSourceDocuments(files));
    expect(historicalSourceDocumentGroups(files, undefined)).toEqual(groupSourceDocuments(files));
    expect(files).toEqual([first, historical, second]);
  });

  it('keeps Round 1 and legacy documents in the main collection without guessing their dates', () => {
    expect(historicalSourceDocumentGroups(files, 1)).toEqual(groupSourceDocuments(files));
    expect(historicalSourceDocumentGroups([historical], 2))
      .toEqual([{ cycle: null, documents: [historical] }]);
  });

  it('does not silently reclassify legacy PDFs', () => {
    expect(groupSourceDocuments([doc('Round 2.pdf'), doc('Round 1.pdf', null)]))
      .toEqual([{ cycle: null, documents: [doc('Round 2.pdf'), doc('Round 1.pdf', null)] }]);
  });
});
