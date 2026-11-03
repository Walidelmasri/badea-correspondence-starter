package org.badea.correspondence.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;

class CorrespondenceWorkspaceContextTest {
    private static final EmployeeDirectoryEntry OFFICE_EMPLOYEE = new EmployeeDirectoryEntry(
        new EmployeeId("99999"), new DirectoryUsername("office.test"), "Office Employee", null,
        Optional.of(new org.badea.correspondence.directory.DepartmentDirectoryEntry(
            new DepartmentCode("117"), "Digital Transformation", null)));

    @Test void officeGroupUsesUnit101RegardlessOfHomeDepartment() {
        var roles = mock(CorrespondenceActorRoleResolver.class);
        when(roles.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.PRESIDENT_OFFICE);
        var workspace = CorrespondenceWorkspaceContext.resolve(roles, null, OFFICE_EMPLOYEE);
        assertEquals(CorrespondenceActorRole.PRESIDENT_OFFICE, workspace.role());
        assertEquals(new DepartmentCode("101"), workspace.unit());
    }

    @Test void staleDirectorGroupCannotGainWorkspaceWithoutOracleAppointment() {
        var roles = mock(CorrespondenceActorRoleResolver.class);
        when(roles.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.DIRECTOR);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> CorrespondenceWorkspaceContext.resolve(roles, null, OFFICE_EMPLOYEE));
    }

    @Test void unselectedDirectorCannotUseOffice101AsDirectorWorkspace() {
        var roles = mock(CorrespondenceActorRoleResolver.class);
        when(roles.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.DIRECTOR);
        when(roles.resolveWorkspace(null, OFFICE_EMPLOYEE)).thenReturn(
            new CorrespondenceActorRoleResolver.Workspace(CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("101")));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> CorrespondenceWorkspaceContext.resolve(roles, null, OFFICE_EMPLOYEE));
    }
}
