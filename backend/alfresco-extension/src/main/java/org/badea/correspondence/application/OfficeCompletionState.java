package org.badea.correspondence.application;

import java.time.Instant;
import java.util.Objects;

/** One persisted Office revision; presidentReviewRevision pins source provenance. */
public record OfficeCompletionState(
        long revision,
        long presidentReviewRevision,
        String json,
        String updatedByEmployeeId,
        String updatedByNameEnglish,
        String updatedByNameArabic,
        Instant updatedAt) {
    public OfficeCompletionState {
        if (revision < 1 || presidentReviewRevision < 0) {
            throw new IllegalArgumentException("Invalid completion or President revision");
        }
        Objects.requireNonNull(json);
        Objects.requireNonNull(updatedByEmployeeId);
        Objects.requireNonNull(updatedByNameEnglish);
        Objects.requireNonNull(updatedAt);
    }
}
