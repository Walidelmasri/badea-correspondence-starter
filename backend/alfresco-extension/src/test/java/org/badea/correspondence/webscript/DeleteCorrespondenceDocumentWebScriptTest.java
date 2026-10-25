package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;

import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceDraftDocumentDeleter;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.extensions.webscripts.Match;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class DeleteCorrespondenceDocumentWebScriptTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private static final String DOCUMENT_ID =
            "22222222-2222-2222-2222-222222222222";

    private static final DepartmentCode DEPARTMENT =
            new DepartmentCode("117");

    private CurrentEmployeeService employeeService;
    private CorrespondenceActorRoleResolver roleResolver;
    private AlfrescoCorrespondenceDraftDocumentDeleter deleter;

    private WebScriptRequest request;
    private WebScriptResponse response;
    private Match match;

    private StringWriter output;
    private PrintWriter writer;

    private DeleteCorrespondenceDocumentWebScript webScript;

    @BeforeEach
    void setUp() throws IOException {

        employeeService =
                mock(CurrentEmployeeService.class);

        roleResolver =
                mock(CorrespondenceActorRoleResolver.class);

        deleter =
                mock(AlfrescoCorrespondenceDraftDocumentDeleter.class);

        request =
                mock(WebScriptRequest.class);

        response =
                mock(WebScriptResponse.class);

        match = mock(Match.class);

        when(request.getServiceMatch())
                .thenReturn(match);

        when(match.getTemplateVars())
                .thenReturn(Map.of(
                        "technicalId", TECHNICAL_ID,
                        "nodeId", DOCUMENT_ID));

        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.DIRECTOR);

        when(employeeService.getCurrentEmployee())
                .thenReturn(employee(true));

        output = new StringWriter();
        writer = new PrintWriter(output);

        when(response.getWriter())
                .thenReturn(writer);

        webScript =
                new DeleteCorrespondenceDocumentWebScript(
                        employeeService,
                        roleResolver,
                        deleter);
    }

    @Test
    void deletesDocumentAndReturnsNoContent()
            throws IOException {

        execute();

        verify(deleter).deleteDocument(
                TECHNICAL_ID,
                DOCUMENT_ID,
                CorrespondenceActorRole.DIRECTOR,
                DEPARTMENT);

        verify(response).setStatus(204);

        assertTrue(output.toString().isEmpty());

        verify(response, never()).getWriter();
    }

    @Test
    void presidentDeletesOwnDraftAttachment() throws IOException {
        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT);

        execute();

        verify(deleter).deleteDocument(TECHNICAL_ID, DOCUMENT_ID,
                CorrespondenceActorRole.PRESIDENT, DEPARTMENT, new EmployeeId("10001"));
        verify(response).setStatus(204);
        assertTrue(output.toString().isEmpty());
    }

    @Test
    void presidentOfficeDeletionIsCreatorScoped() throws IOException {
        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT_OFFICE);
        doThrow(new CorrespondenceAuthorizationException("Not creator"))
                .when(deleter).deleteDocument(TECHNICAL_ID, DOCUMENT_ID,
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        DEPARTMENT, new EmployeeId("10001"));

        JSONObject result = executeError();

        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        assertEquals("CORRESPONDENCE_FORBIDDEN", result.getString("code"));
    }

    @Test
    void rejectsEmployeeWithoutDepartment()
            throws IOException {

        when(employeeService.getCurrentEmployee())
                .thenReturn(employee(false));

        JSONObject result = executeError();

        verify(response).setStatus(Status.STATUS_CONFLICT);

        assertEquals(
                "EMPLOYEE_DEPARTMENT_REQUIRED",
                result.getString("code"));

        verifyNoInteractions(deleter);
    }

    @Test
    void rejectsMissingDocumentId()
            throws IOException {

        when(match.getTemplateVars())
                .thenReturn(Map.of(
                        "technicalId", TECHNICAL_ID));

        JSONObject result = executeError();

        verify(response).setStatus(Status.STATUS_BAD_REQUEST);

        assertEquals(
                "INVALID_REQUEST",
                result.getString("code"));

        verifyNoInteractions(deleter);
    }

    @Test
    void returnsNotFoundForUnrelatedDocument()
            throws IOException {

        doThrow(new CorrespondenceDocumentNotFoundException())
                .when(deleter)
                .deleteDocument(
                        TECHNICAL_ID,
                        DOCUMENT_ID,
                        CorrespondenceActorRole.DIRECTOR,
                        DEPARTMENT);

        JSONObject result = executeError();

        verify(response).setStatus(Status.STATUS_NOT_FOUND);

        assertEquals(
                "DOCUMENT_NOT_FOUND",
                result.getString("code"));
    }

    @Test
    void rejectsRepositoryAuthorizationFailure()
            throws IOException {

        doThrow(new CorrespondenceAuthorizationException(
                "Draft belongs to another department."))
                .when(deleter)
                .deleteDocument(
                        TECHNICAL_ID,
                        DOCUMENT_ID,
                        CorrespondenceActorRole.DIRECTOR,
                        DEPARTMENT);

        JSONObject result = executeError();

        verify(response).setStatus(Status.STATUS_FORBIDDEN);

        assertEquals(
                "CORRESPONDENCE_FORBIDDEN",
                result.getString("code"));
    }

    @Test
    void rejectsInvalidDocumentUuid()
            throws IOException {

        doThrow(new IllegalArgumentException(
                "Document node ID must be a valid UUID."))
                .when(deleter)
                .deleteDocument(
                        TECHNICAL_ID,
                        DOCUMENT_ID,
                        CorrespondenceActorRole.DIRECTOR,
                        DEPARTMENT);

        JSONObject result = executeError();

        verify(response).setStatus(Status.STATUS_BAD_REQUEST);

        assertEquals(
                "INVALID_REQUEST",
                result.getString("code"));
    }

    @Test
    void returnsControlledErrorForRepositoryFailure()
            throws IOException {

        doThrow(new CorrespondenceRepositoryException(
                "Alfresco deletion failed."))
                .when(deleter)
                .deleteDocument(
                        TECHNICAL_ID,
                        DOCUMENT_ID,
                        CorrespondenceActorRole.DIRECTOR,
                        DEPARTMENT);

        JSONObject result = executeError();

        verify(response)
                .setStatus(Status.STATUS_INTERNAL_SERVER_ERROR);

        assertEquals(
                "DOCUMENT_STORAGE_ERROR",
                result.getString("code"));
    }

    @Test
    void handlesEmployeeDirectoryFailure()
            throws IOException {

        when(employeeService.getCurrentEmployee())
                .thenThrow(new EmployeeDirectoryException(
                        "Oracle unavailable."));

        JSONObject result = executeError();

        verify(response)
                .setStatus(Status.STATUS_SERVICE_UNAVAILABLE);

        assertEquals(
                "EMPLOYEE_DIRECTORY_UNAVAILABLE",
                result.getString("code"));

        verifyNoInteractions(deleter);
    }

    private void execute() throws IOException {

        webScript.execute(request, response);

        writer.flush();
    }

    private JSONObject executeError() throws IOException {

        execute();

        return new JSONObject(output.toString());
    }

    private static EmployeeDirectoryEntry employee(
            boolean hasDepartment) {

        Optional<DepartmentDirectoryEntry> department =
                hasDepartment
                        ? Optional.of(
                                new DepartmentDirectoryEntry(
                                        DEPARTMENT,
                                        "Digital Transformation",
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