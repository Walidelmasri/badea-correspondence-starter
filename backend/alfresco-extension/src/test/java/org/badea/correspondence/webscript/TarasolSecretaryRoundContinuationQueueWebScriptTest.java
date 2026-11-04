package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import org.badea.correspondence.organization.TarasolSecretaryRoundContinuationApprovalService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class TarasolSecretaryRoundContinuationQueueWebScriptTest {
    private final CurrentEmployeeService employees = mock(CurrentEmployeeService.class);
    private final TarasolSecretaryRoundContinuationApprovalService queue =
        mock(TarasolSecretaryRoundContinuationApprovalService.class);
    private final WebScriptRequest request = mock(WebScriptRequest.class);
    private final WebScriptResponse response = mock(WebScriptResponse.class);
    private final EmployeeDirectoryEntry manager = new EmployeeDirectoryEntry(
        new EmployeeId("200"), new DirectoryUsername("manager.test"),
        "Manager", "مدير", Optional.empty());

    @Test void returnsSelectedManagerUnitWithoutTrustingClientRole() throws Exception {
        when(request.getParameter("unitCode")).thenReturn("117");
        when(request.getParameter("offset")).thenReturn("0");
        when(request.getParameter("limit")).thenReturn("10");
        when(employees.getCurrentEmployee()).thenReturn(manager);
        var found = new TarasolSecretaryRoundContinuationApprovalService.PendingItem(
            "217a0e57-bc77-4475-a06f-c72aaa0949c4",
            "12345678-1234-1234-1234-123456789abc", "117", "99999",
            "Follow-up", "117-A", "Recheck attachments", 3,
            Instant.parse("2026-11-04T15:00:00Z"));
        when(queue.pendingQueue(manager, new DepartmentCode("117"), 0, 10)).thenReturn(
            new TarasolSecretaryRoundContinuationApprovalService.PendingPage(
                List.of(found), 1, 0, 10));
        StringWriter buffer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(buffer));
        new TarasolSecretaryRoundContinuationQueueWebScript(employees, queue)
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        var body = new JSONObject(buffer.toString());
        assertEquals(1, body.getInt("total"));
        assertEquals("99999", body.getJSONArray("items").getJSONObject(0)
            .getString("ownerEmployeeId"));
        verify(queue).pendingQueue(manager, new DepartmentCode("117"), 0, 10);
    }

    @Test void deniesUnknownManagerWithoutLeakingQueue() throws Exception {
        when(request.getParameter("unitCode")).thenReturn("117");
        when(employees.getCurrentEmployee()).thenReturn(manager);
        when(queue.pendingQueue(eq(manager), any(), eq(0), eq(10))).thenThrow(
            new CorrespondenceAuthorizationException("Manager appointment required"));
        StringWriter buffer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(buffer));
        new TarasolSecretaryRoundContinuationQueueWebScript(employees, queue)
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        assertEquals("ACCESS_DENIED", new JSONObject(buffer.toString()).getString("code"));
    }
}
