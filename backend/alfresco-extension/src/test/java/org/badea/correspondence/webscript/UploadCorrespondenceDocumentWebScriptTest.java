package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.badea.correspondence.application.CorrespondenceDocument;
import org.badea.correspondence.application.CurrentEmployeeService;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;

import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceDraftDocumentUploader;

import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Match;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class UploadCorrespondenceDocumentWebScriptTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private static final String DOCUMENT_ID =
            "22222222-2222-2222-2222-222222222222";

    private static final DepartmentCode DEPARTMENT =
            new DepartmentCode("117");

    private static final EmployeeId EMPLOYEE_ID =
            new EmployeeId("10001");

    private static final byte[] PDF_BYTES =
            "%PDF-1.7\nTest PDF"
                    .getBytes(StandardCharsets.US_ASCII);

    private CurrentEmployeeService currentEmployeeService;

    private CorrespondenceActorRoleResolver actorRoleResolver;

    private AlfrescoCorrespondenceDraftDocumentUploader uploader;

    private WebScriptRequest request;
    private WebScriptResponse response;
    private Content content;

    private StringWriter responseBody;
    private PrintWriter responseWriter;

    private UploadCorrespondenceDocumentWebScript webScript;

    @BeforeEach
    void setUp() throws IOException {

        currentEmployeeService =
                mock(CurrentEmployeeService.class);

        actorRoleResolver =
                mock(CorrespondenceActorRoleResolver.class);

        uploader =
                mock(AlfrescoCorrespondenceDraftDocumentUploader.class);

        request =
                mock(WebScriptRequest.class);

        response =
                mock(WebScriptResponse.class);

        content =
                mock(Content.class);

        Match match =
                mock(Match.class);

        when(request.getServiceMatch())
                .thenReturn(match);

        when(match.getTemplateVars())
                .thenReturn(Map.of(
                        "technicalId",
                        TECHNICAL_ID));

        when(request.getParameter("filename"))
                .thenReturn("main-letter.pdf");

        when(request.getContentType())
                .thenReturn("application/pdf");

        when(request.getContent())
                .thenReturn(content);

        when(content.getInputStream())
                .thenReturn(new ByteArrayInputStream(PDF_BYTES));

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.DIRECTOR);

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(employee(true));

        responseBody =
                new StringWriter();

        responseWriter =
                new PrintWriter(responseBody);

        when(response.getWriter())
                .thenReturn(responseWriter);

        webScript =
                new UploadCorrespondenceDocumentWebScript(
                        currentEmployeeService,
                        actorRoleResolver,
                        uploader);
    }

    @Test
    void uploadsPdfOwnedByAuthenticatedDirector()
            throws IOException {

        when(uploader.uploadPdf(
                eq(TECHNICAL_ID),
                eq(CorrespondenceActorRole.DIRECTOR),
                eq(DEPARTMENT),
                eq(EMPLOYEE_ID),
                eq("main-letter.pdf"),
                any(byte[].class)))
                .thenReturn(
                        new CorrespondenceDocument(
                                DOCUMENT_ID,
                                "main-letter.pdf",
                                "application/pdf",
                                PDF_BYTES.length));

        JSONObject result = execute();

        verify(response)
                .setStatus(Status.STATUS_CREATED);

        assertEquals(
                DOCUMENT_ID,
                result.getString("nodeId"));

        assertEquals(
                "main-letter.pdf",
                result.getString("filename"));

        assertEquals(
                "application/pdf",
                result.getString("mimeType"));

        assertEquals(
                PDF_BYTES.length,
                result.getLong("sizeBytes"));

        ArgumentCaptor<byte[]> bytes =
                ArgumentCaptor.forClass(byte[].class);

        verify(uploader).uploadPdf(
        eq(TECHNICAL_ID),
        eq(CorrespondenceActorRole.DIRECTOR),
        eq(DEPARTMENT),
        eq(EMPLOYEE_ID),
        eq("main-letter.pdf"),
        bytes.capture());

        assertArrayEquals(
                PDF_BYTES,
                bytes.getValue());
    }

    @Test
    void rejectsUnauthorizedDirector()
            throws IOException {

        when(uploader.uploadPdf(
                eq(TECHNICAL_ID),
                eq(CorrespondenceActorRole.DIRECTOR),
                eq(DEPARTMENT),
                eq(EMPLOYEE_ID),
                eq("main-letter.pdf"),
                any(byte[].class)))
                .thenThrow(
                        new CorrespondenceAuthorizationException(
                                "Not the draft owner."));

        JSONObject result = execute();

        verify(response)
                .setStatus(Status.STATUS_FORBIDDEN);

        assertEquals(
                "CORRESPONDENCE_FORBIDDEN",
                result.getString("code"));
    }

    @Test
    void acceptsAuthenticatedPresidentPdfUpload() throws IOException {
        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT);
        when(uploader.uploadPdf(eq(TECHNICAL_ID),
                eq(CorrespondenceActorRole.PRESIDENT), eq(DEPARTMENT),
                eq(EMPLOYEE_ID), eq("main-letter.pdf"), any(byte[].class)))
                .thenReturn(new CorrespondenceDocument(DOCUMENT_ID,
                        "main-letter.pdf", "application/pdf", PDF_BYTES.length));

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_CREATED);
        assertEquals(DOCUMENT_ID, result.getString("nodeId"));
        verify(uploader).uploadPdf(eq(TECHNICAL_ID),
                eq(CorrespondenceActorRole.PRESIDENT), eq(DEPARTMENT),
                eq(EMPLOYEE_ID), eq("main-letter.pdf"), any(byte[].class));
    }

    @Test
    void rejectsUnauthorizedExecutiveUploader() throws IOException {
        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT_OFFICE);
        when(uploader.uploadPdf(eq(TECHNICAL_ID),
                eq(CorrespondenceActorRole.PRESIDENT_OFFICE), eq(DEPARTMENT),
                eq(EMPLOYEE_ID), eq("main-letter.pdf"), any(byte[].class)))
                .thenThrow(new CorrespondenceAuthorizationException("Not creator"));

        JSONObject result = execute();

        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        assertEquals("CORRESPONDENCE_FORBIDDEN", result.getString("code"));
    }

    @Test
    void rejectsEmployeeWithoutDepartment()
            throws IOException {

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(employee(false));

        JSONObject result = execute();

        verify(response)
                .setStatus(Status.STATUS_CONFLICT);

        assertEquals(
                "EMPLOYEE_DEPARTMENT_REQUIRED",
                result.getString("code"));

        verifyNoUpload();
    }

    @Test
    void rejectsNonPdfContentType()
            throws IOException {

        when(request.getContentType())
                .thenReturn("text/plain");

        JSONObject result = execute();

        verify(response)
                .setStatus(415);

        assertEquals(
                "UNSUPPORTED_MEDIA_TYPE",
                result.getString("code"));

        verify(content, never())
                .getInputStream();

        verifyNoUpload();
    }

    @Test
    void rejectsPayloadExceeding25MiB()
            throws IOException {

        byte[] oversized =
                new byte[
                        AlfrescoCorrespondenceDraftDocumentUploader
                                .MAX_PDF_BYTES + 1];

        when(content.getInputStream())
                .thenReturn(new ByteArrayInputStream(oversized));

        JSONObject result = execute();

        verify(response)
                .setStatus(413);

        assertEquals(
                "DOCUMENT_TOO_LARGE",
                result.getString("code"));

        verifyNoUpload();
    }

