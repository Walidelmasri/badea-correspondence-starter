package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceDraftDocumentDeleter;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONObject;

import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class DeleteCorrespondenceDocumentWebScript
        extends AbstractWebScript {

    private static final int HTTP_NO_CONTENT = 204;

    private final CurrentEmployeeService currentEmployeeService;
    private final CorrespondenceActorRoleResolver actorRoleResolver;
    private final AlfrescoCorrespondenceDraftDocumentDeleter deleter;

    public DeleteCorrespondenceDocumentWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver actorRoleResolver,
            AlfrescoCorrespondenceDraftDocumentDeleter deleter) {

        this.currentEmployeeService =
                Objects.requireNonNull(currentEmployeeService);

        this.actorRoleResolver =
                Objects.requireNonNull(actorRoleResolver);

        this.deleter =
                Objects.requireNonNull(deleter);
    }

    @Override
    public void execute(
            WebScriptRequest request,
            WebScriptResponse response)
            throws IOException {

        try {
            CorrespondenceActorRole role =
                    actorRoleResolver.resolveCurrentUserRole();

            if (role != CorrespondenceActorRole.DIRECTOR) {
                throw new CorrespondenceAuthorizationException(
                        "Director role required.");
            }

            Map<String, String> parameters =
                    request.getServiceMatch().getTemplateVars();

            String technicalId =
                    requiredParameter(parameters, "technicalId");

            String nodeId =
                    requiredParameter(parameters, "nodeId");

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

            /*
             * The repository operation independently checks:
             *
             * - Director role
             * - DRAFT state
             * - Originating department
             * - Alfresco WRITE/DELETE permissions
             * - Direct source-document association
             */
            deleter.deleteDocument(
                    technicalId,
                    nodeId,
                    role,
                    departmentCode);

            response.setHeader(
                    "Cache-Control",
                    "no-store");

            response.setStatus(HTTP_NO_CONTENT);

        } catch (CorrespondenceAuthorizationException exception) {

            writeError(
                    response,
                    Status.STATUS_FORBIDDEN,
                    "CORRESPONDENCE_FORBIDDEN",
                    "You are not authorized to delete this document.");

        } catch (CorrespondenceDocumentNotFoundException exception) {

            writeError(
                    response,
                    Status.STATUS_NOT_FOUND,
                    "DOCUMENT_NOT_FOUND",
                    "The requested document was not found.");

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
                    "DOCUMENT_STORAGE_ERROR",
                    "The document could not be deleted.");

        } catch (IllegalArgumentException exception) {

            writeError(
                    response,
                    Status.STATUS_BAD_REQUEST,
                    "INVALID_REQUEST",
                    exception.getMessage());
        }
    }

    private static String requiredParameter(
            Map<String, String> parameters,
            String name) {

        if (parameters == null) {
            throw new IllegalArgumentException(
                    "Request path parameters are required.");
        }

        String value = parameters.get(name);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " is required.");
        }

        return value.trim();
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

        response.setStatus(status);

        response.setContentType(
                "application/json;charset=UTF-8");

        response.getWriter().write(result.toString());
    }
}