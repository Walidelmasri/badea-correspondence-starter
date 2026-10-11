package org.badea.correspondence.directory;

public final class DepartmentDirectoryException
        extends RuntimeException {

    public DepartmentDirectoryException(
            String message) {

        super(message);
    }

    public DepartmentDirectoryException(
            String message,
            Throwable cause) {

        super(
                message,
                cause);
    }
}