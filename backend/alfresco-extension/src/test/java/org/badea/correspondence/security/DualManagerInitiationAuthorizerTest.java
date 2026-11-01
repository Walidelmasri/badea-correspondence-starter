package org.badea.correspondence.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;

class DualManagerInitiationAuthorizerTest {
    private final EmployeeId employee = new EmployeeId("18284");
    private final CorrespondenceActorRoleResolver groups = mock(CorrespondenceActorRoleResolver.class);
    private final TarasolOrganizationReader organization = mock(TarasolOrganizationReader.class);

    private TarasolOrganizationReader.ManagerWorkspace workspace(String code) {
        return new TarasolOrganizationReader.ManagerWorkspace(
            Long.parseLong(code), new DepartmentCode(code), "Unit " + code, null, "MANAGER");
    }

    private void dualGroups() {
        when(groups.resolveCurrentUserRoles()).thenReturn(Set.of(
            CorrespondenceActorRole.DIRECTOR, CorrespondenceActorRole.PRESIDENT_OFFICE));
    }

    @Test
    void permitsDirectOfficeStyleInitiationOnlyWithTwoActiveAppointments() {
        dualGroups();
        when(organization.findCurrentManagerWorkspaces(employee)).thenReturn(
            List.of(workspace("101"), workspace("118"), workspace("108")));
        assertDoesNotThrow(() -> DualManagerInitiationAuthorizer.requireAuthorized(
            employee, new DepartmentCode("118"), groups, organization));
        assertDoesNotThrow(() -> DualManagerInitiationAuthorizer.requireAuthorized(
            employee, new DepartmentCode("108"), groups, organization));
    }

    @Test
    void refusesOrdinaryDirectorWithoutOfficeAppointment() {
        dualGroups();
        when(organization.findCurrentManagerWorkspaces(employee)).thenReturn(List.of(workspace("118")));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> DualManagerInitiationAuthorizer.requireAuthorized(
                employee, new DepartmentCode("118"), groups, organization));
    }

    @Test
    void refusesMissingAlfrescoOfficeGroupEvenWithBothAppointments() {
        when(groups.resolveCurrentUserRoles()).thenReturn(Set.of(CorrespondenceActorRole.DIRECTOR));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> DualManagerInitiationAuthorizer.requireAuthorized(
                employee, new DepartmentCode("118"), groups, organization));
    }

    @Test
    void refusesOriginOfficeOrAmbiguousManagerialRecords() {
        dualGroups();
        when(organization.findCurrentManagerWorkspaces(employee)).thenReturn(
            List.of(workspace("101"), workspace("118"), workspace("118")));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> DualManagerInitiationAuthorizer.requireAuthorized(
                employee, new DepartmentCode("118"), groups, organization));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> DualManagerInitiationAuthorizer.requireAuthorized(
                employee, new DepartmentCode("101"), groups, organization));
    }
}
