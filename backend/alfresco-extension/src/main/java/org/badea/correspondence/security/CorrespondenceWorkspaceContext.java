package org.badea.correspondence.security;

import java.util.Objects;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/**
 * Single server-side boundary for correspondence role and organizational scope.
 * Alfresco decides the role. A Director unit must be backed by a current
 * Tarasol Oracle manager appointment; the employee's HR department is never
 * an authorization shortcut. Executive Office always uses institution unit 101.
 */
public final class CorrespondenceWorkspaceContext {
    private CorrespondenceWorkspaceContext() { }

    public static CorrespondenceActorRoleResolver.Workspace resolve(
            CorrespondenceActorRoleResolver resolver, String requestedUnit,
            EmployeeDirectoryEntry employee) {
        Objects.requireNonNull(resolver);
        Objects.requireNonNull(employee);
        if (requestedUnit != null) {
            var selected = resolver.resolveWorkspace(requestedUnit, employee);
            if (selected == null) throw denied();
            return selected;
        }
        var role = resolver.resolveCurrentUserRole();
        if (role == CorrespondenceActorRole.DIRECTOR) {
            // The production resolver validates a *current* Oracle appointment,
            // selecting a unique managed unit or rejecting ambiguity/stale groups.
            var managed = resolver.resolveWorkspace(null, employee);
            if (managed == null || managed.role() != CorrespondenceActorRole.DIRECTOR
                    || managed.unit() == null || "101".equals(managed.unit().value())) {
                throw denied();
            }
            return managed;
        }
        if (role == CorrespondenceActorRole.PRESIDENT_OFFICE) {
            return new CorrespondenceActorRoleResolver.Workspace(role,
                new DepartmentCode("101"));
        }
        if (role == CorrespondenceActorRole.PRESIDENT) {
            return new CorrespondenceActorRoleResolver.Workspace(role,
                employee.department().map(d -> d.code()).orElse(null));
        }
        throw denied();
    }

    private static CorrespondenceAuthorizationException denied() {
        return new CorrespondenceAuthorizationException("No authorized correspondence workspace");
    }
}
