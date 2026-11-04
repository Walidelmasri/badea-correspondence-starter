package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.badea.correspondence.application.CorrespondenceDetails;
import org.badea.correspondence.application.CorrespondenceDocument;
import org.badea.correspondence.application.CorrespondenceDocumentStream;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceHistoryReader;
import org.badea.correspondence.organization.TarasolSecretaryPersonalSentReadService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.webscripts.Match;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class TarasolSecretaryPersonalSentReadWebScriptTest {
    private static final String ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String DOC = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private final CurrentEmployeeService employees = mock(CurrentEmployeeService.class);
    private final TarasolSecretaryPersonalSentReadService service = mock(TarasolSecretaryPersonalSentReadService.class);
    private final WebScriptRequest request = mock(WebScriptRequest.class);
    private final WebScriptResponse response = mock(WebScriptResponse.class);
    private final StringWriter json = new StringWriter();
    private final ByteArrayOutputStream binary = new ByteArrayOutputStream();
    private final EmployeeDirectoryEntry actor = new EmployeeDirectoryEntry(
        new EmployeeId("99999"), new DirectoryUsername("walid.salem"),
        "Test Secretary", null, Optional.empty());
    private final DepartmentCode unit = new DepartmentCode("117");

    @BeforeEach
    void setup() throws Exception {
        Match match = mock(Match.class);
        when(request.getServiceMatch()).thenReturn(match);
        when(match.getTemplateVars()).thenReturn(Map.of("technicalId", ID, "nodeId", DOC));
        when(request.getParameter("unitCode")).thenReturn("117");
        when(employees.getCurrentEmployee()).thenReturn(actor);
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        when(response.getOutputStream()).thenReturn(binary);
    }

    @Test
    void detailsHaveTheExactExistingCorrespondenceJsonShape() throws Exception {
        var correspondence = new CorrespondenceDetails(ID, CorrespondenceState.WITH_DIRECTOR,
            "Subject", "Body", "REF-1", LocalDate.of(2026,11,4), "Source", "117",
            "Author", null, List.of(new CorrespondenceDocument(DOC, "source.pdf", "application/pdf", 1)));
        when(service.details(actor, unit, ID)).thenReturn(correspondence);
        new TarasolSecretaryPersonalSentReadWebScript(employees, service, "DETAILS")
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        var result = new JSONObject(json.toString());
        assertEquals(ID, result.getString("technicalId"));
        assertEquals(1, result.getJSONArray("documents").length());
        assertEquals("source.pdf", result.getJSONArray("documents").getJSONObject(0).getString("filename"));
    }

    @Test
    void historyHasTheExistingTimelineJsonShape() throws Exception {
        when(service.history(actor, unit, ID)).thenReturn(List.of(
            new AlfrescoCorrespondenceHistoryReader.ActionEntry(3, "SEND_TO_OFFICE",
                "PRESIDENT", "President", null, "WITH_PRESIDENT",
                "WITH_PRESIDENT_OFFICE", Instant.parse("2026-11-04T07:00:00Z"), "Approved")));
        new TarasolSecretaryPersonalSentReadWebScript(employees, service, "HISTORY")
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        JSONObject result = new JSONObject(json.toString());
        assertEquals(1, result.getInt("total"));
        assertEquals("Approved", result.getJSONArray("items").getJSONObject(0).getString("explanation"));
    }

    @Test
    void authorizedPdfStreamsInlineWithSafeHeaders() throws Exception {
        when(service.document(actor, unit, ID, DOC)).thenReturn(
            new CorrespondenceDocumentStream("source.pdf", "application/pdf",
                new ByteArrayInputStream(new byte[] {37, 80, 68, 70})));
        new TarasolSecretaryPersonalSentReadWebScript(employees, service, "DOCUMENT")
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        verify(response).setContentType("application/pdf");
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
        verify(response).setHeader("Content-Disposition",
            "inline; filename=\"document\"; filename*=UTF-8''source.pdf");
        assertArrayEquals(new byte[] {37,80,68,70}, binary.toByteArray());
    }

    @Test
    void deniedCaseNeverStreamsPdf() throws Exception {
        when(service.document(actor, unit, ID, DOC))
            .thenThrow(new CorrespondenceAuthorizationException("Not visible"));
        new TarasolSecretaryPersonalSentReadWebScript(employees, service, "DOCUMENT")
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        assertEquals(0, binary.size());
        assertEquals("ACCESS_DENIED", new JSONObject(json.toString()).getString("code"));
    }

    @Test
    void rejectsInvalidUnitBeforeAnyRead() throws Exception {
        when(request.getParameter("unitCode")).thenReturn("118 OR 1=1");
        new TarasolSecretaryPersonalSentReadWebScript(employees, service, "DETAILS")
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_BAD_REQUEST);
        verifyNoInteractions(service);
    }
}
