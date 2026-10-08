package org.badea.correspondence.webscript;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.badea.correspondence.application.CreateCorrespondenceCommand;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceRepository;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class CreateCorrespondenceWebScript
        extends AbstractWebScript {

    private final CurrentEmployeeService currentEmployeeService;
    private final AlfrescoCorrespondenceRepository correspondenceRepository;

    public CreateCorrespondenceWebScript(
            CurrentEmployeeService currentEmployeeService,
            AlfrescoCorrespondenceRepository correspondenceRepository) {

        this.currentEmployeeService = currentEmployeeService;
        this.correspondenceRepository = correspondenceRepository;
    }

    @Override
    public void execute(
            WebScriptRequest request,
            WebScriptResponse response) throws IOException {

        try {
            JSONObject body = readRequestBody(request);

            EmployeeDirectoryEntry employee =
                    currentEmployeeService.getCurrentEmployee();

            DepartmentDirectoryEntry department =
                    employee.department().orElse(null);

            if (department == null) {
                writeError(
                        response,
                        Status.STATUS_CONFLICT,
                        "EMPLOYEE_DEPARTMENT_REQUIRED",
                        "The authenticated employee does not have an active department assignment.");
                return;
            }

            CreateCorrespondenceCommand command =
                    new CreateCorrespondenceCommand(
                            department.code(),
                            requiredText(body, "subject"),
                            optionalText(body, "bodyText"),
                            optionalText(body, "documentNumber"),
                            optionalDate(body, "documentDate"),
                            optionalText(body, "sourceText"),
                            employee.employeeId(),
                            employee.nameEnglish(),
                            employee.nameArabic());

            String technicalId =
                    correspondenceRepository.createDraft(command);

            JSONObject result = new JSONObject();
            result.put("technicalId", technicalId);
            result.put(
                    "state",
                    CorrespondenceState.DRAFT.name());

            writeJson(
                    response,
                    Status.STATUS_CREATED,
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

        } catch (JSONException
                | DateTimeParseException
                | IllegalArgumentException exception) {

            writeError(
                    response,
                    Status.STATUS_BAD_REQUEST,
                    "INVALID_REQUEST",
                    exception.getMessage());

        } catch (CorrespondenceRepositoryException exception) {

            writeError(
                    response,
                    Status.STATUS_INTERNAL_SERVER_ERROR,
                    "CORRESPONDENCE_STORAGE_ERROR",
                    exception.getMessage());
        }
    }

    private static JSONObject readRequestBody(
            WebScriptRequest request) throws IOException {

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

        return new JSONObject(raw);
    }

    private static String requiredText(
            JSONObject body,
            String propertyName) {

        if (!body.has(propertyName)
                || body.isNull(propertyName)) {

            throw new IllegalArgumentException(
                    propertyName + " is required.");
        }

        String value =
                body.getString(propertyName).trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    propertyName + " must not be blank.");
        }

        return value;
    }

    private static String optionalText(
            JSONObject body,
            String propertyName) {

        if (!body.has(propertyName)
                || body.isNull(propertyName)) {

            return null;
        }

        String value =
                body.getString(propertyName).trim();

        return value.isEmpty()
                ? null
                : value;
    }

    private static LocalDate optionalDate(
            JSONObject body,
            String propertyName) {

        String value =
                optionalText(
                        body,
                        propertyName);

        return value == null
                ? null
                : LocalDate.parse(value);
    }

    private static void writeError(
            WebScriptResponse response,
            int status,
            String code,
            String message) throws IOException {

        JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("message", message);

        writeJson(
                response,
                status,
                json);
    }

    private static void writeJson(
            WebScriptResponse response,
            int status,
            JSONObject json) throws IOException {

        response.setStatus(status);
        response.setContentType(
                "application/json;charset=UTF-8");

        response.getWriter()
                .write(json.toString());
    }
}