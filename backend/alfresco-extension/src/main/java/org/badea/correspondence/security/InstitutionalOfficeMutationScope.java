package org.badea.correspondence.security;

import java.util.Objects;
import java.util.function.Supplier;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/**
 * Repository write scope for an authenticated President's Office member.
 * Callers must resolve the employee and the Alfresco/Oracle workspace before
 * invoking this scope. The supplied service must recheck the correspondence's
 * persisted Office assignment, President return, and revision before mutating.
 * This scope is never appropriate for personal draft editing.
 */
public final class InstitutionalOfficeMutationScope {
    private InstitutionalOfficeMutationScope() { }

    public static <T> T execute(CorrespondenceActorRole verifiedRole,
            Supplier<T> authorizedOfficeAction) {
        if (verifiedRole != CorrespondenceActorRole.PRESIDENT_OFFICE) {
            throw new CorrespondenceAuthorizationException(
                    "Only authenticated President's Office members may perform this action");
        }
        Objects.requireNonNull(authorizedOfficeAction);
        return AuthenticationUtil.runAsSystem(authorizedOfficeAction::get);
    }
}
