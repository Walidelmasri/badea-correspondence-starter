package org.badea.correspondence.webscript;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.UpdateCorrespondenceDraftCommand;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;

import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceDraftUpdater;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;

import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

import org.json.JSONException;
import org.json.JSONObject;

import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class UpdateCorrespondenceDraftWebScript
        extends AbstractWebScript {

    private final CurrentEmployeeService currentEmployeeService;

    private final CorrespondenceActorRoleResolver actorRoleResolver;

    private final AlfrescoCorrespondenceDraftUpdater updater;

    public UpdateCorrespondenceDraftWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver actorRoleResolver,
            AlfrescoCorrespondenceDraftUpdater updater) {

        this.currentEmployeeService =
                Objects.requireNonNull(currentEmployeeService);

        this.actorRoleResolver =
                Objects.requireNonNull(actorRoleResolver);

        this.updater =
                Objects.requireNonNull(updater);
    }

    @Override
    public void execute(
            WebScriptRequest request,
            WebScriptResponse response)
            throws IOException {

        try {
            String technicalId =
                    readTechnicalId(request);

            CorrespondenceActorRole actorRole =
                    actorRoleResolver.resolveCurrentUserRole();

            if (actorRole != CorrespondenceActorRole.DIRECTOR) {
                throw new CorrespondenceAuthorizationException(
                        "Director role required.");
            }

            EmployeeDirectoryEntry employee =
                    currentEmployeeService.getCurrentEmployee();

            DepartmentCode departmentCode =
                    employee.department()
                            .map(department -> department.code())
                            .orElse(null);

            if (departmentCode == null) {
                writeError(
                        response,
                        Status.STATUS_CONFLICT,
                        "EMPLOYEE_DEPARTMENT_REQUIRED",
                        "The authenticated employee has no active department.");

                return;
            }

            JSONObject body =
                    readBody(request);

            UpdateCorrespondenceDraftCommand command =
                    new UpdateCorrespondenceDraftCommand(
                            technicalId,
                            actorRole,
                            departmentCode,
                            employee.employeeId(),
                            requiredText(body, "subject"),
                            optionalText(body, "bodyText"),
                            optionalText(body, "documentNumber"),
                            optionalDate(body, "documentDate"),
                            optionalText(body, "sourceText"));

            updater.updateDraft(command);

            JSONObject result = new JSONObject();

            result.put("technicalId", technicalId);
            result.put("state", CorrespondenceState.DRAFT.name());

            response.setHeader(
                    "Cache-Control",
                    "no-store");

            writeJson(
                    response,
                    Status.STATUS_OK,
                    result);

        } catch (CorrespondenceAuthorizationException exception) {

            writeError(
                    response,
                    Status.STATUS_FORBIDDEN,
                    "CORRESPONDENCE_FORBIDDEN",
                    "You are not authorized to modify this draft.");

        } catch (CurrentEmployeeNotFoundException exception) {

            writeError(
                    response,
                    Status.STATUS_NOT_FOUND,
                    "EMPLOYEE_NOT_FOUND",
                    "No active employee profile was found.");

        } catch (EmployeeDirectoryException exception) {

            writeError(
                    response,
                    Status.STATUS_SERVICE_UNAVAILABLE,
                    "EMPLOYEE_DIRECTORY_UNAVAILABLE",
                    "The employee directory is unavailable.");

        } catch (CorrespondenceRepositoryException exception) {

            writeError(
                    response,
                    Status.STATUS_INTERNAL_SERVER_ERROR,
                    "CORRESPONDENCE_REPOSITORY_ERROR",
                    "The correspondence draft could not be updated.");

        } catch (JSONException
                | DateTimeParseException
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

        if (technicalId == null || technicalId.isBlank()) {
            throw new IllegalArgumentException(
                    "technicalId is required.");
        }

        return technicalId.trim();
    }

    private static JSONObject readBody(
            WebScriptRequest request)
            throws IOException {

        Content content = request.getContent();

        if (content == null) {
            throw new IllegalArgumentException(
                    "Request body is required.");
        }

        String raw = content.getContent();

        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "Request body is required.");
        }

        JSONObject body = new JSONObject(raw);

        /*
         * PUT replaces all editable draft metadata.
         *
         * Require every field to be present so a partial
         * request cannot accidentally erase existing values.
         *
         * Optional fields may explicitly contain null.
         */
        for (String field : new String[] {
                "subject",
                "bodyText",
                "documentNumber",
                "documentDate",
                "sourceText"
        }) {

            if (!body.has(field)) {
                throw new IllegalArgumentException(
                        field + " must be supplied.");
            }
        }

        return body;
    }

    private static String requiredText(
            JSONObject body,
            String field) {

        if (body.isNull(field)) {
            throw new IllegalArgumentException(
                    field + " is required.");
        }

        String value = body.getString(field).trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    field + " must not be blank.");
        }

        return value;
    }

    private static String optionalText(
            JSONObject body,
            String field) {

        if (body.isNull(field)) {
            return null;
        }

        String value = body.getString(field).trim();

        return value.isEmpty() ? null : value;
    }

    private static LocalDate optionalDate(
            JSONObject body,
            String field) {

        String value = optionalText(body, field);

        return value == null
                ? null
                : LocalDate.parse(value);
    }

    private static void writeError(
            WebScriptResponse response,
            int status,
            String code,
            String message)
            throws IOException {

        JSONObject result = new JSONObject();

        result.put("code", code);
        result.put("message", message);

        response.setHeader(
                "Cache-Control",
                "no-store");

        writeJson(response, status, result);
    }

    private static void writeJson(
            WebScriptResponse response,
            int status,
            JSONObject result)
            throws IOException {

        response.setStatus(status);

        response.setContentType(
                "application/json;charset=UTF-8");

        response.getWriter().write(result.toString());
    }
}