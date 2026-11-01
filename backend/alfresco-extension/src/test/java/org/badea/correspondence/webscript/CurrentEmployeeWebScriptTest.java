package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLTimeoutException;
import java.util.Optional;
import java.util.Set;
import java.util.List;

import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;

import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class CurrentEmployeeWebScriptTest {

    private CurrentEmployeeService employeeService;
    private CorrespondenceActorRoleResolver roleResolver;

    private WebScriptRequest request;
    private WebScriptResponse response;

    private StringWriter output;
    private PrintWriter writer;

    private CurrentEmployeeWebScript webScript;

    @BeforeEach
    void setUp() throws IOException {
        employeeService = mock(CurrentEmployeeService.class);
        roleResolver = mock(CorrespondenceActorRoleResolver.class);

        request = mock(WebScriptRequest.class);
        response = mock(WebScriptResponse.class);

        output = new StringWriter();
        writer = new PrintWriter(output);

        when(response.getWriter()).thenReturn(writer);

        when(employeeService.getCurrentEmployee())
                .thenReturn(new EmployeeDirectoryEntry(
                        new EmployeeId("10001"),
                        new DirectoryUsername("test.user"),
                        "Test User",
                        null,
                        Optional.of(
                                new DepartmentDirectoryEntry(
                                        new DepartmentCode("117"),
                                        "Test Department",
                                        null))));

        webScript = new CurrentEmployeeWebScript(
                employeeService,
                roleResolver);
    }

    @Test
    void returnsDirectorRole() throws IOException {
        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.DIRECTOR);

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_OK);

        assertEquals(
                "DIRECTOR",
                result.getString("correspondenceRole"));

        assertEquals(
                "117",
                result.getJSONObject("department").getString("code"));
    }

    @Test
    void returnsAuthenticatedMultiUnitManagerCapacities() throws IOException {
        var organizationReader = mock(TarasolOrganizationReader.class);
        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT_OFFICE);
        when(roleResolver.resolveCurrentUserRoles()).thenReturn(Set.of(
                CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceActorRole.DIRECTOR));
        when(organizationReader.findCurrentManagerWorkspaces(new EmployeeId("10001")))
                .thenReturn(List.of(
                        new TarasolOrganizationReader.ManagerWorkspace(3,
                                new DepartmentCode("101"), "Office of President", null, "MANAGER"),
                        new TarasolOrganizationReader.ManagerWorkspace(7,
                                new DepartmentCode("118"), "Human Resources", null, "ACTING_MANAGER")));
        webScript = new CurrentEmployeeWebScript(employeeService, roleResolver, organizationReader);

        JSONObject result = execute();
        assertEquals("PRESIDENT_OFFICE", result.getString("correspondenceRole"));
        var assigned = result.getJSONArray("managerWorkspaces");
        assertEquals(2, assigned.length());
        assertEquals("PRESIDENT_OFFICE", assigned.getJSONObject(0).getString("role"));
        assertEquals("118", assigned.getJSONObject(1).getString("code"));
        assertEquals("ACTING_MANAGER", assigned.getJSONObject(1).getString("capacity"));
        assertEquals("DIRECTOR", assigned.getJSONObject(1).getString("role"));
        assertEquals(2, result.getJSONArray("correspondenceRoles").length());
    }

    @Test
    void rejectsOfficeWorkspaceWithoutOfficeGroupEvenForUnitManager() throws IOException {
        var organizationReader = mock(TarasolOrganizationReader.class);
        when(roleResolver.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.DIRECTOR);
        when(roleResolver.resolveCurrentUserRoles()).thenReturn(Set.of(CorrespondenceActorRole.DIRECTOR));
        when(organizationReader.findCurrentManagerWorkspaces(new EmployeeId("10001")))
                .thenReturn(List.of(new TarasolOrganizationReader.ManagerWorkspace(4,
                        new DepartmentCode("101"), "Office of President", null, "MANAGER")));
        webScript = new CurrentEmployeeWebScript(employeeService, roleResolver, organizationReader);
        assertEquals(0, execute().getJSONArray("managerWorkspaces").length());
    }

    @Test
    void returnsPresidentRole() throws IOException {
        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT);

        JSONObject result = execute();

        assertEquals(
                "PRESIDENT",
                result.getString("correspondenceRole"));
    }

    @Test
    void returnsOfficeRole() throws IOException {
        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT_OFFICE);

        JSONObject result = execute();

        assertEquals(
                "PRESIDENT_OFFICE",
                result.getString("correspondenceRole"));
    }

    @Test
    void deniesAuthenticatedUsersWithoutAnUnambiguousAlfrescoGroup()
            throws IOException {

        when(roleResolver.resolveCurrentUserRole())
                .thenThrow(new CorrespondenceAuthorizationException(
                        "No unambiguous workflow role."));

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        assertEquals("CORRESPONDENCE_FORBIDDEN", result.getString("code"));
        verify(employeeService, never()).getCurrentEmployee();
    }

    @Test
    void treatsOracleTimeoutAsTemporaryFailureInsteadOfForbidden()
            throws IOException {
        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.DIRECTOR);
        when(employeeService.getCurrentEmployee())
                .thenThrow(new EmployeeDirectoryException(
                        "Database read timed out.",
                        new SQLTimeoutException("Read timed out.")));

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_SERVICE_UNAVAILABLE);
        assertEquals("EMPLOYEE_DIRECTORY_UNAVAILABLE", result.getString("code"));
    }

    private JSONObject execute() throws IOException {
        webScript.execute(request, response);
        writer.flush();

        return new JSONObject(output.toString());
    }
}