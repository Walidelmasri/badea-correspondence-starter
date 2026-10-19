package org.badea.correspondence.application;

import java.util.Objects;

/** Immutable content created during the President's submission. */
public record CorrespondenceGeneratedDocument(
        String nodeId,
        String filename,
        String mimeType,
        long sizeBytes,
        String artifactType,
        String sourceDocumentNodeId,
        long reviewRevision) {

    public CorrespondenceGeneratedDocument {
        Objects.requireNonNull(nodeId);
        Objects.requireNonNull(filename);
        Objects.requireNonNull(mimeType);
        Objects.requireNonNull(artifactType);
        if (sizeBytes < 0 || reviewRevision < 1) {
            throw new IllegalArgumentException("Invalid generated document metadata");
        }
    }
}
