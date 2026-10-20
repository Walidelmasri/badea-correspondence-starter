package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.Map;
import java.util.Optional;

import org.badea.correspondence.application.CorrespondenceDocumentContentService;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.application.CorrespondenceDocumentStream;
import org.badea.correspondence.application.CurrentEmployeeService;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;

import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.extensions.webscripts.Match;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class GetCorrespondenceDocumentContentWebScriptTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private static final String NODE_ID =
            "22222222-2222-2222-2222-222222222222";

    private static final DepartmentCode DEPARTMENT_CODE =
            new DepartmentCode("117");

    private CurrentEmployeeService currentEmployeeService;

    private CorrespondenceActorRoleResolver actorRoleResolver;

    private CorrespondenceDocumentContentService contentService;

    private WebScriptRequest request;

    private WebScriptResponse response;

    private ByteArrayOutputStream binaryOutput;

    private StringWriter errorOutput;

    private PrintWriter errorWriter;

    private GetCorrespondenceDocumentContentWebScript webScript;

    @BeforeEach
    void setUp() throws IOException {

        currentEmployeeService =
                mock(CurrentEmployeeService.class);

        actorRoleResolver =
                mock(CorrespondenceActorRoleResolver.class);

        contentService =
                mock(CorrespondenceDocumentContentService.class);

        request =
                mock(WebScriptRequest.class);

        response =
                mock(WebScriptResponse.class);

        Match match =
                mock(Match.class);

        when(request.getServiceMatch())
                .thenReturn(match);

        when(match.getTemplateVars())
                .thenReturn(Map.of(
                        "technicalId", TECHNICAL_ID,
                        "nodeId", NODE_ID));

        binaryOutput =
                new ByteArrayOutputStream();

        errorOutput =
                new StringWriter();

        errorWriter =
                new PrintWriter(errorOutput);

        when(response.getOutputStream())
                .thenReturn(binaryOutput);

        when(response.getWriter())
                .thenReturn(errorWriter);

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT);

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(employee());

        webScript =
                new GetCorrespondenceDocumentContentWebScript(
                        currentEmployeeService,
                        actorRoleResolver,
                        contentService);
    }

    @Test
    void streamsAuthorizedPdfContent() throws IOException {

        byte[] expected = new byte[] {
                '%', 'P', 'D', 'F', '-', '1', '.', '7'
        };

        when(contentService.open(
                TECHNICAL_ID,
                NODE_ID,
                CorrespondenceActorRole.PRESIDENT,
                DEPARTMENT_CODE, "10001"))
                .thenReturn(
                        new CorrespondenceDocumentStream(
                                "main-letter.pdf",
                                "application/pdf",
                                new ByteArrayInputStream(expected)));

        webScript.execute(request, response);

        verify(response)
                .setStatus(Status.STATUS_OK);

        verify(response)
                .setContentType("application/pdf");

        verify(response)
                .setHeader(
                        "Cache-Control",
                        "private, no-store");

        verify(response)
                .setHeader(
                        "X-Content-Type-Options",
                        "nosniff");

        verify(response)
                .setHeader(
                        "Content-Disposition",
                        "inline; filename=\"document\""
                                + "; filename*=UTF-8''main-letter.pdf");

        assertArrayEquals(
                expected,
                binaryOutput.toByteArray());
    }

    @Test
    void returnsForbiddenForUnauthorizedDocument()
            throws IOException {

        when(contentService.open(
                TECHNICAL_ID,
                NODE_ID,
                CorrespondenceActorRole.PRESIDENT,
                DEPARTMENT_CODE, "10001"))
                .thenThrow(
                        new CorrespondenceAuthorizationException(
                                "Access denied."));

        webScript.execute(request, response);

        errorWriter.flush();

        verify(response)
                .setStatus(Status.STATUS_FORBIDDEN);

        JSONObject result =
                new JSONObject(errorOutput.toString());

        assertEquals(
                "CORRESPONDENCE_FORBIDDEN",
                result.getString("code"));

        verify(response, never())
                .setContentType("application/pdf");
    }

    @Test
    void returnsNotFoundForUnrelatedDocument()
            throws IOException {

        when(contentService.open(
                TECHNICAL_ID,
                NODE_ID,
                CorrespondenceActorRole.PRESIDENT,
                DEPARTMENT_CODE, "10001"))
                .thenThrow(
                        new CorrespondenceDocumentNotFoundException());

        webScript.execute(request, response);

        errorWriter.flush();

        verify(response)
                .setStatus(Status.STATUS_NOT_FOUND);

        JSONObject result =
                new JSONObject(errorOutput.toString());

        assertEquals(
                "DOCUMENT_NOT_FOUND",
                result.getString("code"));
    }

    @Test
    void preventsDocumentAccessWhenRoleResolutionFails()
            throws IOException {

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenThrow(
                        new CorrespondenceAuthorizationException(
                                "No correspondence role."));

        webScript.execute(request, response);

        errorWriter.flush();

        verify(response)
                .setStatus(Status.STATUS_FORBIDDEN);

        verify(contentService, never())
                .open(
                        TECHNICAL_ID,
                        NODE_ID,
                        CorrespondenceActorRole.PRESIDENT,
                        DEPARTMENT_CODE);

        assertEquals(
                0,
                binaryOutput.size());
    }

    private static EmployeeDirectoryEntry employee() {

        DepartmentDirectoryEntry department =
                new DepartmentDirectoryEntry(
                        DEPARTMENT_CODE,
                        "Digital Transformation",
                        null);

        return new EmployeeDirectoryEntry(
                new EmployeeId("10001"),
                new DirectoryUsername("walid.salem"),
                "Test Employee",
                null,
                Optional.of(department));
    }
}