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
            return new Workspace(resolveCurrentUserRole(),
                employee.department().map(d -> d.code()).orElse(null));
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
                && organizationReader.findCurrentOfficeMembership(employee.employeeId()).isPresent()) {
            // An Office member may use the Office workflow, but cannot become
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