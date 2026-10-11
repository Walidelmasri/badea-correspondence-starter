package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;

import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.PresidentReviewService;
import org.badea.correspondence.application.PresidentReviewState;
import org.badea.correspondence.application.PresidentReviewStateConflictException;
import org.badea.correspondence.application.SavePresidentReviewStateCommand;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class SavePresidentReviewWebScript
        extends AbstractWebScript {

    private static final int HTTP_CONFLICT = 409;

    private final CurrentEmployeeService currentEmployeeService;
    private final CorrespondenceActorRoleResolver actorRoleResolver;
    private final PresidentReviewService presidentReviewService;

    public SavePresidentReviewWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver actorRoleResolver,
            PresidentReviewService presidentReviewService) {

        this.currentEmployeeService =
                Objects.requireNonNull(
                        currentEmployeeService,
                        "currentEmployeeService must not be null");

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

            JSONObject body =
                    readJsonBody(
                            request);

            long expectedRevision =
                    readExpectedRevision(
                            body);

            JSONObject reviewState =
                    readReviewState(
                            body);

            CorrespondenceActorRole actorRole =
                    actorRoleResolver.resolveCurrentUserRole();

            EmployeeDirectoryEntry employee =
                    currentEmployeeService.getCurrentEmployee();

            SavePresidentReviewStateCommand command =
                    new SavePresidentReviewStateCommand(
                            technicalId,
                            expectedRevision,
                            employee.employeeId(),
                            reviewState.toString());

            PresidentReviewState saved =
                    presidentReviewService.save(
                            command,
                            actorRole);

            JSONObject result =
                    new JSONObject();

            result.put(
                    "technicalId",
                    technicalId);

            result.put(
                    "revision",
                    saved.revision());

            result.put(
                    "state",
                    new JSONObject(
                            saved.json()));

            writeJson(
                    response,
                    Status.STATUS_OK,
                    result);

        } catch (CurrentEmployeeNotFoundException exception) {

            writeError(
                    response,
                    Status.STATUS_NOT_FOUND,
                    "EMPLOYEE_NOT_FOUND",
                    "No active employee profile was found for the authenticated user.");

        } catch (EmployeeDirectoryException exception) {

            writeError(
                    response,
                    Status.STATUS_SERVICE_UNAVAILABLE,
                    "EMPLOYEE_DIRECTORY_UNAVAILABLE",
                    "The employee directory is currently unavailable.");

        } catch (CorrespondenceAuthorizationException exception) {

            writeError(
                    response,
                    Status.STATUS_FORBIDDEN,
                    "CORRESPONDENCE_FORBIDDEN",
                    "The authenticated user is not authorized to update this President review.");

        } catch (PresidentReviewStateConflictException exception) {

            writeError(
                    response,
                    HTTP_CONFLICT,
                    "PRESIDENT_REVIEW_CONFLICT",
                    "The President review has changed since it was loaded. Reload before saving again.");

        } catch (CorrespondenceRepositoryException exception) {

            writeError(
                    response,
                    Status.STATUS_INTERNAL_SERVER_ERROR,
                    "CORRESPONDENCE_REPOSITORY_ERROR",
                    "The President review state could not be saved.");

        } catch (JSONException
                | IllegalArgumentException exception) {

            writeError(
                    response,
                    Status.STATUS_BAD_REQUEST,
                    "INVALID_REQUEST",
                    exception.getMessage());
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

    private static JSONObject readJsonBody(
            WebScriptRequest request)
            throws IOException {

        Content content =
                request.getContent();

        if (content == null) {

            throw new IllegalArgumentException(
                    "Request body is required");
        }

        String raw =
                content.getContent();

        if (raw == null
                || raw.isBlank()) {

            throw new IllegalArgumentException(
                    "Request body is required");
        }

        return new JSONObject(
                raw);
    }

    private static long readExpectedRevision(
            JSONObject body) {

        if (!body.has(
                "expectedRevision")
                || body.isNull(
                        "expectedRevision")) {

            throw new IllegalArgumentException(
                    "expectedRevision is required");
        }

        long revision =
                body.getLong(
                        "expectedRevision");

        if (revision < 0L) {

            throw new IllegalArgumentException(
                    "expectedRevision must not be negative");
        }

        return revision;
    }

    private static JSONObject readReviewState(
            JSONObject body) {

        if (!body.has(
                "state")
                || body.isNull(
                        "state")) {

            throw new IllegalArgumentException(
                    "state is required");
        }

        return body.getJSONObject(
                "state");
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