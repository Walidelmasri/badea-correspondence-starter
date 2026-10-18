package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.time.LocalDate;

import java.util.Map;
import java.util.Optional;

import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.UpdateCorrespondenceDraftCommand;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;

import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceDraftUpdater;

import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Match;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class UpdateCorrespondenceDraftWebScriptTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private CurrentEmployeeService employeeService;
    private CorrespondenceActorRoleResolver roleResolver;
    private AlfrescoCorrespondenceDraftUpdater updater;

    private WebScriptRequest request;
    private WebScriptResponse response;
    private Content content;

    private StringWriter output;
    private PrintWriter writer;

    private UpdateCorrespondenceDraftWebScript webScript;

    @BeforeEach
    void setUp() throws IOException {

        employeeService = mock(CurrentEmployeeService.class);
        roleResolver = mock(CorrespondenceActorRoleResolver.class);
        updater = mock(AlfrescoCorrespondenceDraftUpdater.class);

        request = mock(WebScriptRequest.class);
        response = mock(WebScriptResponse.class);
        content = mock(Content.class);

        Match match = mock(Match.class);

        when(request.getServiceMatch()).thenReturn(match);

        when(match.getTemplateVars()).thenReturn(
                Map.of("technicalId", TECHNICAL_ID));

        when(request.getContent()).thenReturn(content);

        when(content.getContent()).thenReturn(validBody());

        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.DIRECTOR);

        when(employeeService.getCurrentEmployee())
                .thenReturn(employee(true));

        output = new StringWriter();
        writer = new PrintWriter(output);

        when(response.getWriter()).thenReturn(writer);

        webScript = new UpdateCorrespondenceDraftWebScript(
                employeeService,
                roleResolver,
                updater);
    }

    @Test
    void updatesOwnedDraft() throws IOException {

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_OK);

        assertEquals(
                TECHNICAL_ID,
                result.getString("technicalId"));

        assertEquals("DRAFT", result.getString("state"));

        ArgumentCaptor<UpdateCorrespondenceDraftCommand> captor =
                ArgumentCaptor.forClass(
                        UpdateCorrespondenceDraftCommand.class);

        verify(updater).updateDraft(captor.capture());

        UpdateCorrespondenceDraftCommand command =
                captor.getValue();

        assertEquals(
                new DepartmentCode("117"),
                command.actorDepartmentCode());

        assertEquals(
                new EmployeeId("10001"),
                command.actorEmployeeId());

        assertEquals("Updated subject", command.subject());

        assertEquals(
                LocalDate.of(2026, 10, 14),
                command.documentDate());
    }

    @Test
    void rejectsPresident() throws IOException {

        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT);

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_FORBIDDEN);

        assertEquals(
                "CORRESPONDENCE_FORBIDDEN",
                result.getString("code"));

        verifyNoUpdates();
    }

    @Test
    void rejectsEmployeeWithoutDepartment() throws IOException {

        when(employeeService.getCurrentEmployee())
                .thenReturn(employee(false));

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_CONFLICT);

        assertEquals(
                "EMPLOYEE_DEPARTMENT_REQUIRED",
                result.getString("code"));

        verifyNoUpdates();
    }

    @Test
    void rejectsInvalidDocumentDate() throws IOException {

        when(content.getContent())
                .thenReturn(validBody()
                        .replace("2026-10-14", "invalid-date"));

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_BAD_REQUEST);

        assertEquals(
                "INVALID_REQUEST",
                result.getString("code"));

        verifyNoUpdates();
    }

    @Test
    void rejectsPartialMetadataReplacement() throws IOException {

        when(content.getContent())
                .thenReturn("""
                        {
                            "subject": "Updated subject"
                        }
                        """);

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_BAD_REQUEST);

        assertEquals(
                "INVALID_REQUEST",
                result.getString("code"));

        verifyNoUpdates();
    }

    @Test
    void rejectsDraftOwnedByAnotherEmployee() throws IOException {

        doThrow(
                new CorrespondenceAuthorizationException(
                        "Not the draft creator."))
                .when(updater)
                .updateDraft(
                        any(UpdateCorrespondenceDraftCommand.class));

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_FORBIDDEN);

        assertEquals(
                "CORRESPONDENCE_FORBIDDEN",
                result.getString("code"));
    }

    @Test
    void allowsClearingOptionalMetadata() throws IOException {

        when(content.getContent())
                .thenReturn("""
                        {
                            "subject": "Updated subject",
                            "bodyText": null,
                            "documentNumber": null,
                            "documentDate": null,
                            "sourceText": null
                        }
                        """);

        execute();

        ArgumentCaptor<UpdateCorrespondenceDraftCommand> captor =
                ArgumentCaptor.forClass(
                        UpdateCorrespondenceDraftCommand.class);

        verify(updater).updateDraft(captor.capture());

        UpdateCorrespondenceDraftCommand command =
                captor.getValue();

        assertNull(command.bodyText());
        assertNull(command.documentNumber());
        assertNull(command.documentDate());
        assertNull(command.sourceText());
    }

    private JSONObject execute() throws IOException {

        webScript.execute(request, response);

        writer.flush();

        return new JSONObject(output.toString());
    }

    private void verifyNoUpdates() {

        verify(updater, never()).updateDraft(
                any(UpdateCorrespondenceDraftCommand.class));
    }

    private static String validBody() {

        return """
                {
                    "subject": "Updated subject",
                    "bodyText": "Updated description",
                    "documentNumber": "DOC-2026-001",
                    "documentDate": "2026-10-14",
                    "sourceText": "BADEA"
                }
                """;
    }

    private static EmployeeDirectoryEntry employee(
            boolean hasDepartment) {

        Optional<DepartmentDirectoryEntry> department =
                hasDepartment
                        ? Optional.of(
                                new DepartmentDirectoryEntry(
                                        new DepartmentCode("117"),
                                        "Department 117",
                                        null))
                        : Optional.empty();

        return new EmployeeDirectoryEntry(
                new EmployeeId("10001"),
                new DirectoryUsername("walid.salem"),
                "Test Employee",
                null,
                department);
    }
}