package org.badea.correspondence.webscript;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.organization.TarasolSecretaryDocumentService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Authenticated owner/manager preview, strictly within one secretary draft. */
public final class TarasolSecretaryDocumentWebScript extends AbstractWebScript {
    private final CurrentEmployeeService currentEmployee;
    private final TarasolSecretaryDocumentService service;

    public TarasolSecretaryDocumentWebScript(CurrentEmployeeService currentEmployee,
            TarasolSecretaryDocumentService service) {
        this.currentEmployee = Objects.requireNonNull(currentEmployee);
        this.service = Objects.requireNonNull(service);
    }

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        try {
            String unitCode = request.getParameter("unitCode");
            if (unitCode == null || !unitCode.matches("[0-9]{3}"))
                throw new IllegalArgumentException("Valid unitCode required");
            String view = request.getParameter("view");
            if (!"mine".equals(view) && !"pending".equals(view))
                throw new IllegalArgumentException("Valid view required");
            var vars = request.getServiceMatch().getTemplateVars();
            var actor = currentEmployee.getCurrentEmployee();
            var unit = new DepartmentCode(unitCode);
            String technicalId = vars.get("technicalId");
            String nodeId = vars.get("nodeId");
            if (nodeId == null) {
                JSONArray items = new JSONArray();
                for (var doc : service.list(actor, unit, technicalId, "pending".equals(view))) {
                    items.put(new JSONObject()
                        .put("nodeId", doc.nodeId())
                        .put("filename", doc.filename())
                        .put("sizeBytes", doc.sizeBytes())
                        .put("mimeType", doc.mimeType()));
                }
                response.setStatus(Status.STATUS_OK);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(new JSONObject().put("items", items).toString());
            } else {
                var preview = service.open(actor, unit, technicalId, nodeId, "pending".equals(view));
                var filename = URLEncoder.encode(preview.document().filename(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
                response.setStatus(Status.STATUS_OK);
                response.setContentType("application/pdf");
                response.setHeader("Content-Disposition", "inline; filename=\"document.pdf\"; filename*=UTF-8''" + filename);
                try (var stream = preview.stream()) {
                    stream.transferTo(response.getOutputStream());
                }
            }
        } catch (CorrespondenceAuthorizationException denied) {
            error(response, Status.STATUS_FORBIDDEN, "ACCESS_DENIED");
        } catch (CorrespondenceDocumentNotFoundException missing) {
            error(response, Status.STATUS_NOT_FOUND, "DOCUMENT_NOT_FOUND");
        } catch (IllegalArgumentException invalid) {
            error(response, Status.STATUS_BAD_REQUEST, "INVALID_REQUEST");
        }
    }

    private static void error(WebScriptResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(new JSONObject().put("code", code).toString());
    }
}
