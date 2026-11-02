package org.badea.correspondence.infrastructure.alfresco;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.alfresco.service.cmr.security.AuthorityService;
import org.badea.correspondence.security.AuthenticatedUserProvider;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;

public final class AlfrescoCorrespondenceActorRoleResolver
        implements CorrespondenceActorRoleResolver {

    private static final String PRESIDENT_AUTHORITY =
            "GROUP_BADEA_CORRESPONDENCE_PRESIDENT";

    private static final String PRESIDENT_OFFICE_AUTHORITY =
            "GROUP_BADEA_CORRESPONDENCE_PRESIDENT_OFFICE";

    private static final String DIRECTORS_AUTHORITY =
            "GROUP_BADEA_CORRESPONDENCE_DIRECTORS";

    private final AuthorityService authorityService;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final TarasolOrganizationReader organizationReader;

    public AlfrescoCorrespondenceActorRoleResolver(
            AuthorityService authorityService,
            AuthenticatedUserProvider authenticatedUserProvider) {
        this(authorityService, authenticatedUserProvider, null);
    }

    public AlfrescoCorrespondenceActorRoleResolver(
            AuthorityService authorityService,
            AuthenticatedUserProvider authenticatedUserProvider,
            TarasolOrganizationReader organizationReader) {
        this.organizationReader = organizationReader;
        this.authorityService =
                Objects.requireNonNull(
                        authorityService,
                        "authorityService must not be null");

        this.authenticatedUserProvider =
                Objects.requireNonNull(
                        authenticatedUserProvider,
                        "authenticatedUserProvider must not be null");
    }

    @Override
    public Workspace resolveWorkspace(String requestedUnit, EmployeeDirectoryEntry employee) {
        Objects.requireNonNull(employee, "employee");
        if (requestedUnit == null) {
            CorrespondenceActorRole role = resolveCurrentUserRole();
            if (role != CorrespondenceActorRole.DIRECTOR || organizationReader == null) {
                // Office is group-controlled; President stays exclusive.
                return new Workspace(role,
                    employee.department().map(d -> d.code()).orElse(null));
            }
            // A stale Alfresco DIRECTORS group must not silently grant a revoked
            // Director access through the legacy unscoped fallback.
            var managed = organizationReader.findCurrentManagerWorkspaces(employee.employeeId())
                .stream().filter(w -> !"101".equals(w.code().value())).toList();
            if (managed.isEmpty()) {
                throw new CorrespondenceAuthorizationException("No active Tarasol Director appointment");
            }
            var preferred = managed.stream().filter(w -> employee.department()
                .map(d -> d.code().equals(w.code())).orElse(false)).toList();
            if (preferred.size() == 1) return new Workspace(role, preferred.get(0).code());
            if (managed.size() == 1) return new Workspace(role, managed.get(0).code());
            throw new CorrespondenceAuthorizationException("Select an authorized Director workspace");
        }
        // An explicit selection must be a known, active managerial appointment.
        // Never fall back to SAP department or Alfresco group alone.
        if (!requestedUnit.matches("[0-9]{3}") || organizationReader == null) {
            throw new CorrespondenceAuthorizationException("Invalid Tarasol workspace");
        }
        var groups = resolveCurrentUserRoles();
        if (groups.contains(CorrespondenceActorRole.PRESIDENT)) {
            throw new CorrespondenceAuthorizationException("President cannot select a managerial workspace");
        }
        Workspace resolved = null;
        for (var assignment : organizationReader.findCurrentManagerWorkspaces(employee.employeeId())) {
            if (!requestedUnit.equals(assignment.code().value())) continue;
            CorrespondenceActorRole target = "101".equals(requestedUnit)
                ? CorrespondenceActorRole.PRESIDENT_OFFICE : CorrespondenceActorRole.DIRECTOR;
            if (!groups.contains(target) || resolved != null) {
                throw new CorrespondenceAuthorizationException("Unauthorized or ambiguous managerial workspace");
            }
            resolved = new Workspace(target, assignment.code());
        }
        if (resolved == null && "101".equals(requestedUnit)
                && groups.contains(CorrespondenceActorRole.PRESIDENT_OFFICE)
                && organizationReader.findActiveUnits().stream()
                    .anyMatch(u -> "101".equals(u.code().value()))) {
            // Alfresco Office group grants Office access, but cannot become
            // an Office manager or obtain a departmental Director workspace.
            return new Workspace(CorrespondenceActorRole.PRESIDENT_OFFICE,
                new org.badea.correspondence.directory.DepartmentCode("101"));
        }
        if (resolved == null) {
            throw new CorrespondenceAuthorizationException("No active Tarasol appointment for workspace");
        }
        return resolved;
    }

    @Override
    public CorrespondenceActorRole resolveCurrentUserRole() {
        Set<CorrespondenceActorRole> roles = resolveCurrentUserRoles();
        // Retain the established Office mailbox for overlapping Office/Director
        // memberships. A Director workspace still requires an active Tarasol
        // manager assignment; group membership alone never grants a unit.
        if (roles.contains(CorrespondenceActorRole.PRESIDENT_OFFICE)) {
            return CorrespondenceActorRole.PRESIDENT_OFFICE;
        }
        return roles.iterator().next();
    }

    @Override
    public Set<CorrespondenceActorRole> resolveCurrentUserRoles() {

        String username =
                authenticatedUserProvider.getUsername();

        Set<String> authorities =
                authorityService.getAuthoritiesForUser(
                        username);

        EnumSet<CorrespondenceActorRole> roles =
                EnumSet.noneOf(
                        CorrespondenceActorRole.class);

        if (authorities.contains(
                PRESIDENT_AUTHORITY)) {

            roles.add(
                    CorrespondenceActorRole.PRESIDENT);
        }

        if (authorities.contains(
                PRESIDENT_OFFICE_AUTHORITY)) {

            roles.add(
                    CorrespondenceActorRole.PRESIDENT_OFFICE);
        }

        if (authorities.contains(
                DIRECTORS_AUTHORITY)) {

            roles.add(
                    CorrespondenceActorRole.DIRECTOR);
        }

        if (roles.isEmpty()) {
            throw new CorrespondenceAuthorizationException(
                    "Authenticated user has no correspondence workflow role.");
        }

        // President identity must never be combined with Office or Director
        // authority. A separate PRESIDENT role remains explicit and exclusive.
        if (roles.contains(CorrespondenceActorRole.PRESIDENT) && roles.size() > 1) {
            throw new CorrespondenceAuthorizationException(
                    "President workflow role cannot be combined with another role.");
        }

        return Set.copyOf(roles);
    }
}