@Test
void rejectsInvalidPdfBytes()
        throws IOException {

    byte[] invalidBytes =
            "Not a PDF".getBytes(
                    StandardCharsets.UTF_8);

    when(content.getInputStream())
            .thenReturn(
                    new ByteArrayInputStream(invalidBytes));

    /*
     * PDF validation belongs to the uploader.
     * Mock its rejection rather than expecting
     * the Web Script to duplicate that validation.
     */
    when(uploader.uploadPdf(
            eq(TECHNICAL_ID),
            eq(CorrespondenceActorRole.DIRECTOR),
            eq(DEPARTMENT),
            eq(EMPLOYEE_ID),
            eq("main-letter.pdf"),
            any(byte[].class)))
            .thenThrow(
                    new IllegalArgumentException(
                            "Uploaded content is not a PDF."));

    JSONObject result = execute();

    verify(response)
            .setStatus(Status.STATUS_BAD_REQUEST);

    assertEquals(
            "INVALID_REQUEST",
            result.getString("code"));

    /*
     * The endpoint passed the bytes to the uploader.
     * The uploader rejected them before creating a node.
     */
    ArgumentCaptor<byte[]> bytes =
            ArgumentCaptor.forClass(byte[].class);

    verify(uploader).uploadPdf(
            eq(TECHNICAL_ID),
            eq(CorrespondenceActorRole.DIRECTOR),
            eq(DEPARTMENT),
            eq(EMPLOYEE_ID),
            eq("main-letter.pdf"),
            bytes.capture());

    assertArrayEquals(
            invalidBytes,
            bytes.getValue());
}

    private JSONObject execute()
            throws IOException {

        webScript.execute(
                request,
                response);

        responseWriter.flush();

        return new JSONObject(
                responseBody.toString());
    }

    private void verifyNoUpload() {

        verify(uploader, never())
                .uploadPdf(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
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
                EMPLOYEE_ID,
                new DirectoryUsername("walid.salem"),
                "Test Employee",
                null,
                department);
    }
}