package org.badea.correspondence.application;

import java.util.Objects;
import org.badea.correspondence.directory.EmployeeId;

/** The authenticated employee is resolved by the server, never by the client. */
public record SaveOfficeCompletionCommand(
        String technicalId,
        long expectedRevision,
        long expectedPresidentReviewRevision,
        EmployeeId actorEmployeeId,
        String actorNameEnglish,
        String actorNameArabic,
        String json) {
    public SaveOfficeCompletionCommand {
        if (technicalId == null || technicalId.isBlank()
                || expectedRevision < 0 || expectedPresidentReviewRevision < 0) {
            throw new IllegalArgumentException("Invalid revision or correspondence ID");
        }
        Objects.requireNonNull(actorEmployeeId);
        if (actorNameEnglish == null || actorNameEnglish.isBlank()) {
            throw new IllegalArgumentException("Editor name is required");
        }
        Objects.requireNonNull(json);
    }
}
