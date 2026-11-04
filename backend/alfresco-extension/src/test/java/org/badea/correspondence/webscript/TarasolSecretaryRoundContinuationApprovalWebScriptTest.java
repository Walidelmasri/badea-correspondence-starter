package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoSecretaryRoundTwoRequestRepository;
import org.badea.correspondence.organization.TarasolSecretaryRoundContinuationApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Match;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class TarasolSecretaryRoundContinuationApprovalWebScriptTest {
    private static final String TECHNICAL_ID = "217a0e57-bc77-4475-a06f-c72aaa0949c4";
    private static final String REQUEST_ID = "12345678-1234-1234-1234-123456789abc";
    private final CurrentEmployeeService employees = mock(CurrentEmployeeService.class);
    private final TarasolSecretaryRoundContinuationApprovalService approvals =
        mock(TarasolSecretaryRoundContinuationApprovalService.class);
    private final WebScriptRequest request = mock(WebScriptRequest.class);
    private final WebScriptResponse response = mock(WebScriptResponse.class);
    private final Content content = mock(Content.class);
    private final StringWriter output = new StringWriter();
    private final DepartmentCode unit = new DepartmentCode("117");
    private final EmployeeDirectoryEntry manager = new EmployeeDirectoryEntry(
        new EmployeeId("200"), new DirectoryUsername("manager.test"),
        "Manager", "مدير", Optional.empty());

    @BeforeEach void setup() throws IOException {
        Match match = mock(Match.class);
        when(request.getServiceMatch()).thenReturn(match);
        when(match.getTemplateVars()).thenReturn(Map.of("technicalId", TECHNICAL_ID));
        when(request.getContent()).thenReturn(content);
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        when(employees.getCurrentEmployee()).thenReturn(manager);
    }

    private void body(String json) throws IOException {
        when(content.getContent()).thenReturn(json);
    }

    @Test void onlyAnExplicitlyReviewedRevisionMayBeApproved() throws IOException {
        body("{\"unitCode\":\"117\",\"action\":\"APPROVE_AND_CONTINUE\","
            + "\"requestId\":\"" + REQUEST_ID + "\",\"documentsRevision\":5}");
        when(approvals.approve(manager, unit, TECHNICAL_ID, REQUEST_ID, 5L)).thenReturn(
            new AlfrescoSecretaryRoundTwoRequestRepository.Decision(
                REQUEST_ID, TECHNICAL_ID, "APPROVED", 4L));
        new TarasolSecretaryRoundContinuationApprovalWebScript(employees, approvals, true)
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        verify(approvals).approve(manager, unit, TECHNICAL_ID, REQUEST_ID, 5L);
        verifyNoMoreInteractions(approvals);
    }

    @Test void missingReviewRevisionRejectsApprovalBeforeAnyTransition() throws IOException {
        body("{\"unitCode\":\"117\",\"action\":\"APPROVE_AND_CONTINUE\","
            + "\"requestId\":\"" + REQUEST_ID + "\"}");
        new TarasolSecretaryRoundContinuationApprovalWebScript(employees, approvals, true)
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_BAD_REQUEST);
        verifyNoInteractions(approvals);
    }

    @Test void staleReviewRevisionReturnsConflictWithoutRetrying() throws IOException {
        body("{\"unitCode\":\"117\",\"action\":\"APPROVE_AND_CONTINUE\","
            + "\"requestId\":\"" + REQUEST_ID + "\",\"documentsRevision\":3}");
        when(approvals.approve(manager, unit, TECHNICAL_ID, REQUEST_ID, 3L))
            .thenThrow(new OfficeCycleConflictException("Staged PDFs changed"));
        new TarasolSecretaryRoundContinuationApprovalWebScript(employees, approvals, true)
            .execute(request, response);
        verify(response).setStatus(409);
        verify(approvals).approve(manager, unit, TECHNICAL_ID, REQUEST_ID, 3L);
    }
}
