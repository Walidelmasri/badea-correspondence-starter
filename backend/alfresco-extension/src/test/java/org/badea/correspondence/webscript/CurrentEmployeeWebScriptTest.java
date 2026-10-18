package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
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
    void doesNotAssignARoleWhenMembershipIsInvalid()
            throws IOException {

        when(roleResolver.resolveCurrentUserRole())
                .thenThrow(new CorrespondenceAuthorizationException(
                        "No unambiguous workflow role."));

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_OK);

        assertTrue(result.isNull("correspondenceRole"));

        assertEquals("10001", result.getString("employeeId"));
    }

    private JSONObject execute() throws IOException {
        webScript.execute(request, response);
        writer.flush();

        return new JSONObject(output.toString());
    }
}