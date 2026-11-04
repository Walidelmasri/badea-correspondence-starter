package org.badea.correspondence.organization;

import java.util.Objects;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.security.AuthorityService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceMailboxLister;
import org.badea.correspondence.security.AuthenticatedUserProvider;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/**
 * Read-only departmental Inbox for appointed secretaries.
 * The Director projection is reused ONLY after independent Alfresco-group and
 * Oracle-unit checks. It never gives a secretary Director action authority.
 */
public final class TarasolSecretarySharedInboxService {
    private final TarasolEffectiveAccessReader access;
    private final AlfrescoCorrespondenceMailboxLister mailbox;
    private final AuthorityService authorities;
    private final AuthenticatedUserProvider authenticatedUser;

    public TarasolSecretarySharedInboxService(TarasolEffectiveAccessReader access,
            AlfrescoCorrespondenceMailboxLister mailbox,
            AuthorityService authorities, AuthenticatedUserProvider authenticatedUser) {
        this.access = Objects.requireNonNull(access);
        this.mailbox = Objects.requireNonNull(mailbox);
        this.authorities = Objects.requireNonNull(authorities);
        this.authenticatedUser = Objects.requireNonNull(authenticatedUser);
    }

    public AlfrescoCorrespondenceMailboxLister.MailboxPage list(
            EmployeeDirectoryEntry actor, DepartmentCode unit, String view,
            int offset, int limit) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(unit);
        if (!TarasolSecretaryGroupPolicy.isMember(authorities, authenticatedUser)
                || !access.hasActiveSecretaryAssignment(actor.employeeId(), unit)) {
            throw new CorrespondenceAuthorizationException("Secretary Inbox access denied");
        }
        if (view == null || !java.util.Set.of("all", "unread", "needs_action", "handled", "archived")
                .contains(view)) {
            throw new IllegalArgumentException("Invalid Inbox view");
        }
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid Inbox pagination");
        }
        // The existing lister enforces department assignment/participation.
        // runAsSystem is needed for institutional read projection because
        // underlying creator ACLs do not necessarily grant shared folder read.
        return AuthenticationUtil.runAsSystem(() -> mailbox.list("inbox",
            CorrespondenceActorRole.DIRECTOR, unit, actor.employeeId().value(),
            view, offset, limit));
    }
}
