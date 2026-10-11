package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;

import org.badea.correspondence.application.PresidentReviewService;
import org.badea.correspondence.application.PresidentReviewState;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetPresidentReviewWebScriptTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private CorrespondenceActorRoleResolver actorRoleResolver;
    private PresidentReviewService presidentReviewService;

    private WebScriptRequest request;
    private WebScriptResponse response;

    private StringWriter responseBody;
    private PrintWriter responseWriter;

    private GetPresidentReviewWebScript webScript;

    @BeforeEach
    void setUp()
            throws IOException {

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
                new GetPresidentReviewWebScript(
                        actorRoleResolver,
                        presidentReviewService);
    }

    @Test
    void returnsSavedPresidentReviewState()
            throws IOException {

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(
                        CorrespondenceActorRole.PRESIDENT);

        when(presidentReviewService.load(
                TECHNICAL_ID,
                CorrespondenceActorRole.PRESIDENT))
                .thenReturn(
                        Optional.of(
                                new PresidentReviewState(
                                        3L,
                                        """
                                        {
                                          "schemaVersion": 1,
                                          "selectedRecipientIds": ["117"]
                                        }
                                        """)));

        webScript.execute(
                request,
                response);

        responseWriter.flush();

        verify(response)
                .setStatus(
                        Status.STATUS_OK);

        JSONObject result =
                new JSONObject(
                        responseBody.toString());

        assertEquals(
                TECHNICAL_ID,
                result.getString(
                        "technicalId"));

        assertEquals(
                3L,
                result.getLong(
                        "revision"));

        assertEquals(
                1,
                result.getJSONObject(
                                "state")
                        .getInt(
                                "schemaVersion"));
    }

    @Test
    void returnsRevisionZeroWhenNothingHasBeenSaved()
            throws IOException {

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(
                        CorrespondenceActorRole.PRESIDENT);

        when(presidentReviewService.load(
                TECHNICAL_ID,
                CorrespondenceActorRole.PRESIDENT))
                .thenReturn(
                        Optional.empty());

        webScript.execute(
                request,
                response);

        responseWriter.flush();

        JSONObject result =
                new JSONObject(
                        responseBody.toString());

        assertTrue(
        result.isNull(
                "state"));

        assertEquals(
                0L,
                result.getLong(
                        "revision"));

        verify(response)
                .setStatus(
                        Status.STATUS_OK);
    }

    @Test
    void returnsForbiddenForUnauthorizedUser()
            throws IOException {

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenThrow(
                        new CorrespondenceAuthorizationException(
                                "No valid role."));

        webScript.execute(
                request,
                response);

        responseWriter.flush();

        verify(response)
                .setStatus(
                        Status.STATUS_FORBIDDEN);

        verify(
                presidentReviewService,
                never())
                .load(
                        TECHNICAL_ID,
                        CorrespondenceActorRole.PRESIDENT);

        JSONObject error =
                new JSONObject(
                        responseBody.toString());

        assertEquals(
                "CORRESPONDENCE_FORBIDDEN",
                error.getString(
                        "code"));
    }
}