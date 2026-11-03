package org.badea.correspondence.webscript;

import java.io.IOException;
import java.io.InputStream;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import java.util.Objects;

import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceDraftDocumentUploader;
import org.badea.correspondence.organization.TarasolSecretaryWorkflowService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** PDF-only, owner-bound draft attachment endpoint. No Director impersonation. */
public final class TarasolSecretaryUploadWebScript extends AbstractWebScript {
    private final CurrentEmployeeService currentEmployee;
    private final TarasolSecretaryWorkflowService workflow;
    private final AlfrescoCorrespondenceDraftDocumentUploader uploader;

    public TarasolSecretaryUploadWebScript(CurrentEmployeeService currentEmployee,
            TarasolSecretaryWorkflowService workflow,
            AlfrescoCorrespondenceDraftDocumentUploader uploader) {
        this.currentEmployee = Objects.requireNonNull(currentEmployee);
        this.workflow = Objects.requireNonNull(workflow);
        this.uploader = Objects.requireNonNull(uploader);
    }

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            var employee = currentEmployee.getCurrentEmployee();
            String unitText = request.getParameter("unitCode");
            if (unitText == null || !unitText.matches("[0-9]{3}")) {
                throw new IllegalArgumentException("Invalid unitCode");
            }
            DepartmentCode unit = new DepartmentCode(unitText);
            String technicalId = request.getServiceMatch().getTemplateVars().get("technicalId");
            if (technicalId == null || !technicalId.matches(
                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                throw new IllegalArgumentException("Invalid correspondence ID");
            }
            String filename = request.getParameter("filename");
            if (filename == null || filename.isBlank()) throw new IllegalArgumentException("Filename required");
            String contentType = request.getContentType();
            if (contentType == null || !"application/pdf".equalsIgnoreCase(contentType.split(";",2)[0].trim())) {
                throw new IllegalArgumentException("Only PDF attachments are supported");
            }
            // Authorize before accepting or buffering potentially large PDF content.
            workflow.authorizePdfUpload(employee, unit, technicalId);
            Content content = request.getContent();
            if (content == null) throw new IllegalArgumentException("PDF required");
            byte[] bytes;
            try (InputStream input = content.getInputStream()) {
                if (input == null) throw new IllegalArgumentException("PDF required");
                bytes = input.readNBytes(AlfrescoCorrespondenceDraftDocumentUploader.MAX_PDF_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > AlfrescoCorrespondenceDraftDocumentUploader.MAX_PDF_BYTES) {
                throw new IllegalArgumentException("PDF size must be between 1 byte and 25 MiB");
            }
            // Active Oracle secretary grant + persisted owner/status have already
            // been validated. Use a narrowly scoped repository write so native
            // originating-folder ACLs cannot reject the authorized secretary.
            var result = AuthenticationUtil.runAsSystem(() ->
                uploader.uploadPdf(technicalId, CorrespondenceActorRole.SECRETARY, unit,
                    employee.employeeId(), filename, bytes));
            send(response, Status.STATUS_CREATED, new JSONObject().put("nodeId", result.nodeId())
                .put("filename", result.filename()).put("sizeBytes", result.sizeBytes()));
        } catch (CorrespondenceAuthorizationException denied) {
            send(response, Status.STATUS_FORBIDDEN, new JSONObject()
                .put("code","ACCESS_DENIED").put("message","Cannot upload to this draft"));
        } catch (IllegalArgumentException invalid) {
            send(response, Status.STATUS_BAD_REQUEST, new JSONObject()
                .put("code","INVALID_PDF").put("message",invalid.getMessage()));
        }
        // Storage errors escape REQUIRED transaction, forcing rollback.
    }

    private static void send(WebScriptResponse response, int status, JSONObject result) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(result.toString());
    }
}
