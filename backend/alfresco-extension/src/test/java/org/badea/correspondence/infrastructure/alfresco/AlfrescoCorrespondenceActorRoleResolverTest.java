package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.List;
import java.util.Optional;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader.ManagerWorkspace;

import org.alfresco.service.cmr.security.AuthorityService;
import org.badea.correspondence.security.AuthenticatedUserProvider;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlfrescoCorrespondenceActorRoleResolverTest {

    private static final String USERNAME =
            "walid.salem";

    private AuthorityService authorityService;
    private AuthenticatedUserProvider authenticatedUserProvider;

    private AlfrescoCorrespondenceActorRoleResolver resolver;

    @BeforeEach
    void setUp() {

        authorityService =
                mock(
                        AuthorityService.class);

        authenticatedUserProvider =
                mock(
                        AuthenticatedUserProvider.class);

        resolver =
                new AlfrescoCorrespondenceActorRoleResolver(
                        authorityService,
                        authenticatedUserProvider);

        when(authenticatedUserProvider.getUsername())
                .thenReturn(
                        USERNAME);
    }

    @Test
    void resolvesDirector() {

        when(authorityService.getAuthoritiesForUser(
                USERNAME))
                .thenReturn(
                        Set.of(
                                "GROUP_EVERYONE",
                                "GROUP_BADEA_CORRESPONDENCE_DIRECTORS"));

        CorrespondenceActorRole role =
                resolver.resolveCurrentUserRole();

        assertEquals(
                CorrespondenceActorRole.DIRECTOR,
                role);

        verify(authorityService)
                .getAuthoritiesForUser(
                        USERNAME);
    }

    @Test
    void resolvesPresident() {

        when(authorityService.getAuthoritiesForUser(
                USERNAME))
                .thenReturn(
                        Set.of(
                                "GROUP_BADEA_CORRESPONDENCE_PRESIDENT"));

        assertEquals(
                CorrespondenceActorRole.PRESIDENT,
                resolver.resolveCurrentUserRole());
    }

    @Test
    void resolvesPresidentOffice() {

        when(authorityService.getAuthoritiesForUser(
                USERNAME))
                .thenReturn(
                        Set.of(
                                "GROUP_BADEA_CORRESPONDENCE_PRESIDENT_OFFICE"));

        assertEquals(
                CorrespondenceActorRole.PRESIDENT_OFFICE,
                resolver.resolveCurrentUserRole());
    }

    @Test
    void rejectsUserWithoutCorrespondenceRole() {

        when(authorityService.getAuthoritiesForUser(
                USERNAME))
                .thenReturn(
                        Set.of(
                                "GROUP_EVERYONE",
                                "GROUP_SOME_OTHER_GROUP"));

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> resolver.resolveCurrentUserRole());
    }

    @Test
    void permitsDirectorAndOfficeMembershipWithoutGrantingPresident() {
        when(authorityService.getAuthoritiesForUser(USERNAME))
                .thenReturn(Set.of(
                        "GROUP_BADEA_CORRESPONDENCE_DIRECTORS",
                        "GROUP_BADEA_CORRESPONDENCE_PRESIDENT_OFFICE"));

        assertEquals(Set.of(CorrespondenceActorRole.DIRECTOR,
                CorrespondenceActorRole.PRESIDENT_OFFICE),
                resolver.resolveCurrentUserRoles());
        assertEquals(CorrespondenceActorRole.PRESIDENT_OFFICE,
                resolver.resolveCurrentUserRole());
    }

    private static EmployeeDirectoryEntry actingEmployee() {
        return new EmployeeDirectoryEntry(new EmployeeId("18284"),
            new DirectoryUsername(USERNAME), "Mohamed N'Diack", null, Optional.empty());
    }

    @Test
    void verifiesActingHrAndOfficeManagerWithoutGrantingPresident() {
        var directory = mock(TarasolOrganizationReader.class);
        var employee = actingEmployee();
        when(authorityService.getAuthoritiesForUser(USERNAME)).thenReturn(Set.of(
            "GROUP_BADEA_CORRESPONDENCE_DIRECTORS",
            "GROUP_BADEA_CORRESPONDENCE_PRESIDENT_OFFICE"));
        when(directory.findCurrentManagerWorkspaces(employee.employeeId())).thenReturn(List.of(
            new ManagerWorkspace(1L, new DepartmentCode("101"), "Office", null, "MANAGER"),
            new ManagerWorkspace(3L, new DepartmentCode("118"), "HR", null, "ACTING_MANAGER")));
        var scoped = new AlfrescoCorrespondenceActorRoleResolver(authorityService,
            authenticatedUserProvider, directory);
        assertEquals(CorrespondenceActorRole.DIRECTOR,
            scoped.resolveWorkspace("118", employee).role());
        assertEquals(new DepartmentCode("118"), scoped.resolveWorkspace("118", employee).unit());
        assertEquals(CorrespondenceActorRole.PRESIDENT_OFFICE,
            scoped.resolveWorkspace("101", employee).role());
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> scoped.resolveWorkspace("106", employee));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> scoped.resolveWorkspace("invalid", employee));
    }

    @Test
    void officeMemberOf101CanUseOfficeWorkspaceWithoutManagerAuthority() {
        var directory = mock(TarasolOrganizationReader.class);
        var employee = actingEmployee();
        when(authorityService.getAuthoritiesForUser(USERNAME)).thenReturn(Set.of(
            "GROUP_BADEA_CORRESPONDENCE_DIRECTORS",
            "GROUP_BADEA_CORRESPONDENCE_PRESIDENT_OFFICE"));
        when(directory.findCurrentManagerWorkspaces(employee.employeeId())).thenReturn(List.of(
            new ManagerWorkspace(9, new DepartmentCode("117"), "Digital Transformation", null, "MANAGER")));
        when(directory.findCurrentOfficeMembership(employee.employeeId())).thenReturn(Optional.of(
            new TarasolOrganizationReader.OfficeMembership(10, new DepartmentCode("101"),
                "Office of President", null)));
        var scoped = new AlfrescoCorrespondenceActorRoleResolver(authorityService,
            authenticatedUserProvider, directory);
        assertEquals(CorrespondenceActorRole.PRESIDENT_OFFICE,
            scoped.resolveWorkspace("101", employee).role());
        assertEquals(CorrespondenceActorRole.DIRECTOR,
            scoped.resolveWorkspace("117", employee).role());
    }

    @Test
    void officeMemberWithoutOfficeGroupCannotSwitchOrGainDirectorRights() {
        var directory = mock(TarasolOrganizationReader.class);
        var employee = actingEmployee();
        when(authorityService.getAuthoritiesForUser(USERNAME)).thenReturn(Set.of(
            "GROUP_BADEA_CORRESPONDENCE_DIRECTORS"));
        when(directory.findCurrentOfficeMembership(employee.employeeId())).thenReturn(Optional.of(
            new TarasolOrganizationReader.OfficeMembership(10, new DepartmentCode("101"),
                "Office", null)));
        var scoped = new AlfrescoCorrespondenceActorRoleResolver(authorityService,
            authenticatedUserProvider, directory);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> scoped.resolveWorkspace("101", employee));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> scoped.resolveWorkspace("117", employee));
    }

    @Test
    void officeMembershipIn117CannotBecomeDirectorWorkspace() {
        var directory = mock(TarasolOrganizationReader.class);
        var employee = actingEmployee();
        when(authorityService.getAuthoritiesForUser(USERNAME)).thenReturn(Set.of(
            "GROUP_BADEA_CORRESPONDENCE_PRESIDENT_OFFICE",
            "GROUP_BADEA_CORRESPONDENCE_DIRECTORS"));
        var scoped = new AlfrescoCorrespondenceActorRoleResolver(authorityService,
            authenticatedUserProvider, directory);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> scoped.resolveWorkspace("117", employee));
    }

    @Test
    void refusesDirectorWorkspaceForOfficeOnlyGroupEvenWhenAssigned() {
        var directory = mock(TarasolOrganizationReader.class);
        var employee = actingEmployee();
        when(authorityService.getAuthoritiesForUser(USERNAME)).thenReturn(Set.of(
            "GROUP_BADEA_CORRESPONDENCE_PRESIDENT_OFFICE"));
        when(directory.findCurrentManagerWorkspaces(employee.employeeId())).thenReturn(List.of(
            new ManagerWorkspace(3L, new DepartmentCode("118"), "HR", null, "ACTING_MANAGER")));
        var scoped = new AlfrescoCorrespondenceActorRoleResolver(authorityService,
            authenticatedUserProvider, directory);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> scoped.resolveWorkspace("118", employee));
    }

    @Test
    void refusesDuplicateOrRevokedManagerWorkspace() {
        var directory = mock(TarasolOrganizationReader.class);
        var employee = actingEmployee();
        when(authorityService.getAuthoritiesForUser(USERNAME)).thenReturn(Set.of(
            "GROUP_BADEA_CORRESPONDENCE_DIRECTORS"));
        when(directory.findCurrentManagerWorkspaces(employee.employeeId())).thenReturn(List.of(
            new ManagerWorkspace(2L, new DepartmentCode("108"), "Regional", null, "MANAGER"),
            new ManagerWorkspace(3L, new DepartmentCode("108"), "Regional", null, "ACTING_MANAGER")));
        var scoped = new AlfrescoCorrespondenceActorRoleResolver(authorityService,
            authenticatedUserProvider, directory);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> scoped.resolveWorkspace("108", employee));
        when(directory.findCurrentManagerWorkspaces(employee.employeeId())).thenReturn(List.of());
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> scoped.resolveWorkspace("108", employee));
    }

    @Test
    void neverMapsPresidentIntoDepartmentManagerRole() {
        var employee = actingEmployee();
        when(authorityService.getAuthoritiesForUser(USERNAME)).thenReturn(Set.of(
            "GROUP_BADEA_CORRESPONDENCE_PRESIDENT"));
        var scoped = new AlfrescoCorrespondenceActorRoleResolver(authorityService,
            authenticatedUserProvider, mock(TarasolOrganizationReader.class));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> scoped.resolveWorkspace("101", employee));
    }

    @Test
    void rejectsConflictingCorrespondenceRoles() {

        when(authorityService.getAuthoritiesForUser(
                USERNAME))
                .thenReturn(
                        Set.of(
                                "GROUP_BADEA_CORRESPONDENCE_DIRECTORS",
                                "GROUP_BADEA_CORRESPONDENCE_PRESIDENT"));

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> resolver.resolveCurrentUserRole());
    }
}