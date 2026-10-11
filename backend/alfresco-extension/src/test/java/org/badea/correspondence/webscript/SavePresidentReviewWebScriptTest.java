package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;

import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.PresidentReviewService;
import org.badea.correspondence.application.PresidentReviewState;
import org.badea.correspondence.application.PresidentReviewStateConflictException;
import org.badea.correspondence.application.SavePresidentReviewStateCommand;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
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

class SavePresidentReviewWebScriptTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private CurrentEmployeeService currentEmployeeService;
    private CorrespondenceActorRoleResolver actorRoleResolver;
    private PresidentReviewService presidentReviewService;

    private WebScriptRequest request;
    private WebScriptResponse response;

    private StringWriter responseBody;
    private PrintWriter responseWriter;

    private SavePresidentReviewWebScript webScript;

    @BeforeEach
    void setUp()
            throws IOException {

        currentEmployeeService =
                mock(
                        CurrentEmployeeService.class);

        actorRoleResolver =
                mock(
                        CorrespondenceActorRoleResolver.class);

        presidentReviewService =
                mock(
                        PresidentReviewService.class);

        request =
                mock(
                        WebScriptRequest.class);

        response =
                mock(
                        WebScriptResponse.class);

        responseBody =
                new StringWriter();

        responseWriter =
                new PrintWriter(
                        responseBody);

        when(response.getWriter())
                .thenReturn(
                        responseWriter);

        Match match =
                mock(
                        Match.class);

        when(request.getServiceMatch())
                .thenReturn(
                        match);

        when(match.getTemplateVars())
                .thenReturn(
                        Map.of(
                                "technicalId",
                                TECHNICAL_ID));

        webScript =
                new SavePresidentReviewWebScript(
                        currentEmployeeService,
                        actorRoleResolver,
                        presidentReviewService);
    }

    @Test
    void derivesPresidentIdentityServerSideAndSavesState()
            throws IOException {

        configureBody(
                """
                {
                  "expectedRevision": 2,
                  "actorEmployeeId": "99999",
                  "actorRole": "DIRECTOR",
                  "state": {
                    "schemaVersion": 1,
                    "selectedRecipientIds": ["117"]
                  }
                }
                """);

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(
                        CorrespondenceActorRole.PRESIDENT);

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(
                        employee());

        when(presidentReviewService.save(
                any(SavePresidentReviewStateCommand.class),
                any(CorrespondenceActorRole.class)))
                .thenReturn(
                        new PresidentReviewState(
                                3L,
                                """
                                {
                                  "schemaVersion": 1,
                                  "selectedRecipientIds": ["117"]
                                }
                                """));

        webScript.execute(
                request,
                response);

        responseWriter.flush();

        ArgumentCaptor<SavePresidentReviewStateCommand>
                commandCaptor =
                ArgumentCaptor.forClass(
                        SavePresidentReviewStateCommand.class);

        verify(presidentReviewService)
                .save(
                        commandCaptor.capture(),
                        org.mockito.ArgumentMatchers.eq(
                                CorrespondenceActorRole.PRESIDENT));

        SavePresidentReviewStateCommand command =
                commandCaptor.getValue();

        assertEquals(
                TECHNICAL_ID,
                command.technicalId());

        assertEquals(
                2L,
                command.expectedRevision());

        assertEquals(
                "18280",
                command.actorEmployeeId()
                        .value());

        JSONObject state =
                new JSONObject(
                        command.json());

        assertEquals(
                1,
                state.getInt(
                        "schemaVersion"));

        verify(response)
                .setStatus(
                        Status.STATUS_OK);

        JSONObject result =
                new JSONObject(
                        responseBody.toString());

        assertEquals(
                3L,
                result.getLong(
                        "revision"));
    }

    @Test
    void rejectsMissingStateBeforeResolvingIdentity()
            throws IOException {

        configureBody(
                """
                {
                  "expectedRevision": 0
                }
                """);

        webScript.execute(
                request,
                response);

        responseWriter.flush();

        verify(response)
                .setStatus(
                        Status.STATUS_BAD_REQUEST);

        verify(
                actorRoleResolver,
                never())
                .resolveCurrentUserRole();

        verify(
                currentEmployeeService,
                never())
                .getCurrentEmployee();

        verify(
                presidentReviewService,
                never())
                .save(
                        any(SavePresidentReviewStateCommand.class),
                        any(CorrespondenceActorRole.class));
    }

    @Test
    void returnsConflictForStaleRevision()
            throws IOException {

        configureBody(
                """
                {
                  "expectedRevision": 2,
                  "state": {
                    "schemaVersion": 1
                  }
                }
                """);

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(
                        CorrespondenceActorRole.PRESIDENT);

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(
                        employee());

        when(presidentReviewService.save(
                any(SavePresidentReviewStateCommand.class),
                any(CorrespondenceActorRole.class)))
                .thenThrow(
                        new PresidentReviewStateConflictException(
                                "Expected revision 2 but found 3."));

        webScript.execute(
                request,
                response);

        responseWriter.flush();

        verify(response)
                .setStatus(
                        409);

        JSONObject error =
                new JSONObject(
                        responseBody.toString());

        assertEquals(
                "PRESIDENT_REVIEW_CONFLICT",
                error.getString(
                        "code"));
    }

    private void configureBody(
            String json)
            throws IOException {

        Content content =
                mock(
                        Content.class);

        when(request.getContent())
                .thenReturn(
                        content);

        when(content.getContent())
                .thenReturn(
                        json);
    }

    private static EmployeeDirectoryEntry employee() {

        return new EmployeeDirectoryEntry(
                new EmployeeId(
                        "18280"),
                new DirectoryUsername(
                        "president.user"),
                "President User",
                "مستخدم الرئيس",
                Optional.empty());
    }
}