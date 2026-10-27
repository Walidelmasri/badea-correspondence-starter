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
 * Valid Alfresco credentials without exactly one correspondence
 * workflow group are denied with HTTP 403.
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
            // Check Alfresco workflow group membership before returning any
            // application profile. Valid AD credentials alone are insufficient.
            CorrespondenceActorRole role =
                    roleResolver.resolveCurrentUserRole();

            EmployeeDirectoryEntry employee =
                    currentEmployeeService.getCurrentEmployee();

            response.setHeader("Cache-Control", "no-store");

            writeJson(
                    response,
                    Status.STATUS_OK,
                    toJson(employee, role));

        } catch (CorrespondenceAuthorizationException exception) {

            writeError(
                    response,
                    Status.STATUS_FORBIDDEN,
                    "CORRESPONDENCE_FORBIDDEN",
                    "You do not have access to Tarasol.");

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