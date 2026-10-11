package org.badea.correspondence.application;

import java.util.Objects;

import org.badea.correspondence.directory.EmployeeId;

public record SavePresidentReviewStateCommand(
        String technicalId,
        long expectedRevision,
        EmployeeId actorEmployeeId,
        String json) {

    public SavePresidentReviewStateCommand {

        Objects.requireNonNull(
                technicalId,
                "technicalId must not be null");

        if (technicalId.isBlank()) {
            throw new IllegalArgumentException(
                    "technicalId must not be blank");
        }

        if (expectedRevision < 0L) {
            throw new IllegalArgumentException(
                    "expectedRevision must not be negative");
        }

        Objects.requireNonNull(
                actorEmployeeId,
                "actorEmployeeId must not be null");

        Objects.requireNonNull(
                json,
                "json must not be null");

        if (json.isBlank()) {
            throw new IllegalArgumentException(
                    "json must not be blank");
        }
    }
}