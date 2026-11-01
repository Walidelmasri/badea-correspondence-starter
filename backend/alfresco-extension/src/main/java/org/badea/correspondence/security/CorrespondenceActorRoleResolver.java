package org.badea.correspondence.security;

import org.badea.correspondence.workflow.CorrespondenceActorRole;
import java.util.Set;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

public interface CorrespondenceActorRoleResolver {

    CorrespondenceActorRole resolveCurrentUserRole();

    record Workspace(CorrespondenceActorRole role, DepartmentCode unit) { }

    /** A caller-supplied unit is never trusted without an active server-side assignment. */
    default Workspace resolveWorkspace(String requestedUnit, EmployeeDirectoryEntry employee) {
        if (requestedUnit != null) {
            throw new CorrespondenceAuthorizationException("Workspace selection is not available");
        }
        return new Workspace(resolveCurrentUserRole(),
            employee.department().map(d -> d.code()).orElse(null));
    }

    default Set<CorrespondenceActorRole> resolveCurrentUserRoles() {
        return Set.of(resolveCurrentUserRole());
    }
}