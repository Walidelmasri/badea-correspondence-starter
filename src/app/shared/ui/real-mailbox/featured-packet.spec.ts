import { describe, expect, it } from 'vitest';
import { CorrespondenceGeneratedDocument } from '../../data-access/correspondence-api.service';
import { chooseFeaturedPacket } from './featured-packet';

function artifact(nodeId: string, artifactType: CorrespondenceGeneratedDocument['artifactType'],
  reviewRevision: number): CorrespondenceGeneratedDocument {
  return {
    nodeId, artifactType, reviewRevision, filename: `${nodeId}.pdf`,
    mimeType: 'application/pdf', sizeBytes: 123, sourceDocumentNodeId: null,
  };
}

describe('chooseFeaturedPacket', () => {
  it('returns null when there is no complete packet', () => {
    expect(chooseFeaturedPacket(undefined)).toBeNull();
    expect(chooseFeaturedPacket([artifact('a', 'OFFICIAL_ACTION_SHEET', 4)])).toBeNull();
  });
  it('prefers the highest existing review revision, not the last array entry', () => {
    const packet1 = artifact('p1', 'CORRESPONDENCE_PACKET', 1);
    const packet4 = artifact('p4', 'CORRESPONDENCE_PACKET', 4);
    const documents = [packet4, artifact('sheet', 'OFFICIAL_ACTION_SHEET', 7), packet1];
    expect(chooseFeaturedPacket(documents)).toBe(packet4);
    expect(documents[0]).toBe(packet4);
    expect(documents[2]).toBe(packet1);
  });
  it('uses deterministic first-existing packet on equal revision without hiding other files', () => {
    const first = artifact('first', 'CORRESPONDENCE_PACKET', 3);
    const second = artifact('second', 'CORRESPONDENCE_PACKET', 3);
    expect(chooseFeaturedPacket([first, second])).toBe(first);
  });
});

// A later Office dispatch may reuse the same President review revision.
describe('chooseFeaturedPacket Office revisions', () => {
  it('prefers the later Office packet for an unchanged President revision', () => {
    const original = artifact('old', 'CORRESPONDENCE_PACKET', 5);
    const followUp = artifact('new', 'CORRESPONDENCE_PACKET', 5);
    (original as { officeRevision?: number }).officeRevision = 2;
    (followUp as { officeRevision?: number }).officeRevision = 3;
    expect(chooseFeaturedPacket([original, followUp])).toBe(followUp);
    expect(chooseFeaturedPacket([followUp, original])).toBe(followUp);
  });

  it('keeps legacy Office packets but prefers known later Office revisions', () => {
    const legacy = artifact('legacy', 'CORRESPONDENCE_PACKET', 5);
    const later = { ...artifact('new', 'CORRESPONDENCE_PACKET', 5), officeRevision: 2 };
    expect(chooseFeaturedPacket([legacy, later])).toBe(later);
  });
});
