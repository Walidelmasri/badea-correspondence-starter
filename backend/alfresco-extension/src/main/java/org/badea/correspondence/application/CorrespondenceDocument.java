package org.badea.correspondence.application;

import java.util.Objects;

public record CorrespondenceDocument(
        String nodeId,
        String filename,
        String mimeType,
        long sizeBytes) {

    public CorrespondenceDocument {

        Objects.requireNonNull(
                nodeId,
                "nodeId must not be null");

        Objects.requireNonNull(
                filename,
                "filename must not be null");

        Objects.requireNonNull(
                mimeType,
                "mimeType must not be null");

        if (nodeId.isBlank()
                || filename.isBlank()
                || mimeType.isBlank()) {

            throw new IllegalArgumentException(
                    "Document identity, filename and MIME type must not be blank.");
        }

        if (sizeBytes < 0) {

            throw new IllegalArgumentException(
                    "Document size must not be negative.");
        }
    }
}