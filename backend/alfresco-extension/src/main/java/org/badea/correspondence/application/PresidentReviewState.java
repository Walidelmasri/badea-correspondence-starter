package org.badea.correspondence.application;

import java.util.Objects;

public record PresidentReviewState(
        long revision,
        String json) {

    public PresidentReviewState {

        if (revision < 0L) {
            throw new IllegalArgumentException(
                    "revision must not be negative");
        }

        Objects.requireNonNull(
                json,
                "json must not be null");

        if (json.isBlank()) {
            throw new IllegalArgumentException(
                    "json must not be blank");
        }
    }
}