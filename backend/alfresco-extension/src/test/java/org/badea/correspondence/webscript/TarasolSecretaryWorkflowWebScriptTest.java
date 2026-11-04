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
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceRepository;
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
    void personalDraftReadIncludesSavedMetadataForTheDirectorForm() throws Exception {
        when(employees.getCurrentEmployee()).thenReturn(secretary);
        when(request.getParameter("unitCode")).thenReturn("117");
        when(request.getParameter("view")).thenReturn("mine");
        when(request.getParameter("technicalId")).thenReturn("case-1");
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        var draft = new AlfrescoCorrespondenceRepository.SecretaryDraft(
                "case-1", "117", "Subject", "Body", "99999", "RETURNED", "Please revise");
        var metadata = new AlfrescoCorrespondenceRepository.SecretaryDraftMetadata(
                "DOC-117", LocalDate.of(2026, 11, 4), "Digital Transformation");
        when(workflow.personalDraft(eq(secretary), eq(new DepartmentCode("117")), eq("case-1")))
            .thenReturn(new TarasolSecretaryWorkflowService.PersonalDraftDetails(draft, metadata));
        var reader = new TarasolSecretaryWorkflowWebScript(employees, workflow, false);
        reader.execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        var json = new JSONObject(output.toString());
        assertEquals("RETURNED", json.getString("approvalStatus"));
        assertEquals("Please revise", json.getString("returnReason"));
        assertEquals("DOC-117", json.getString("documentNumber"));
        assertEquals("2026-11-04", json.getString("documentDate"));
        assertEquals("Digital Transformation", json.getString("sourceText"));
    }

    @Test
    void personalDraftsExposeOnlyServerFilteredEditableRowsWithRealTimestamps() throws Exception {
        when(employees.getCurrentEmployee()).thenReturn(secretary);
        when(request.getParameter("unitCode")).thenReturn("117");
        when(request.getParameter("view")).thenReturn("mine");
        when(request.getParameter("box")).thenReturn("drafts");
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        var draft = new AlfrescoCorrespondenceRepository.SecretaryDraft(
            "case-1", "117", "Subject", "Body", "99999", "RETURNED", "Please revise",
            "DOC-117", "2026-11-04T07:00:00Z", "2026-11-04T09:00:00Z");
        when(workflow.page(eq(secretary), eq(new DepartmentCode("117")), eq(false),
            eq(0), eq(20), eq("drafts")))
            .thenReturn(new AlfrescoCorrespondenceRepository.SecretaryPage(java.util.List.of(draft), 1));
        new TarasolSecretaryWorkflowWebScript(employees, workflow, false)
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        var row = new JSONObject(output.toString()).getJSONArray("items").getJSONObject(0);
        assertEquals("DOC-117", row.getString("documentNumber"));
        assertEquals("RETURNED", row.getString("approvalStatus"));
        assertEquals("2026-11-04T09:00:00Z", row.getString("modifiedAt"));
    }

    @Test
    void managerQueueCannotBeUsedAsAnArbitraryDraftDetailsRead() throws Exception {
        when(employees.getCurrentEmployee()).thenReturn(secretary);
        when(request.getParameter("unitCode")).thenReturn("117");
        when(request.getParameter("view")).thenReturn("pending");
        when(request.getParameter("technicalId")).thenReturn("case-1");
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        new TarasolSecretaryWorkflowWebScript(employees, workflow, false)
            .execute(request, response);
        verify(response).setStatus(Status.STATUS_BAD_REQUEST);
        verifyNoInteractions(workflow);
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
