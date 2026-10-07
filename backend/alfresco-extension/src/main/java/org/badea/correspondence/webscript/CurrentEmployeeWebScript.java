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
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/**
 * Returns the BADEA employee profile associated with the currently
 * authenticated Alfresco user.
 */
public final class CurrentEmployeeWebScript
    extends AbstractWebScript {

    private static final Log LOGGER =
        LogFactory.getLog(CurrentEmployeeWebScript.class);

    private final CurrentEmployeeService currentEmployeeService;

    public CurrentEmployeeWebScript(
        CurrentEmployeeService currentEmployeeService
    ) {
        this.currentEmployeeService = Objects.requireNonNull(
            currentEmployeeService,
            "Current employee service must not be null."
        );
    }

    @Override
    public void execute(
        WebScriptRequest request,
        WebScriptResponse response
    ) throws IOException {

        try {
            EmployeeDirectoryEntry employee =
                currentEmployeeService.getCurrentEmployee();

            writeJson(
                response,
                Status.STATUS_OK,
                toJson(employee)
            );
        } catch (CurrentEmployeeNotFoundException exception) {
            writeError(
                response,
                Status.STATUS_NOT_FOUND,
                "EMPLOYEE_NOT_FOUND",
                "No active employee profile was found "
                    + "for the authenticated user."
            );
        } catch (EmployeeDirectoryException exception) {
            LOGGER.error(
                "Failed to resolve the authenticated employee "
                    + "from the BADEA employee directory.",
                exception
            );

            writeError(
                response,
                Status.STATUS_SERVICE_UNAVAILABLE,
                "EMPLOYEE_DIRECTORY_UNAVAILABLE",
                "The employee directory is currently unavailable."
            );
        }
    }

    private static JSONObject toJson(
        EmployeeDirectoryEntry employee
    ) throws IOException {

        try {
            JSONObject json = new JSONObject();

            json.put(
                "employeeId",
                employee.employeeId().value()
            );

            json.put(
                "username",
                employee.username().value()
            );

            json.put(
                "nameEnglish",
                employee.nameEnglish()
            );

            json.put(
                "nameArabic",
                nullableJsonValue(employee.nameArabic())
            );

            if (employee.department().isPresent()) {
                json.put(
                    "department",
                    toJson(employee.department().orElseThrow())
                );
            } else {
                json.put(
                    "department",
                    JSONObject.NULL
                );
            }

            return json;
        } catch (JSONException exception) {
            throw new WebScriptException(
                "Unable to serialize current employee response.",
                exception
            );
        }
    }

    private static JSONObject toJson(
        DepartmentDirectoryEntry department
    ) throws JSONException {

        JSONObject json = new JSONObject();

        json.put(
            "code",
            department.code().value()
        );

        json.put(
            "nameEnglish",
            department.nameEnglish()
        );

        json.put(
            "nameArabic",
            nullableJsonValue(department.nameArabic())
        );

        return json;
    }

    private static Object nullableJsonValue(String value) {
        return value == null
            ? JSONObject.NULL
            : value;
    }

    private static void writeError(
        WebScriptResponse response,
        int status,
        String code,
        String message
    ) throws IOException {

        try {
            JSONObject error = new JSONObject();

            error.put("code", code);
            error.put("message", message);

            writeJson(
                response,
                status,
                error
            );
        } catch (JSONException exception) {
            throw new WebScriptException(
                "Unable to serialize error response.",
                exception
            );
        }
    }

    private static void writeJson(
        WebScriptResponse response,
        int status,
        JSONObject json
    ) throws IOException {

        response.setStatus(status);
        response.setContentType(
            "application/json;charset=UTF-8"
        );

        response.getWriter().write(
            json.toString()
        );
    }
}