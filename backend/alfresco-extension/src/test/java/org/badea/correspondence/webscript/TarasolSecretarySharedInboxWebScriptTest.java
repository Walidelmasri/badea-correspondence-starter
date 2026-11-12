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
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceMailboxLister;
import org.badea.correspondence.organization.TarasolSecretarySharedInboxService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class TarasolSecretarySharedInboxWebScriptTest {
    private final CurrentEmployeeService employees = mock(CurrentEmployeeService.class);
    private final TarasolSecretarySharedInboxService service = mock(TarasolSecretarySharedInboxService.class);
    private final WebScriptRequest request = mock(WebScriptRequest.class);
    private final WebScriptResponse response = mock(WebScriptResponse.class);
    private final StringWriter output = new StringWriter();
    private final EmployeeDirectoryEntry secretary = new EmployeeDirectoryEntry(
        new EmployeeId("99999"), new DirectoryUsername("walid.salem"),
        "Test Secretary", null, Optional.empty());
    private final TarasolSecretarySharedInboxWebScript webScript =
        new TarasolSecretarySharedInboxWebScript(employees, service);

    @BeforeEach
    void init() throws IOException {
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        when(request.getParameter("unitCode")).thenReturn("117");
        when(employees.getCurrentEmployee()).thenReturn(secretary);
    }

    @Test
    void returnsTheExistingMailboxSummaryShapeWithNoWriteCapability() throws IOException {
        var summary = new AlfrescoCorrespondenceMailboxLister.MailboxSummary(
            "case-1", "Subject", null, "WITH_DIRECTOR", "117", "Sender",
            Instant.parse("2026-11-04T07:00:00Z"),
            Instant.parse("2026-11-04T08:00:00Z"), true, true, 4);
        when(service.list(secretary, new DepartmentCode("117"), "needs_action", 0, 10))
            .thenReturn(new AlfrescoCorrespondenceMailboxLister.MailboxPage(
                List.of(summary), 0, 10, 1));

        webScript.execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        var result = new JSONObject(output.toString());
        assertEquals(1, result.getInt("total"));
        var item = result.getJSONArray("items").getJSONObject(0);
        assertEquals("case-1", item.getString("technicalId"));
        assertEquals("117", item.getString("originDepartmentCode"));
        assertEquals(true, item.getBoolean("needsAction"));
        assertTrue(item.isNull("documentNumber"));
        assertEquals(4, item.getLong("incomingSequence"));
    }

    @Test
    void returnedFilterSerializesTheOwnersReasonWithoutChangingDocumentAccess() throws IOException {
        when(request.getParameter("view")).thenReturn("returned");
        var summary = new AlfrescoCorrespondenceMailboxLister.MailboxSummary(
            "case-1", "Returned Secretary Draft", null, "DRAFT", "117", "Secretary",
            Instant.parse("2026-11-11T07:00:00Z"),
            Instant.parse("2026-11-11T08:00:00Z"), true, false, 0,
            "RETURNED", "Please replace the PDF");
        when(service.list(secretary, new DepartmentCode("117"), "returned", 10, 10))
            .thenReturn(new AlfrescoCorrespondenceMailboxLister.MailboxPage(
                List.of(summary), 10, 10, 12));
        when(request.getParameter("offset")).thenReturn("10");
        webScript.execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        var result = new JSONObject(output.toString());
        assertEquals(12, result.getInt("total"));
        assertEquals(10, result.getInt("offset"));
        assertEquals(1, result.getJSONArray("items").length());
        var item = result.getJSONArray("items").getJSONObject(0);
        assertEquals("RETURNED", item.getString("approvalStatus"));
        assertEquals("Please replace the PDF", item.getString("returnReason"));
        assertEquals("DRAFT", item.getString("state"));
    }

    @Test
    void rejectsInvalidDepartmentBeforeServiceInvocation() throws IOException {
        when(request.getParameter("unitCode")).thenReturn("117 OR 1=1");
        webScript.execute(request, response);
        verify(response).setStatus(Status.STATUS_BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void deniesWithoutBothAuthorizationGrants() throws IOException {
        when(service.list(eq(secretary), eq(new DepartmentCode("117")),
            eq("needs_action"), eq(0), eq(10)))
            .thenThrow(new CorrespondenceAuthorizationException("Denied"));
        webScript.execute(request, response);
        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        assertEquals("ACCESS_DENIED", new JSONObject(output.toString()).getString("code"));
    }
}
