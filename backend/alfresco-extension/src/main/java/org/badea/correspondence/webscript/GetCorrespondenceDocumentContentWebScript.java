 package org.badea.correspondence.webscript;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.badea.correspondence.application.CorrespondenceDocumentContentService;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.application.CorrespondenceDocumentStream;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;

import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;

import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONObject;

import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class GetCorrespondenceDocumentContentWebScript
        extends AbstractWebScript {

    private final CurrentEmployeeService currentEmployeeService;

    private final CorrespondenceActorRoleResolver actorRoleResolver;

    private final CorrespondenceDocumentContentService contentService;

    public GetCorrespondenceDocumentContentWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver actorRoleResolver,
            CorrespondenceDocumentContentService contentService) {

        this.currentEmployeeService =
                Objects.requireNonNull(currentEmployeeService);

        this.actorRoleResolver =
                Objects.requireNonNull(actorRoleResolver);

        this.contentService =
                Objects.requireNonNull(contentService);
    }

    @Override
    public void execute(
            WebScriptRequest request,
            WebScriptResponse response)
            throws IOException {

        CorrespondenceDocumentStream document;

        /*
         * Complete all authorization and repository checks
         * before writing the binary response.
         */
        try {
            String technicalId =
                    readPathVariable(request, "technicalId");

            String nodeId =
                    readPathVariable(request, "nodeId");

            CorrespondenceActorRole actorRole =
                    actorRoleResolver.resolveCurrentUserRole();

            EmployeeDirectoryEntry employee =
                    currentEmployeeService.getCurrentEmployee();

            DepartmentCode departmentCode =
                    employee.department()
                            .map(department -> department.code())
                            .orElse(null);

            document = contentService.open(
                    technicalId,
                    nodeId,
                    actorRole,
                    departmentCode,
                    employee.employeeId().value());

        } catch (CurrentEmployeeNotFoundException exception) {

            writeError(
                    response,
                    Status.STATUS_NOT_FOUND,
                    "EMPLOYEE_NOT_FOUND",
                    "No active employee profile was found.");

            return;

        } catch (EmployeeDirectoryException exception) {

            writeError(
                    response,
                    Status.STATUS_SERVICE_UNAVAILABLE,
                    "EMPLOYEE_DIRECTORY_UNAVAILABLE",
                    "The employee directory is unavailable.");

            return;

        } catch (CorrespondenceAuthorizationException exception) {

            writeError(
                    response,
                    Status.STATUS_FORBIDDEN,
                    "CORRESPONDENCE_FORBIDDEN",
                    "Access to this document is denied.");

            return;

        } catch (CorrespondenceDocumentNotFoundException exception) {

            writeError(
                    response,
                    Status.STATUS_NOT_FOUND,
                    "DOCUMENT_NOT_FOUND",
                    "The requested document was not found.");

            return;

        } catch (CorrespondenceRepositoryException exception) {

            writeError(
                    response,
                    Status.STATUS_INTERNAL_SERVER_ERROR,
                    "DOCUMENT_REPOSITORY_ERROR",
                    "The document could not be retrieved.");

            return;

        } catch (IllegalArgumentException exception) {

            writeError(
                    response,
                    Status.STATUS_BAD_REQUEST,
                    "INVALID_REQUEST",
                    "Invalid document request.");

            return;
        }

        /*
         * From this point onward the response is binary.
         * Do not attempt to write a JSON error after streaming begins.
         */
        response.setStatus(Status.STATUS_OK);

        response.setContentType(document.mimeType());

        response.setHeader(
                "Cache-Control",
                "private, no-store");

        response.setHeader(
                "X-Content-Type-Options",
                "nosniff");

        /*
         * Display PDFs inside the existing PDF viewer.
         * Other supported document types download as attachments.
         *
         * The fallback filename is constant to avoid unsafe
         * characters in the HTTP header. The UTF-8 filename*
         * parameter carries the actual name.
         */
        String disposition =
                "application/pdf".equalsIgnoreCase(document.mimeType())
                        ? "inline"
                        : "attachment";

        String encodedFilename =
                URLEncoder.encode(
                        document.filename(),
                        StandardCharsets.UTF_8)
                        .replace("+", "%20");

        response.setHeader(
                "Content-Disposition",
                disposition
                        + "; filename=\"document\""
                        + "; filename*=UTF-8''"
                        + encodedFilename);

        /*
         * Streaming uses a bounded buffer internally.
         * The InputStream is always closed.
         *
         * We intentionally do not claim HTTP Range support yet.
         */
        try (InputStream input = document.content()) {

            input.transferTo(
                    response.getOutputStream());
        }
    }

    private static String readPathVariable(
            WebScriptRequest request,
            String name) {

        String value =
                request.getServiceMatch()
                        .getTemplateVars()
                        .get(name);

        if (value == null || value.isBlank()) {

            throw new IllegalArgumentException(
                    "Missing required path variable: " + name);
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

        response.setStatus(status);

        response.setContentType(
                "application/json;charset=UTF-8");

        response.setHeader(
                "Cache-Control",
                "no-store");

        response.getWriter().write(
                result.toString());
    }
}