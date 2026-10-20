package org.badea.correspondence.application;

/** Stale or invalid continuation: no write has occurred. Safe HTTP 409. */
public final class OfficeCycleConflictException extends RuntimeException {
    public OfficeCycleConflictException(String message) { super(message); }
}
