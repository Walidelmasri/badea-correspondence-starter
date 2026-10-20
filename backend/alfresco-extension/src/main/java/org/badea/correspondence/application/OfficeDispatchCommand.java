package org.badea.correspondence.application;

import org.badea.correspondence.directory.EmployeeId;
import java.util.Objects;

/** Identity comes from server-side Alfresco and Oracle authentication. */
public record OfficeDispatchCommand(String technicalId,
        long expectedPresidentRevision, long expectedOfficeRevision,
        EmployeeId actorEmployeeId, String actorNameEnglish, String actorNameArabic) {
    public OfficeDispatchCommand {
        if (technicalId == null || technicalId.isBlank() || expectedPresidentRevision < 1
                || expectedOfficeRevision < 1) throw new IllegalArgumentException("Invalid dispatch preconditions");
        Objects.requireNonNull(actorEmployeeId);
        if (actorNameEnglish == null || actorNameEnglish.isBlank())
            throw new IllegalArgumentException("Authenticated actor name required");
    }
}
