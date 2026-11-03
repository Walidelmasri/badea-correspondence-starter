package org.badea.correspondence.security;

import java.util.Objects;
import java.util.function.Supplier;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/**
 * Read projection for institutional shared mailboxes.
 *
 * Only Web Scripts that have first resolved the authenticated user's Alfresco
 * group and, where applicable, the selected Oracle manager workspace may call
 * this helper. Elevated access NEVER replaces the persisted correspondence
 * visibility policy in CorrespondenceAccessAuthorizer / MailboxLister. It
 * allows different members of the same executive group to see the same
 * delivered records even when the underlying DMS folders have creator ACLs.
 * The scope must not be used for mutations or direct DMS downloads.
 */
public final class InstitutionalMailboxReadScope {
    private InstitutionalMailboxReadScope() { }

    /**
     * Called ONLY after CorrespondenceWorkspaceContext validates the active
     * Director Oracle manager appointment and chooses its unit. The supplied
     * service still checks the persisted case assignment before returning data.
     * Without both checks, this method must never be used for Directors.
     */
    public static <T> T readValidatedWorkspace(CorrespondenceActorRole verifiedRole,
            Supplier<T> authorizedCaseRead) {
        if (verifiedRole == CorrespondenceActorRole.DIRECTOR) {
            return AuthenticationUtil.runAsSystem(authorizedCaseRead::get);
        }
        return read(verifiedRole, authorizedCaseRead);
    }

    public static <T> T read(CorrespondenceActorRole authenticatedRole, Supplier<T> authorizedRead) {
        Objects.requireNonNull(authenticatedRole);
        Objects.requireNonNull(authorizedRead);
        if (authenticatedRole == CorrespondenceActorRole.PRESIDENT
                || authenticatedRole == CorrespondenceActorRole.PRESIDENT_OFFICE) {
            return AuthenticationUtil.runAsSystem(authorizedRead::get);
        }
        return authorizedRead.get();
    }
}
