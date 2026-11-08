package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceReadReceipts;
import org.badea.correspondence.organization.TarasolSecretarySharedInboxService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Match;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class TarasolSecretaryInboxReadReceiptWebScriptTest {
    private static final String ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private final CurrentEmployeeService employees = mock(CurrentEmployeeService.class);
    private final TarasolSecretarySharedInboxService inbox = mock(TarasolSecretarySharedInboxService.class);
    private final AlfrescoCorrespondenceReadReceipts receipts = mock(AlfrescoCorrespondenceReadReceipts.class);
    private final WebScriptRequest request = mock(WebScriptRequest.class);
    private final WebScriptResponse response = mock(WebScriptResponse.class);
    private final Content content = mock(Content.class);
    private final StringWriter output = new StringWriter();
    private final EmployeeDirectoryEntry actor = new EmployeeDirectoryEntry(
        new EmployeeId("99999"), new DirectoryUsername("secretary.test"),
        "Secretary", null, Optional.empty());
    private final DepartmentCode unit = new DepartmentCode("117");

    @BeforeEach
    void setup() throws IOException {
        Match match = mock(Match.class);
        when(request.getServiceMatch()).thenReturn(match);
        when(match.getTemplateVars()).thenReturn(Map.of("technicalId", ID));
        when(request.getParameter("unitCode")).thenReturn("117");
        when(request.getContent()).thenReturn(content);
        when(content.getContent()).thenReturn("{\"throughSequence\":7}");
        when(employees.getCurrentEmployee()).thenReturn(actor);
        when(response.getWriter()).thenReturn(new PrintWriter(output));
    }

    @Test
    void onlyAuthorizedEmployeeMayAcknowledgeObservedIncomingSequence() throws Exception {
        // Emulate the real authorization boundary so the Supplier actually runs.
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
            .when(inbox).readCase(eq(actor), eq(unit), eq(ID), any());
        when(receipts.markRead(ID, CorrespondenceActorRole.DIRECTOR, unit,
                "99999", 7L, true)).thenReturn(7L);
        new TarasolSecretaryInboxReadReceiptWebScript(employees, inbox, receipts)
            .execute(request, response);
        verify(inbox).readCase(eq(actor), eq(unit), eq(ID), any());
        verify(receipts).markRead(ID, CorrespondenceActorRole.DIRECTOR, unit,
            "99999", 7L, true);
        verify(response).setStatus(Status.STATUS_OK);
        assertEquals(7L, new JSONObject(output.toString()).getLong("readThroughSequence"));
    }

    @Test
    void revokedAssignmentOrWrongUnitCannotWriteAnyReceipt() throws Exception {
        doThrow(new CorrespondenceAuthorizationException("No active secretary assignment"))
            .when(inbox).readCase(eq(actor), eq(unit), eq(ID), any());
        new TarasolSecretaryInboxReadReceiptWebScript(employees, inbox, receipts)
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        assertEquals("ACCESS_DENIED", new JSONObject(output.toString()).getString("code"));
        verifyNoInteractions(receipts);
    }

    @Test
    void invalidUnitOrSequenceDoesNotReachPrivilegedRead() throws Exception {
        when(request.getParameter("unitCode")).thenReturn("117 OR 1=1");
        new TarasolSecretaryInboxReadReceiptWebScript(employees, inbox, receipts)
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_BAD_REQUEST);
        verifyNoInteractions(inbox, receipts);
    }

    @Test
    void fractionalOrStringSequenceDoesNotReachPrivilegedRead() throws Exception {
        when(content.getContent()).thenReturn("{\"throughSequence\":1.5}");
        new TarasolSecretaryInboxReadReceiptWebScript(employees, inbox, receipts)
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_BAD_REQUEST);
        verifyNoInteractions(inbox, receipts);
    }

    @Test
    void negativeSequenceDoesNotReachPrivilegedRead() throws Exception {
        when(content.getContent()).thenReturn("{\"throughSequence\":-1}");
        new TarasolSecretaryInboxReadReceiptWebScript(employees, inbox, receipts)
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_BAD_REQUEST);
        verifyNoInteractions(inbox, receipts);
    }
}
