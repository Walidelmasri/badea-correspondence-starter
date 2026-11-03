package org.badea.correspondence.webscript;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.organization.TarasolDelegatedCaseService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Separate scoped delegated-case entry point. NEVER accepts an actor ID or role. */
public final class TarasolDelegatedCaseWebScript extends AbstractWebScript {
    private final CurrentEmployeeService current;
    private final TarasolDelegatedCaseService delegated;
    private final boolean writing;

    public TarasolDelegatedCaseWebScript(CurrentEmployeeService current,
            TarasolDelegatedCaseService delegated, boolean writing) {
        this.current = Objects.requireNonNull(current);
        this.delegated = Objects.requireNonNull(delegated);
        this.writing = writing;
    }

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            var vars = request.getServiceMatch().getTemplateVars();
            var technicalId = vars.get("technicalId");
            var documentNodeId = vars.get("nodeId");
            JSONObject input = null;
            if (writing) {
                Content body = request.getContent();
                if (body == null || body.getContent() == null || body.getContent().isBlank())
                    throw new IllegalArgumentException("JSON body required");
                input = new JSONObject(body.getContent());
            }
            String unitText = writing ? input.optString("unitCode", "") : request.getParameter("unitCode");
            if (unitText == null || !unitText.matches("[0-9]{3}"))
                throw new IllegalArgumentException("Valid unitCode required");
            DepartmentCode unit = new DepartmentCode(unitText);
            var employee = current.getCurrentEmployee();
            if (writing) {
                if (documentNodeId != null) throw new IllegalArgumentException("Not a transition URL");
                String requested = input.optString("action", "");
                CorrespondenceAction action = switch (requested) {
                    case "SEND_TO_OFFICE" -> CorrespondenceAction.SEND_TO_OFFICE;
                    case "MARK_DONE" -> CorrespondenceAction.MARK_DONE;
                    default -> throw new IllegalArgumentException("Unsupported delegated action");
                };
                var state = delegated.act(employee, unit, technicalId, action);
                json(response, Status.STATUS_OK, new JSONObject()
                    .put("ok", true).put("state", state.name()));
            } else if (technicalId == null) {
                var results = new JSONArray();
                for (var item : delegated.caseHandoffs(employee, unit)) {
                    results.put(new JSONObject()
                        .put("technicalId", item.technicalId())
                        .put("subject", item.subject())
                        .put("state", item.state()));
                }
                json(response, Status.STATUS_OK, new JSONObject().put("items", results));
            } else if (documentNodeId != null) {
                var preview = delegated.openPdf(employee, unit, technicalId, documentNodeId);
                String filename = URLEncoder.encode(preview.document().filename(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
                response.setStatus(Status.STATUS_OK);
                response.setContentType("application/pdf");
                response.setHeader("Content-Disposition", "inline; filename=\"document.pdf\"; filename*=UTF-8''" + filename);
                try (var stream = preview.stream()) {
                    stream.transferTo(response.getOutputStream());
                }
            } else {
                var details = delegated.read(employee, unit, technicalId);
                var docs = new JSONArray();
                for (var document : details.documents()) {
                    if (!"application/pdf".equalsIgnoreCase(document.mimeType())) continue;
                    docs.put(new JSONObject().put("nodeId", document.nodeId())
                        .put("filename", document.filename())
                        .put("mimeType", document.mimeType())
                        .put("sizeBytes", document.sizeBytes()));
                }
                json(response, Status.STATUS_OK, new JSONObject()
                    .put("technicalId", details.technicalId())
                    .put("unitCode", unitText)
                    .put("state", details.state().name())
                    .put("subject", details.subject())
                    .put("documentNumber", details.documentNumber() == null
                        ? JSONObject.NULL : details.documentNumber())
                    .put("bodyText", details.bodyText() == null ? JSONObject.NULL : details.bodyText())
                    .put("documents", docs));
            }
        } catch (CorrespondenceAuthorizationException denied) {
            error(response, Status.STATUS_FORBIDDEN, "DELEGATION_DENIED");
        } catch (CorrespondenceDocumentNotFoundException missing) {
            error(response, Status.STATUS_NOT_FOUND, "DOCUMENT_NOT_FOUND");
        } catch (IllegalArgumentException malformed) {
            error(response, Status.STATUS_BAD_REQUEST, "INVALID_DELEGATED_CASE_REQUEST");
        }
        // Repository and Oracle failures escape REQUIRED transaction and cannot
        // be falsely represented as successful delegated actions.
    }

    private static void error(WebScriptResponse response, int status, String code) throws IOException {
        json(response, status, new JSONObject().put("code", code));
    }

    private static void json(WebScriptResponse response, int status, JSONObject body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body.toString());
    }
}
