package org.badea.correspondence.infrastructure.alfresco;

/** Indicates that Alfresco ACLs prevented a complete institutional mailbox read. */
public final class CorrespondenceMailboxPermissionException extends RuntimeException {
    public CorrespondenceMailboxPermissionException() {
        super("The correspondence repository denied READ permission to this account");
    }
}
