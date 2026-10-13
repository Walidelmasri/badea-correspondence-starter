package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.badea.correspondence.application.CorrespondenceDetails;
import org.badea.correspondence.application.CorrespondenceDetailsService;
import org.badea.correspondence.application.CorrespondenceDocument;
import org.badea.correspondence.application.CurrentEmployeeService;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;

import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

import org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.extensions.webscripts.Match;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class GetCorrespondenceDetailsWebScriptTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private CurrentEmployeeService currentEmployeeService;
    private CorrespondenceActorRoleResolver actorRoleResolver;
    private CorrespondenceDetailsService detailsService;

    private WebScriptRequest request;
    private WebScriptResponse response;

    private StringWriter responseBody;
    private PrintWriter responseWriter;

    private GetCorrespondenceDetailsWebScript webScript;

    @BeforeEach
    void setUp() throws IOException {

        currentEmployeeService =
                mock(CurrentEmployeeService.class);

        actorRoleResolver =
                mock(CorrespondenceActorRoleResolver.class);

        detailsService =
                mock(CorrespondenceDetailsService.class);

        request =
                mock(WebScriptRequest.class);

        response =
                mock(WebScriptResponse.class);

        responseBody =
                new StringWriter();

        responseWriter =
                new PrintWriter(responseBody);

        when(response.getWriter())
                .thenReturn(responseWriter);

        Match match =
                mock(Match.class);

        when(request.getServiceMatch())
                .thenReturn(match);

        when(match.getTemplateVars())
                .thenReturn(Map.of(
                        "technicalId",
                        TECHNICAL_ID));

        webScript =
                new GetCorrespondenceDetailsWebScript(
                        currentEmployeeService,
                        actorRoleResolver,
                        detailsService);
    }

    @Test
    void returnsAuthorizedCorrespondenceAndDocuments()
            throws IOException {

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.PRESIDENT);

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(employee());

        CorrespondenceDetails details =
                new CorrespondenceDetails(
                        TECHNICAL_ID,
                        CorrespondenceState.WITH_PRESIDENT,
                        "Test correspondence",
                        "Please review the attached documents.",
                        "DOC-2026-001",
                        LocalDate.of(2026, 10, 13),
                        "Digital Transformation",
                        "117",
                        "Test Employee",
                        null,
                        List.of(
                                new CorrespondenceDocument(
                                        "22222222-2222-2222-2222-222222222222",
                                        "main-letter.pdf",
                                        "application/pdf",
                                        4096L)));

        when(detailsService.getDetails(
                TECHNICAL_ID,
                CorrespondenceActorRole.PRESIDENT,
                new DepartmentCode("117")))
                .thenReturn(details);

        webScript.execute(request, response);
        responseWriter.flush();

        verify(response)
                .setStatus(Status.STATUS_OK);

        JSONObject result =
                new JSONObject(responseBody.toString());

        assertEquals(
                TECHNICAL_ID,
                result.getString("technicalId"));

        assertEquals(
                "WITH_PRESIDENT",
                result.getString("state"));

        assertEquals(
                "Test correspondence",
                result.getString("subject"));

        assertEquals(
                "2026-10-13",
                result.getString("documentDate"));

        assertTrue(
                result.isNull("createdByNameArabic"));

        assertEquals(
                1,
                result.getJSONArray("documents").length());

        JSONObject document =
                result.getJSONArray("documents")
                        .getJSONObject(0);

        assertEquals(
                "main-letter.pdf",
                document.getString("filename"));

        assertEquals(
                "application/pdf",
                document.getString("mimeType"));

        assertEquals(
                4096L,
                document.getLong("sizeBytes"));
    }

    @Test
    void rejectsUnauthorizedCorrespondenceRead()
            throws IOException {

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.DIRECTOR);

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(employee());

        when(detailsService.getDetails(
                TECHNICAL_ID,
                CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("117")))
                .thenThrow(
                        new CorrespondenceAuthorizationException(
                                "Access denied"));

        webScript.execute(request, response);
        responseWriter.flush();

        verify(response)
                .setStatus(Status.STATUS_FORBIDDEN);

        JSONObject result =
                new JSONObject(responseBody.toString());

        assertEquals(
                "CORRESPONDENCE_FORBIDDEN",
                result.getString("code"));
    }

    @Test
    void doesNotReadDetailsWhenRoleResolutionFails()
            throws IOException {

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenThrow(
                        new CorrespondenceAuthorizationException(
                                "No correspondence role"));

        webScript.execute(request, response);

        verify(response)
                .setStatus(Status.STATUS_FORBIDDEN);

        verify(detailsService, never())
                .getDetails(
                        eq(TECHNICAL_ID),
                        eq(CorrespondenceActorRole.DIRECTOR),
                        eq(new DepartmentCode("117")));
    }

    private static EmployeeDirectoryEntry employee() {

        DepartmentDirectoryEntry department =
                new DepartmentDirectoryEntry(
                        new DepartmentCode("117"),
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