package org.badea.correspondence.application;

import java.time.Instant;
import java.util.Optional;

/** Reusable presidential mark. Registration is restricted by the web endpoint; only the
 * server-side distribution flow can read the stored strokes. No public download API. */
public interface PresidentSignatureStore {
    record Status(boolean configured, long revision, Instant updatedAt) {}
    Status status();
    Status register(PresidentSignatureDrawing signature, String employeeId, long expectedRevision);
    Optional<PresidentSignatureDrawing> loadForReview(String correspondenceId);
}
