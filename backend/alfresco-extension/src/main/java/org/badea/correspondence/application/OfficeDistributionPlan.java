package org.badea.correspondence.application;

import java.util.List;
import java.util.Objects;

/** Frozen, reconciled instructions: never edits President or Office source snapshots. */
public record OfficeDistributionPlan(
        long presidentRevision,
        long officeRevision,
        DistributionRecipients recipients,
        List<String> actionCodes,
        String presidentDirections,
        String officeDirections,
        String administrativeNotes,
        String otherRecipient,
        String otherAction) {
    public OfficeDistributionPlan {
        if (presidentRevision < 1 || officeRevision < 1) {
            throw new IllegalArgumentException("Both source snapshots must be persisted");
        }
        Objects.requireNonNull(recipients);
        actionCodes = List.copyOf(Objects.requireNonNull(actionCodes));
        presidentDirections = Objects.requireNonNullElse(presidentDirections, "");
        officeDirections = Objects.requireNonNullElse(officeDirections, "");
        administrativeNotes = Objects.requireNonNullElse(administrativeNotes, "");
        otherRecipient = Objects.requireNonNullElse(otherRecipient, "");
        otherAction = Objects.requireNonNullElse(otherAction, "");
    }
}
