package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.Optional;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.organization.TarasolSecretaryWorkflowService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class TarasolSecretaryWorkflowWebScriptTest {
    private final CurrentEmployeeService employees = mock(CurrentEmployeeService.class);
    private final TarasolSecretaryWorkflowService workflow = mock(TarasolSecretaryWorkflowService.class);
    private final WebScriptRequest request = mock(WebScriptRequest.class);
    private final WebScriptResponse response = mock(WebScriptResponse.class);
    private final Content content = mock(Content.class);
    private final StringWriter output = new StringWriter();
    private final EmployeeDirectoryEntry secretary = new EmployeeDirectoryEntry(
        new EmployeeId("99999"), new DirectoryUsername("walid.salem"),
        "Test Secretary", null, Optional.empty());
    private final TarasolSecretaryWorkflowWebScript script =
        new TarasolSecretaryWorkflowWebScript(employees, workflow, true);

    private void payload(String json) throws Exception {
        when(employees.getCurrentEmployee()).thenReturn(secretary);
        when(request.getContent()).thenReturn(content);
        when(content.getContent()).thenReturn(json);
        when(response.getWriter()).thenReturn(new PrintWriter(output));
    }

    @Test
    void createsFullDirectorMetadataWithoutAcceptingSpoofedCreatorOrUnit() throws Exception {
        payload("""
            {"unitCode":"117","action":"CREATE_DRAFT","subject":"Subject", "bodyText":"Body",
             "documentNumber":"DT-2026-5","documentDate":"2026-11-04", "sourceText":"Digital Transformation",
             "creatorEmployeeId":"12345", "originDepartmentCode":"101"}
            """);
        when(workflow.create(eq(secretary), eq(new DepartmentCode("117")), eq("Subject"),
            eq("Body"), eq("DT-2026-5"), eq(LocalDate.of(2026, 11, 4)),
            eq("Digital Transformation"))).thenReturn("case-1");

        script.execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        assertEquals("case-1", new JSONObject(output.toString()).getString("technicalId"));
        verify(workflow).create(eq(secretary), eq(new DepartmentCode("117")),
            eq("Subject"), eq("Body"), eq("DT-2026-5"),
            eq(LocalDate.of(2026, 11, 4)), eq("Digital Transformation"));
    }

    @Test
    void revisesCompleteDocumentMetadataAndCanClearOptionalValues() throws Exception {
        payload("""
            {"unitCode":"117","action":"REVISE","technicalId":"case-1",
             "subject":"Updated", "bodyText":"Body", "documentNumber":null,
             "documentDate":null,"sourceText":null}
            """);

        script.execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        verify(workflow).revise(eq(secretary), eq(new DepartmentCode("117")),
            eq("case-1"), eq("Updated"), eq("Body"),
            isNull(), isNull(), isNull(), eq(true));
    }

    @Test
    void oldSubjectOnlyRevisionDoesNotEraseStoredMetadata() throws Exception {
        payload("""
            {"unitCode":"117","action":"REVISE","technicalId":"case-1",
             "subject":"Updated", "bodyText":"Body"}
            """);

        script.execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        verify(workflow).revise(eq(secretary), eq(new DepartmentCode("117")),
            eq("case-1"), eq("Updated"), eq("Body"),
            isNull(), isNull(), isNull(), eq(false));
    }

    @Test
    void incompleteMetadataUpdateIsRejectedRatherThanSilentlyErased() throws Exception {
        payload("""
            {"unitCode":"117","action":"REVISE","technicalId":"case-1",
             "subject":"Updated", "documentNumber":"DT-2026-5"}
            """);

        script.execute(request, response);
        verify(response).setStatus(Status.STATUS_BAD_REQUEST);
        verifyNoInteractions(workflow);
    }

    @Test
    void invalidDocumentDateIsRejectedWithoutCreatingDraft() throws Exception {
        payload("""
            {"unitCode":"117","action":"CREATE_DRAFT","subject":"Subject",
             "documentDate":"04-11-2026"}
            """);

        script.execute(request, response);
        verify(response).setStatus(Status.STATUS_BAD_REQUEST);
        verifyNoInteractions(workflow);
    }
}
