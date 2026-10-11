package org.badea.correspondence.application;

public final class PresidentReviewStateConflictException
        extends RuntimeException {

    public PresidentReviewStateConflictException(
            String message) {

        super(message);
    }
}