package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;

import org.badea.correspondence.application.CorrespondenceTransitionService;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.badea.correspondence.workflow.InvalidCorrespondenceTransitionException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class TransitionCorrespondenceWebScript
        extends AbstractWebScript {

    private static final int HTTP_CONFLICT = 409;

    private final CurrentEmployeeService currentEmployeeService;
    private final CorrespondenceActorRoleResolver actorRoleResolver;
    private final CorrespondenceTransitionService transitionService;

    public TransitionCorrespondenceWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver actorRoleResolver,
            CorrespondenceTransitionService transitionService) {

        this.currentEmployeeService =
                Objects.requireNonNull(
                        currentEmployeeService,
                        "currentEmployeeService must not be null");

        this.actorRoleResolver =
                Objects.requireNonNull(
                        actorRoleResolver,
                        "actorRoleResolver must not be null");

        this.transitionService =
                Objects.requireNonNull(
                        transitionService,
                        "transitionService must not be null");
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

            CorrespondenceAction action =
                    readAction(
                            body);

            DepartmentCode targetDepartmentCode =
                    readOptionalDepartmentCode(
                            body,
                            "targetDepartmentCode");

            String explanation =
                    readOptionalString(
                            body,
                            "explanation");

            String closureReason =
                    readOptionalString(
                            body,
                            "closureReason");

            CorrespondenceActorRole actorRole =
                    actorRoleResolver.resolveCurrentUserRole();

            EmployeeDirectoryEntry employee =
                    currentEmployeeService.getCurrentEmployee();

            DepartmentCode actorDepartmentCode =
                    employee.department()
                            .map(department -> department.code())
                            .orElse(null);

            TransitionCorrespondenceCommand command =
                    new TransitionCorrespondenceCommand(
                            technicalId,
                            actorRole,
                            action,
                            employee.employeeId(),
                            actorDepartmentCode,
                            employee.nameEnglish(),
                            employee.nameArabic(),
                            targetDepartmentCode,
                            explanation,
                            closureReason);

            CorrespondenceState state =
                    transitionService.transition(
                            command);

            JSONObject result =
                    new JSONObject();

            result.put(
                    "technicalId",
                    technicalId);

            result.put(
                    "state",
                    state.name());

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
                    "The authenticated user is not authorized to perform this correspondence action.");

        } catch (InvalidCorrespondenceTransitionException exception) {

            writeError(
                    response,
                    HTTP_CONFLICT,
                    "INVALID_TRANSITION",
                    exception.getMessage());

        } catch (CorrespondenceRepositoryException exception) {

            writeError(
                    response,
                    Status.STATUS_INTERNAL_SERVER_ERROR,
                    "CORRESPONDENCE_REPOSITORY_ERROR",
                    "The correspondence operation could not be completed.");

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

    private static CorrespondenceAction readAction(
            JSONObject body) {

        String value =
                readRequiredString(
                        body,
                        "action");

        try {
            return CorrespondenceAction.valueOf(
                    value);
        } catch (IllegalArgumentException exception) {

            throw new IllegalArgumentException(
                    "Unknown correspondence action: "
                            + value,
                    exception);
        }
    }

    private static DepartmentCode readOptionalDepartmentCode(
            JSONObject body,
            String fieldName) {

        String value =
                readOptionalString(
                        body,
                        fieldName);

        if (value == null) {
            return null;
        }

        return new DepartmentCode(
                value);
    }

    private static String readRequiredString(
            JSONObject body,
            String fieldName) {

        String value =
                readOptionalString(
                        body,
                        fieldName);

        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
        }

        return value;
    }

    private static String readOptionalString(
            JSONObject body,
            String fieldName) {

        if (!body.has(fieldName)
                || body.isNull(fieldName)) {

            return null;
        }

        String value =
                body.getString(
                        fieldName);

        if (value.isBlank()) {
            return null;
        }

        return value.trim();
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