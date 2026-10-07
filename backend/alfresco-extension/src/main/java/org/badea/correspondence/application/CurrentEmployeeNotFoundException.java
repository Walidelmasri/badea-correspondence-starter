package org.badea.correspondence.application;

/**
 * Indicates that an authenticated user could not be resolved to an active
 * employee in BADEA's employee directory.
 */
public class CurrentEmployeeNotFoundException extends RuntimeException {

    public CurrentEmployeeNotFoundException(String username) {
        super(
            "No active employee directory entry was found for username: "
                + username
        );
    }
}