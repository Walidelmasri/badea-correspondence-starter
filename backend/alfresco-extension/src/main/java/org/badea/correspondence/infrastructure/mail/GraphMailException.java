package org.badea.correspondence.infrastructure.mail;

/** Safe operational error: deliberately never includes OAuth tokens or secrets. */
public final class GraphMailException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int status;

    public GraphMailException(String message, int status) {
        super(message);
        this.status = status;
    }

    public GraphMailException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public int status() { return status; }
}
