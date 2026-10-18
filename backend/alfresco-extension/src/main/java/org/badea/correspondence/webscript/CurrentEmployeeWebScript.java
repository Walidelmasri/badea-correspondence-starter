package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONException;
import org.json.JSONObject;

import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/**
 * Returns the authenticated employee and their resolved
 * correspondence workflow role.
 *
 * Users without a valid correspondence role can still
 * authenticate, but correspondence operations remain blocked.
 */
public final class CurrentEmployeeWebScript
        extends AbstractWebScript {

    private static final Log LOGGER =
            LogFactory.getLog(CurrentEmployeeWebScript.class);

    private final CurrentEmployeeService currentEmployeeService;

    private final CorrespondenceActorRoleResolver roleResolver;

    public CurrentEmployeeWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver roleResolver) {

        this.currentEmployeeService =
                Objects.requireNonNull(currentEmployeeService);

        this.roleResolver =
                Objects.requireNonNull(roleResolver);
    }

    @Override
    public void execute(
            WebScriptRequest request,
            WebScriptResponse response)
            throws IOException {

        try {
            EmployeeDirectoryEntry employee =
                    currentEmployeeService.getCurrentEmployee();

            CorrespondenceActorRole role = null;

            try {
                role = roleResolver.resolveCurrentUserRole();

            } catch (CorrespondenceAuthorizationException exception) {
                /*
                 * A missing or ambiguous workflow role must not
                 * prevent an otherwise valid Alfresco login.
                 *
                 * Returning null makes the UI fail closed.
                 * All write/read endpoints still authorize
                 * roles independently.
                 */
                LOGGER.warn(
                        "No unambiguous correspondence role "
                                + "for the authenticated user.");
            }

            response.setHeader("Cache-Control", "no-store");

            writeJson(
                    response,
                    Status.STATUS_OK,
                    toJson(employee, role));

        } catch (CurrentEmployeeNotFoundException exception) {

            writeError(
                    response,
                    Status.STATUS_NOT_FOUND,
                    "EMPLOYEE_NOT_FOUND",
                    "No active employee profile was found "
                            + "for the authenticated user.");

        } catch (EmployeeDirectoryException exception) {

            LOGGER.error(
                    "Failed to resolve the authenticated employee "
                            + "from the BADEA employee directory.",
                    exception);

            writeError(
                    response,
                    Status.STATUS_SERVICE_UNAVAILABLE,
                    "EMPLOYEE_DIRECTORY_UNAVAILABLE",
                    "The employee directory is currently unavailable.");
        }
    }

    private static JSONObject toJson(
            EmployeeDirectoryEntry employee,
            CorrespondenceActorRole role) {

        try {
            JSONObject json = new JSONObject();

            json.put(
                    "employeeId",
                    employee.employeeId().value());

            json.put(
                    "username",
                    employee.username().value());

            json.put(
                    "nameEnglish",
                    employee.nameEnglish());

            json.put(
                    "nameArabic",
                    nullableJsonValue(employee.nameArabic()));

            json.put(
                    "correspondenceRole",
                    role == null
                            ? JSONObject.NULL
                            : role.name());

            json.put(
                    "department",
                    employee.department()
                            .<Object>map(
                                    CurrentEmployeeWebScript::toJson)
                            .orElse(JSONObject.NULL));

            return json;

        } catch (JSONException exception) {
            throw new WebScriptException(
                    "Unable to serialize current employee response.",
                    exception);
        }
    }

    private static JSONObject toJson(
            DepartmentDirectoryEntry department) {

        JSONObject json = new JSONObject();

        json.put("code", department.code().value());
        json.put("nameEnglish", department.nameEnglish());

        json.put(
                "nameArabic",
                nullableJsonValue(department.nameArabic()));

        return json;
    }

    private static Object nullableJsonValue(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static void writeError(
            WebScriptResponse response,
            int status,
            String code,
            String message)
            throws IOException {

        JSONObject error = new JSONObject();

        error.put("code", code);
        error.put("message", message);

        writeJson(response, status, error);
    }

    private static void writeJson(
            WebScriptResponse response,
            int status,
            JSONObject json)
            throws IOException {

        response.setStatus(status);
        response.setContentType(
                "application/json;charset=UTF-8");

        response.getWriter().write(json.toString());
    }
}