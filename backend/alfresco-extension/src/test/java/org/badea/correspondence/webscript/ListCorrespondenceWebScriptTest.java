package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceDraftLister;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceMailboxLister;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class ListCorrespondenceWebScriptTest {

    private static final DepartmentCode DEPARTMENT =
            new DepartmentCode("117");

    private CurrentEmployeeService employeeService;
    private CorrespondenceActorRoleResolver roleResolver;
    private AlfrescoCorrespondenceDraftLister draftLister;
    private AlfrescoCorrespondenceMailboxLister mailboxLister;

    private WebScriptRequest request;
    private WebScriptResponse response;

    private StringWriter output;
    private PrintWriter writer;

    private ListCorrespondenceWebScript webScript;

    @BeforeEach
    void setUp() throws IOException {

        employeeService = mock(CurrentEmployeeService.class);
        roleResolver = mock(CorrespondenceActorRoleResolver.class);
        draftLister = mock(AlfrescoCorrespondenceDraftLister.class);
        mailboxLister = mock(AlfrescoCorrespondenceMailboxLister.class);

        request = mock(WebScriptRequest.class);
        response = mock(WebScriptResponse.class);

        output = new StringWriter();
        writer = new PrintWriter(output);

        when(response.getWriter()).thenReturn(writer);

        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.DIRECTOR);

        when(request.getParameter("box"))
                .thenReturn("drafts");

        when(employeeService.getCurrentEmployee())
                .thenReturn(employee(true));

        webScript = new ListCorrespondenceWebScript(
                employeeService,
                roleResolver,
                draftLister,
                mailboxLister);
    }

    @Test
    void returnsAuthorizedDraftsWithPagination() throws IOException {

        when(request.getParameter("offset"))
                .thenReturn("5");

        when(request.getParameter("limit"))
                .thenReturn("10");

        var summary =
                new AlfrescoCorrespondenceDraftLister.DraftSummary(
                        "draft-1",
                        "Test Draft 1",
                        "DOC-001",
                        Instant.parse("2026-10-18T08:00:00Z"),
                        Instant.parse("2026-10-18T09:00:00Z"));

        var page =
                new AlfrescoCorrespondenceDraftLister.DraftPage(
                        List.of(summary),
                        5,
                        10,
                        11);

        when(draftLister.list(DEPARTMENT, 5, 10))
                .thenReturn(page);

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_OK);

        assertEquals(5, result.getInt("offset"));
        assertEquals(10, result.getInt("limit"));
        assertEquals(11, result.getInt("total"));

        assertEquals(
                "Test Draft 1",
                result.getJSONArray("items")
                        .getJSONObject(0)
                        .getString("subject"));

        assertEquals(
                "2026-10-18T09:00:00Z",
                result.getJSONArray("items")
                        .getJSONObject(0)
                        .getString("modifiedAt"));

        verify(draftLister).list(DEPARTMENT, 5, 10);
    }

    @Test
    void returnsEmptyListWhenNoDraftsExist() throws IOException {

        when(draftLister.list(DEPARTMENT, 0, 25))
                .thenReturn(
                        new AlfrescoCorrespondenceDraftLister.DraftPage(
                                List.of(),
                                0,
                                25,
                                0));

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_OK);

        assertEquals(0, result.getInt("total"));
        assertEquals(0, result.getJSONArray("items").length());
    }

    @Test
    void officeDraftsComeFromSavedReviewProjection() throws IOException {
        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT_OFFICE);
        when(mailboxLister.list("drafts", CorrespondenceActorRole.PRESIDENT_OFFICE,
                DEPARTMENT, "10001", "all", 0, 25))
                .thenReturn(new AlfrescoCorrespondenceMailboxLister.MailboxPage(
                        List.of(), 0, 25, 0));
        assertEquals(0, execute().getInt("total"));
        verifyNoInteractions(draftLister);
    }

    @Test
    void presidentDraftsComeFromSavedReviewProjection() throws IOException {
        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT);
        when(mailboxLister.list("drafts", CorrespondenceActorRole.PRESIDENT,
                DEPARTMENT, "10001", "all", 0, 25))
                .thenReturn(new AlfrescoCorrespondenceMailboxLister.MailboxPage(
                        List.of(), 0, 25, 0));
        assertEquals(0, execute().getInt("total"));
        verifyNoInteractions(draftLister);
    }

    @Test
    void rejectsDirectorWithoutDepartment() throws IOException {

        when(employeeService.getCurrentEmployee())
                .thenReturn(employee(false));

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_FORBIDDEN);

        assertEquals(
                "CORRESPONDENCE_FORBIDDEN",
                result.getString("code"));

        verifyNoInteractions(draftLister);
    }

    @Test
    void rejectsInvalidOffset() throws IOException {

        when(request.getParameter("offset"))
                .thenReturn("-1");

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_BAD_REQUEST);

        assertEquals(
                "INVALID_REQUEST",
                result.getString("code"));

        verifyNoInteractions(draftLister);
    }

    @Test
    void rejectsInvalidLimit() throws IOException {

        when(request.getParameter("limit"))
                .thenReturn("101");

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_BAD_REQUEST);

        assertEquals(
                "INVALID_REQUEST",
                result.getString("code"));

        verifyNoInteractions(draftLister);
    }

    @Test
    void rejectsUnsupportedBox() throws IOException {

        when(request.getParameter("box"))
                .thenReturn("unknown");

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_BAD_REQUEST);

        assertEquals(
                "INVALID_REQUEST",
                result.getString("code"));

        verifyNoInteractions(draftLister);
    }

    @Test
    void handlesRepositoryFailure() throws IOException {

        when(draftLister.list(DEPARTMENT, 0, 25))
                .thenThrow(
                        new CorrespondenceRepositoryException(
                                "Department folder unavailable."));

        JSONObject result = execute();

        verify(response)
                .setStatus(Status.STATUS_INTERNAL_SERVER_ERROR);

        assertEquals(
                "CORRESPONDENCE_REPOSITORY_ERROR",
                result.getString("code"));
    }

    @Test
    void listsAssignedOfficeInbox() throws IOException {
        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT_OFFICE);
        when(request.getParameter("box")).thenReturn("inbox");
        var summary = new AlfrescoCorrespondenceMailboxLister.MailboxSummary(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "Office task", "N-2",
                "WITH_PRESIDENT_OFFICE", "117", "Director",
                Instant.parse("2026-10-18T08:00:00Z"),
                Instant.parse("2026-10-18T09:00:00Z"));
        when(mailboxLister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE,
                DEPARTMENT, "10001", "all", 0, 25))
                .thenReturn(new AlfrescoCorrespondenceMailboxLister.MailboxPage(
                        List.of(summary), 0, 25, 1));

        JSONObject result = execute();
        verify(response).setStatus(Status.STATUS_OK);
        assertEquals(1, result.getInt("total"));
        assertEquals("WITH_PRESIDENT_OFFICE",
                result.getJSONArray("items").getJSONObject(0).getString("state"));
    }

    @Test
    void returnsOriginatingDirectorSent() throws IOException {
        when(request.getParameter("box")).thenReturn("sent");
        when(mailboxLister.list("sent", CorrespondenceActorRole.DIRECTOR, DEPARTMENT, "10001", "all", 0, 25))
                .thenReturn(new AlfrescoCorrespondenceMailboxLister.MailboxPage(
                        List.of(), 0, 25, 0));
        JSONObject result = execute();
        verify(response).setStatus(Status.STATUS_OK);
        assertEquals(0, result.getInt("total"));
        verify(mailboxLister).list("sent", CorrespondenceActorRole.DIRECTOR, DEPARTMENT, "10001", "all", 0, 25);
    }

    @Test
    void officeSentUsesHistoricalActionProjection() throws IOException {
        when(roleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT_OFFICE);
        when(request.getParameter("box")).thenReturn("sent");
        when(mailboxLister.list("sent", CorrespondenceActorRole.PRESIDENT_OFFICE,
                DEPARTMENT, "10001", "all", 0, 25))
                .thenReturn(new AlfrescoCorrespondenceMailboxLister.MailboxPage(
                        List.of(), 0, 25, 0));
        JSONObject result = execute();
        verify(response).setStatus(Status.STATUS_OK);
        assertEquals(0, result.getInt("total"));
    }

    private JSONObject execute() throws IOException {

        webScript.execute(request, response);

        writer.flush();

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