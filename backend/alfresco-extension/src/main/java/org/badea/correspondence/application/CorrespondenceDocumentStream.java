package org.badea.correspondence.application;

import java.io.InputStream;
import java.util.Objects;

public record CorrespondenceDocumentStream(
        String filename,
        String mimeType,
        InputStream content) {

    public CorrespondenceDocumentStream {

        Objects.requireNonNull(filename, "filename");

        Objects.requireNonNull(mimeType, "mimeType");

        Objects.requireNonNull(content, "content");

        if (filename.isBlank() || mimeType.isBlank()) {
            throw new IllegalArgumentException(
                    "Document filename and MIME type must not be blank.");
        }
    }
}