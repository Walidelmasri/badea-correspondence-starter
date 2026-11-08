package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoSecretaryRoundTwoRequestRepository;
import org.badea.correspondence.organization.TarasolSecretaryRoundTwoRequestService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Match;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class TarasolSecretaryRoundTwoRevisionWebScriptTest {
    private static final String CASE = "217a0e57-bc77-4475-a06f-c72aaa0949c4";
    private static final String REQUEST = "12345678-1234-1234-1234-123456789abc";
    private final CurrentEmployeeService employees = mock(CurrentEmployeeService.class);
    private final TarasolSecretaryRoundTwoRequestService service =
        mock(TarasolSecretaryRoundTwoRequestService.class);
    private final WebScriptRequest request = mock(WebScriptRequest.class);
    private final WebScriptResponse response = mock(WebScriptResponse.class);
    private final Content content = mock(Content.class);
    private final StringWriter output = new StringWriter();
    private final EmployeeDirectoryEntry actor = new EmployeeDirectoryEntry(
        new EmployeeId("99999"), new DirectoryUsername("sec.test"),
        "Secretary", "سكرتير", Optional.empty());
    private final DepartmentCode unit = new DepartmentCode("117");

    @BeforeEach void setup() throws IOException {
        Match match = mock(Match.class);
        when(request.getServiceMatch()).thenReturn(match);
        when(match.getTemplateVars()).thenReturn(Map.of("technicalId", CASE));
        when(request.getParameter("unitCode")).thenReturn("117");
        when(request.getContent()).thenReturn(content);
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        when(employees.getCurrentEmployee()).thenReturn(actor);
    }

    private void body(String json) throws Exception { when(content.getContent()).thenReturn(json); }

    private AlfrescoSecretaryRoundTwoRequestRepository.Request saved(String status) {
        return new AlfrescoSecretaryRoundTwoRequestRepository.Request(REQUEST, CASE,
            "117", "99999", "New reason", 4L, status,
            Instant.parse("2026-11-04T15:00:00Z"), "Correct the PDF", 2L);
    }

    @Test void eligibilityIsReadOnlyAndReturnsA200ForIneligibleCorrespondence() throws Exception {
        var result = new AlfrescoSecretaryRoundTwoRequestRepository.Eligibility(
            false, false, null, 3L, "DEPARTMENT_NOT_READY");
        when(service.eligibility(actor, unit, CASE)).thenReturn(result);
        new TarasolSecretaryRoundTwoRequestWebScript(employees, service, "ELIGIBILITY")
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        var body = new JSONObject(output.toString());
        assertFalse(body.getBoolean("canPrepare"));
        assertFalse(body.getBoolean("canReadRequest"));
        assertTrue(body.isNull("requestStatus"));
        assertEquals(3, body.getLong("nextCycle"));
        verify(service).eligibility(actor, unit, CASE);
        verifyNoMoreInteractions(service);
    }

    @Test void reviseAcceptsOnlyMatchingRequestVersionAndReturnsPreservedManagerReason() throws Exception {
        body("{\"requestId\":\"" + REQUEST + "\",\"returnVersion\":2,\"reason\":\"New reason\"}");
        when(service.revise(actor, unit, CASE, REQUEST, 2L, "New reason"))
            .thenReturn(saved("RETURNED"));
        new TarasolSecretaryRoundTwoRequestWebScript(employees, service, "REVISE")
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        JSONObject json = new JSONObject(output.toString());
        assertEquals(2, json.getLong("returnVersion"));
        assertEquals("Correct the PDF", json.getString("returnReason"));
        assertEquals("RETURNED", json.getString("status"));
    }

    @Test void explicitPreparedSubmissionUsesOwnerAuthorizedService() throws Exception {
        body("{\"requestId\":\"" + REQUEST + "\"}");
        when(service.submitPrepared(actor, unit, CASE, REQUEST)).thenReturn(saved("PENDING"));
        new TarasolSecretaryRoundTwoRequestWebScript(employees, service, "SUBMIT_PREPARED")
            .execute(request, response);
        assertEquals("PENDING", new JSONObject(output.toString()).getString("status"));
        verify(service).submitPrepared(actor, unit, CASE, REQUEST);
    }

    @Test void resubmitCallsOnlyManagerGateNotDirectorContinuation() throws Exception {
        body("{\"requestId\":\"" + REQUEST + "\",\"returnVersion\":2}");
        when(service.resubmit(actor, unit, CASE, REQUEST, 2L)).thenReturn(saved("PENDING"));
        new TarasolSecretaryRoundTwoRequestWebScript(employees, service, "RESUBMIT")
            .execute(request, response);
        assertEquals("PENDING", new JSONObject(output.toString()).getString("status"));
        verify(service).resubmit(actor, unit, CASE, REQUEST, 2L);
        verifyNoMoreInteractions(service);
    }

    @Test void staleRevisionReturnsConflictWithoutAcceptingDuplicateSubmission() throws Exception {
        body("{\"requestId\":\"" + REQUEST + "\",\"returnVersion\":1}");
        when(service.resubmit(actor, unit, CASE, REQUEST, 1L)).thenThrow(
            new OfficeCycleConflictException("Request was already returned again"));
        new TarasolSecretaryRoundTwoRequestWebScript(employees, service, "RESUBMIT")
            .execute(request, response);
        verify(response).setStatus(409);
        assertEquals("CYCLE_CONFLICT", new JSONObject(output.toString()).getString("code"));
    }

    @Test void revokedSecretaryIsDeniedWithoutExposingRequestData() throws Exception {
        body("{\"requestId\":\"" + REQUEST + "\",\"returnVersion\":2}");
        when(service.resubmit(actor, unit, CASE, REQUEST, 2L)).thenThrow(
            new CorrespondenceAuthorizationException("Not authorized"));
        new TarasolSecretaryRoundTwoRequestWebScript(employees, service, "RESUBMIT")
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        assertEquals("ACCESS_DENIED", new JSONObject(output.toString()).getString("code"));
    }

    @Test void invalidVersionOrExtraPayloadFieldsCannotReachRepository() throws Exception {
        body("{\"requestId\":\"" + REQUEST + "\",\"returnVersion\":2.5}");
        new TarasolSecretaryRoundTwoRequestWebScript(employees, service, "RESUBMIT")
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_BAD_REQUEST);
        verifyNoInteractions(service);
    }
}
