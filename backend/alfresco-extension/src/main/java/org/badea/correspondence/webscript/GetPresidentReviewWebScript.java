package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.badea.correspondence.application.PresidentReviewService;
import org.badea.correspondence.application.PresidentReviewState;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class GetPresidentReviewWebScript
        extends AbstractWebScript {

    private final CorrespondenceActorRoleResolver actorRoleResolver;
    private final PresidentReviewService presidentReviewService;

    public GetPresidentReviewWebScript(
            CorrespondenceActorRoleResolver actorRoleResolver,
            PresidentReviewService presidentReviewService) {

        this.actorRoleResolver =
                Objects.requireNonNull(
                        actorRoleResolver,
                        "actorRoleResolver must not be null");

        this.presidentReviewService =
                Objects.requireNonNull(
                        presidentReviewService,
                        "presidentReviewService must not be null");
    }

    @Override
    public void execute(
            WebScriptRequest request,
            WebScriptResponse response)
            throws IOException {

        try {
            String technicalId =
                    readTechnicalId(
                            request);

            CorrespondenceActorRole actorRole =
                    actorRoleResolver.resolveCurrentUserRole();

            Optional<PresidentReviewState> savedState =
                    presidentReviewService.load(
                            technicalId,
                            actorRole);

            JSONObject result =
                    new JSONObject();

            result.put(
                    "technicalId",
                    technicalId);

            if (savedState.isEmpty()) {

                result.put(
                        "revision",
                        0L);

                result.put(
                        "state",
                        JSONObject.NULL);

            } else {

                PresidentReviewState state =
                        savedState.orElseThrow();

                result.put(
                        "revision",
                        state.revision());

                result.put(
                        "state",
                        parsePersistedState(
                                state.json()));
            }

            writeJson(
                    response,
                    Status.STATUS_OK,
                    result);

        } catch (CorrespondenceAuthorizationException exception) {

            writeError(
                    response,
                    Status.STATUS_FORBIDDEN,
                    "CORRESPONDENCE_FORBIDDEN",
                    "The authenticated user is not authorized to access this President review.");

        } catch (CorrespondenceRepositoryException exception) {

            writeError(
                    response,
                    Status.STATUS_INTERNAL_SERVER_ERROR,
                    "CORRESPONDENCE_REPOSITORY_ERROR",
                    "The President review state could not be loaded.");

        } catch (IllegalArgumentException exception) {

            writeError(
                    response,
                    Status.STATUS_BAD_REQUEST,
                    "INVALID_REQUEST",
                    exception.getMessage());
        }
    }

    private static JSONObject parsePersistedState(
            String json) {

        try {
            return new JSONObject(
                    json);

        } catch (JSONException exception) {

            throw new CorrespondenceRepositoryException(
                    "Persisted President review state is not valid JSON.",
                    exception);
        }
    }

    private static String readTechnicalId(
            WebScriptRequest request) {

        String technicalId =
                request.getServiceMatch()
                        .getTemplateVars()
                        .get("technicalId");

        if (technicalId == null
                || technicalId.isBlank()) {

            throw new IllegalArgumentException(
                    "technicalId must not be blank");
        }

        return technicalId.trim();
    }

    private static void writeJson(
            WebScriptResponse response,
            int status,
            JSONObject json)
            throws IOException {

        response.setStatus(
                status);

        response.setContentType(
                "application/json;charset=UTF-8");

        response.getWriter()
                .write(
                        json.toString());
    }

    private static void writeError(
            WebScriptResponse response,
            int status,
            String code,
            String message)
            throws IOException {

        JSONObject error =
                new JSONObject();

        error.put(
                "code",
                code);

        error.put(
                "message",
                message);

        writeJson(
                response,
                status,
                error);
    }
}