package org.badea.correspondence.security;

import java.util.Objects;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/** Director-originated executive metadata is available only to active dual managers.
 * Group authority AND both current Tarasol managerial appointments are mandatory.
 */
public final class DualManagerInitiationAuthorizer {
    private DualManagerInitiationAuthorizer() { }

    public static void requireAuthorized(
            EmployeeId employeeId, DepartmentCode originatingUnit,
            CorrespondenceActorRoleResolver roles,
            TarasolOrganizationReader assignments) {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(originatingUnit, "originatingUnit");
        if ("101".equals(originatingUnit.value()) || roles == null || assignments == null) {
            throw forbidden();
        }
        var groups = roles.resolveCurrentUserRoles();
        if (!groups.contains(CorrespondenceActorRole.DIRECTOR)
                || !groups.contains(CorrespondenceActorRole.PRESIDENT_OFFICE)
                || groups.contains(CorrespondenceActorRole.PRESIDENT)) {
            throw forbidden();
        }
        var workspaces = assignments.findCurrentManagerWorkspaces(employeeId);
        if (workspaces.stream().filter(w -> w.code().equals(originatingUnit)).count() != 1
                || workspaces.stream().filter(w -> "101".equals(w.code().value())).count() != 1) {
            throw forbidden();
        }
    }

    private static CorrespondenceAuthorizationException forbidden() {
        return new CorrespondenceAuthorizationException(
            "Dual-manager initiation requires current department and Office managerial appointments");
    }
}
