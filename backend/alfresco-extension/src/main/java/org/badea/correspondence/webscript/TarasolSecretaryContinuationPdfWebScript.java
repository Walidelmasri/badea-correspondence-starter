package org.badea.correspondence.webscript;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceDraftDocumentUploader;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoSecretaryContinuationPdfStore;
import org.badea.correspondence.organization.TarasolSecretaryContinuationPdfService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Explicit actor-specific endpoints: secretary may edit; manager may only preview. */
public final class TarasolSecretaryContinuationPdfWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final TarasolSecretaryContinuationPdfService service;
    private final String mode;

    public TarasolSecretaryContinuationPdfWebScript(CurrentEmployeeService employees,
            TarasolSecretaryContinuationPdfService service, String mode) {
        this.employees = Objects.requireNonNull(employees);
        this.service = Objects.requireNonNull(service);
        this.mode = Objects.requireNonNull(mode);
        if (!java.util.Set.of("SECRETARY_UPLOAD", "SECRETARY_LIST", "SECRETARY_OPEN",
                "SECRETARY_DELETE", "MANAGER_LIST", "MANAGER_OPEN").contains(mode))
            throw new IllegalArgumentException("Unsupported PDF endpoint mode");
    }

    @Override public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        try {
            var vars = request.getServiceMatch().getTemplateVars();
            String technicalId = vars.get("technicalId");
            String unitText = request.getParameter("unitCode");
            if (unitText == null || !unitText.matches("[0-9]{3}"))
                throw new IllegalArgumentException("unitCode required");
            DepartmentCode unit = new DepartmentCode(unitText);
            String requestId = mode.startsWith("MANAGER")
                ? request.getParameter("requestId") : vars.get("requestId");
            var actor = employees.getCurrentEmployee();
            switch (mode) {
                case "SECRETARY_UPLOAD" -> {
                    // Check active Oracle assignment/owner/request before buffering bytes.
                    service.authorizeSecretary(actor, unit, technicalId, requestId);
                    String name = request.getParameter("filename");
                    if (request.getContentType() == null || !"application/pdf".equalsIgnoreCase(
                            request.getContentType().split(";", 2)[0].trim()))
                        throw new IllegalArgumentException("Only PDFs are supported");
                    Content body = request.getContent();
                    if (body == null) throw new IllegalArgumentException("PDF body required");
                    byte[] bytes;
                    try (InputStream input = body.getInputStream()) {
                        if (input == null) throw new IllegalArgumentException("PDF body required");
                        bytes = input.readNBytes(AlfrescoCorrespondenceDraftDocumentUploader.MAX_PDF_BYTES + 1);
                    }
                    var stored = service.upload(actor, unit, technicalId, requestId, name, bytes);
                    json(response, Status.STATUS_CREATED, entry(stored));
                }
                case "SECRETARY_DELETE" -> {
                    service.delete(actor, unit, technicalId, requestId, vars.get("nodeId"));
                    response.setStatus(204);
                }
                case "SECRETARY_LIST" -> json(response, Status.STATUS_OK,
                    list(service.secretaryList(actor, unit, technicalId, requestId)));
                case "MANAGER_LIST" -> json(response, Status.STATUS_OK,
                    list(service.managerList(actor, unit, technicalId, requestId)));
                case "SECRETARY_OPEN" -> preview(response,
                    service.secretaryOpen(actor, unit, technicalId, requestId, vars.get("nodeId")));
                case "MANAGER_OPEN" -> preview(response,
                    service.managerOpen(actor, unit, technicalId, requestId, vars.get("nodeId")));
                default -> throw new IllegalStateException("Unknown PDF route");
            }
        } catch (CorrespondenceAuthorizationException denied) {
            error(response, Status.STATUS_FORBIDDEN, "ACCESS_DENIED");
        } catch (OfficeCycleConflictException stale) {
            error(response, 409, "CYCLE_CONFLICT");
        } catch (CorrespondenceDocumentNotFoundException missing) {
            error(response, Status.STATUS_NOT_FOUND, "DOCUMENT_NOT_FOUND");
        } catch (IllegalArgumentException bad) {
            error(response, Status.STATUS_BAD_REQUEST, "INVALID_REQUEST");
        }
        // Unanticipated storage errors must escape and roll back the REQUIRED transaction.
    }

    private static void preview(WebScriptResponse response,
            AlfrescoSecretaryContinuationPdfStore.Preview preview) throws IOException {
        response.setStatus(Status.STATUS_OK);
        response.setContentType("application/pdf");
        String filename = URLEncoder.encode(preview.pdf().filename(), StandardCharsets.UTF_8)
            .replace("+", "%20");
        response.setHeader("Content-Disposition",
            "inline; filename=\"document.pdf\"; filename*=UTF-8''" + filename);
        try (InputStream stream = preview.stream()) {
            stream.transferTo(response.getOutputStream());
        }
    }

    private static JSONObject entry(AlfrescoSecretaryContinuationPdfStore.Pdf pdf) {
        return new JSONObject().put("nodeId", pdf.nodeId()).put("filename", pdf.filename())
            .put("mimeType", "application/pdf").put("sizeBytes", pdf.sizeBytes())
            .put("workflowCycle", pdf.workflowCycle());
    }
    private static JSONObject list(List<AlfrescoSecretaryContinuationPdfStore.Pdf> pdfs) {
        JSONArray items = new JSONArray();
        pdfs.forEach(pdf -> items.put(entry(pdf)));
        return new JSONObject().put("items", items);
    }
    private static void json(WebScriptResponse response, int code, JSONObject body) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body.toString());
    }
    private static void error(WebScriptResponse response, int code, String message) throws IOException {
        json(response, code, new JSONObject().put("code", message));
    }
}
