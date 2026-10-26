package org.badea.correspondence.webscript;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.badea.correspondence.application.CorrespondenceDocument;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;

import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceDraftDocumentUploader;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;

import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONObject;

import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class UploadCorrespondenceDocumentWebScript
        extends AbstractWebScript {

    private static final int HTTP_PAYLOAD_TOO_LARGE = 413;
    private static final int HTTP_UNSUPPORTED_MEDIA_TYPE = 415;

    private static final String PDF_MIME_TYPE =
            "application/pdf";

    private final CurrentEmployeeService currentEmployeeService;

    private final CorrespondenceActorRoleResolver actorRoleResolver;

    private final AlfrescoCorrespondenceDraftDocumentUploader uploader;

    public UploadCorrespondenceDocumentWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver actorRoleResolver,
            AlfrescoCorrespondenceDraftDocumentUploader uploader) {

        this.currentEmployeeService =
                Objects.requireNonNull(
                        currentEmployeeService,
                        "currentEmployeeService");

        this.actorRoleResolver =
                Objects.requireNonNull(
                        actorRoleResolver,
                        "actorRoleResolver");

        this.uploader =
                Objects.requireNonNull(
                        uploader,
                        "uploader");
    }

    @Override
    public void execute(
            WebScriptRequest request,
            WebScriptResponse response)
            throws IOException {

        try {
            String technicalId =
                    readTechnicalId(request);

            /*
             * Resolve authorization context from Alfresco and Oracle.
             * No caller-supplied employee ID, department, or role.
             */
            CorrespondenceActorRole actorRole =
                    actorRoleResolver.resolveCurrentUserRole();

            if (actorRole != CorrespondenceActorRole.DIRECTOR
                    && actorRole != CorrespondenceActorRole.PRESIDENT
                    && actorRole != CorrespondenceActorRole.PRESIDENT_OFFICE) {
                throw new CorrespondenceAuthorizationException(
                        "A correspondence role is required to upload draft PDFs.");
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

            String filename =
                    request.getParameter("filename");

            if (filename == null || filename.isBlank()) {
                throw new IllegalArgumentException(
                        "A PDF filename is required.");
            }

            validateContentType(request.getContentType());

            /*
             * The HTTP request is limited before its bytes reach
             * the Alfresco repository writer.
             */
            byte[] pdfBytes = readPdfBytes(request);

            CorrespondenceDocument document =
                    uploader.uploadPdf(
                            technicalId,
                            actorRole,
                            departmentCode,
                            employee.employeeId(),
                            filename,
                            pdfBytes);

            JSONObject result = new JSONObject();

            result.put("nodeId", document.nodeId());
            result.put("filename", document.filename());
            result.put("mimeType", document.mimeType());
            result.put("sizeBytes", document.sizeBytes());
            result.put("workflowCycle", document.workflowCycle() == null
                    ? JSONObject.NULL : document.workflowCycle());

            response.setHeader(
                    "Cache-Control",
                    "no-store");

            writeJson(
                    response,
                    Status.STATUS_CREATED,
                    result);

        } catch (UnsupportedPdfMediaTypeException exception) {

            writeError(
                    response,
                    HTTP_UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_MEDIA_TYPE",
                    "Only application/pdf uploads are supported.");

        } catch (PdfTooLargeException exception) {

            writeError(
                    response,
                    HTTP_PAYLOAD_TOO_LARGE,
                    "DOCUMENT_TOO_LARGE",
                    "PDF uploads must not exceed 25 MiB.");

        } catch (CorrespondenceAuthorizationException exception) {

            writeError(
                    response,
                    Status.STATUS_FORBIDDEN,
                    "CORRESPONDENCE_FORBIDDEN",
                    "You are not authorized to upload to this draft.");

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
                    "The document could not be stored.");

        } catch (IllegalArgumentException exception) {

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
                    "Correspondence technical ID is required.");
        }

        return technicalId.trim();
    }

    private static void validateContentType(
            String contentType) {

        if (contentType == null) {
            throw new UnsupportedPdfMediaTypeException();
        }

        /*
         * Ignore optional MIME parameters.
         * Do not accept arbitrary application/* content.
         */
        String mimeType =
                contentType.split(";", 2)[0].trim();

        if (!PDF_MIME_TYPE.equalsIgnoreCase(mimeType)) {
            throw new UnsupportedPdfMediaTypeException();
        }
    }

    private static byte[] readPdfBytes(
            WebScriptRequest request)
            throws IOException {

        Content content = request.getContent();

        if (content == null) {
            throw new IllegalArgumentException(
                    "PDF content is required.");
        }

        /*
         * Read at most MAX_PDF_BYTES + 1.
         *
         * This also protects requests whose Content-Length
         * is missing or inaccurate.
         */
        try (InputStream stream = content.getInputStream()) {

            if (stream == null) {
                throw new IllegalArgumentException(
                        "PDF content is required.");
            }

            byte[] bytes =
                    stream.readNBytes(
                            AlfrescoCorrespondenceDraftDocumentUploader
                                    .MAX_PDF_BYTES + 1);

            if (bytes.length >
                    AlfrescoCorrespondenceDraftDocumentUploader
                            .MAX_PDF_BYTES) {

                throw new PdfTooLargeException();
            }

            if (bytes.length == 0) {
                throw new IllegalArgumentException(
                        "PDF content is empty.");
            }

            return bytes;
        }
    }

    private static void writeError(
            WebScriptResponse response,
            int status,
            String code,
            String message)
            throws IOException {

        JSONObject json = new JSONObject();

        json.put("code", code);
        json.put("message", message);

        response.setHeader(
                "Cache-Control",
                "no-store");

        writeJson(
                response,
                status,
                json);
    }

    private static void writeJson(
            WebScriptResponse response,
            int status,
            JSONObject json)
            throws IOException {

        response.setStatus(status);

        response.setContentType(
                "application/json;charset=UTF-8");

        response.getWriter().write(
                json.toString());
    }

    private static final class PdfTooLargeException
            extends RuntimeException {
    }

    private static final class UnsupportedPdfMediaTypeException
            extends RuntimeException {
    }
}