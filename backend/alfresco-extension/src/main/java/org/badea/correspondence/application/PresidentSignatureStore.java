package org.badea.correspondence.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** President-only reusable signature slots. Each signed review freezes its own immutable drawing. */
public interface PresidentSignatureStore {
    record Status(boolean configured, long revision, Instant updatedAt) {}
    record AuditEntry(int slot, long revision, String action, String actorEmployeeId, Instant at,
            String previousSha256, String newSha256) {}

    // Keep the original slot-one API compatible with existing callers and tests.
    Status status();
    Status register(PresidentSignatureDrawing signature, String employeeId, long expectedRevision);
    Status deactivate(String employeeId, long expectedRevision);
    Optional<PresidentSignatureDrawing> previewForOwner(String employeeId);
    Optional<PresidentSignatureDrawing> loadForReview(String correspondenceId);

    default Status status(int slot) {
        if (slot != 1) throw new IllegalArgumentException("Invalid signature slot");
        return status();
    }
    default Status register(int slot, PresidentSignatureDrawing drawing, String actor, long expectedRevision) {
        if (slot != 1) throw new IllegalArgumentException("Invalid signature slot");
        return register(drawing, actor, expectedRevision);
    }
    default Status deactivate(int slot, String actor, long expectedRevision) {
        if (slot != 1) throw new IllegalArgumentException("Invalid signature slot");
        return deactivate(actor, expectedRevision);
    }
    default Optional<PresidentSignatureDrawing> previewForOwner(int slot, String actor) {
        if (slot != 1) throw new IllegalArgumentException("Invalid signature slot");
        return previewForOwner(actor);
    }
    default List<AuditEntry> audit(int slot) { return List.of(); }

    /** Called inside the authenticated President SEND_TO_OFFICE transaction, never on autosave. */
    default void freezeForReview(String correspondenceId, String actorEmployeeId,
            long reviewRevision, int slot, long signatureRevision) {
        throw new UnsupportedOperationException("Signature snapshot storage is not configured");
    }

    /** Load exactly the drawing frozen for the supplied review revision. Never read the latest slot. */
    default Optional<PresidentSignatureDrawing> loadForReview(String correspondenceId, long reviewRevision) {
        return loadForReview(correspondenceId);
    }
}
