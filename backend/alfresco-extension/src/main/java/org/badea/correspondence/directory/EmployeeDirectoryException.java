package org.badea.correspondence.directory;

/**
 * Indicates that the employee directory could not provide a reliable result.
 */
public class EmployeeDirectoryException extends RuntimeException {

    public EmployeeDirectoryException(String message) {
        super(message);
    }

    public EmployeeDirectoryException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}