package org.badea.correspondence.application;

import java.time.Instant;
import java.util.Optional;

/** Reusable presidential mark. Registration is restricted by the web endpoint; only the
 * server-side distribution flow can read the stored strokes. President-only previews are rasterized server-side. */
public interface PresidentSignatureStore {
    record Status(boolean configured, long revision, Instant updatedAt) {}
    Status status();
    Status register(PresidentSignatureDrawing signature, String employeeId, long expectedRevision);
    Status deactivate(String employeeId, long expectedRevision);
    Optional<PresidentSignatureDrawing> previewForOwner(String employeeId);
    Optional<PresidentSignatureDrawing> loadForReview(String correspondenceId);
}
