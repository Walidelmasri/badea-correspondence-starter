package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;

import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.organization.TarasolSecretaryWorkflowService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/**
 * Secretary-only and manager-only operations share a server-side authorization
 * service, never a caller-provided actor ID/role. Storage errors deliberately
 * escape REQUIRED transactions to prevent partially committed workflow state.
 */
public final class TarasolSecretaryWorkflowWebScript extends AbstractWebScript {
    private final CurrentEmployeeService currentEmployee;
    private final TarasolSecretaryWorkflowService workflow;
    private final boolean writing;

    public TarasolSecretaryWorkflowWebScript(CurrentEmployeeService currentEmployee,
            TarasolSecretaryWorkflowService workflow, boolean writing) {
        this.currentEmployee = Objects.requireNonNull(currentEmployee);
        this.workflow = Objects.requireNonNull(workflow);
        this.writing = writing;
    }

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            EmployeeDirectoryEntry employee = currentEmployee.getCurrentEmployee();
            JSONObject input = writing ? readBody(request) : null;
            String unitText = writing ? required(input, "unitCode") : request.getParameter("unitCode");
            if (unitText == null || !unitText.matches("[0-9]{3}")) {
                throw new IllegalArgumentException("Valid unitCode required");
            }
            DepartmentCode unit = new DepartmentCode(unitText);
            JSONObject result = new JSONObject();
            if (!writing) {
                String view = request.getParameter("view");
                if (!"mine".equals(view) && !"pending".equals(view)) {
                    throw new IllegalArgumentException("Unknown secretary workflow view");
                }
                var entries = "mine".equals(view)
                    ? workflow.mine(employee, unit) : workflow.pending(employee, unit);
                JSONArray items = new JSONArray();
                for (var item : entries) {
                    items.put(new JSONObject()
                        .put("technicalId", item.technicalId())
                        .put("unitCode", item.unitCode())
                        .put("subject", item.subject())
                        .put("bodyText", item.bodyText() == null ? JSONObject.NULL : item.bodyText())
                        .put("ownerEmployeeId", item.ownerEmployeeId())
                        .put("approvalStatus", item.approvalStatus()));
                }
                result.put("items", items);
            } else {
                String action = required(input, "action");
                switch (action) {
                    case "CREATE_DRAFT" -> {
                        String id = workflow.create(employee, unit, required(input,"subject"),
                            optional(input,"bodyText"));
                        result.put("technicalId", id).put("approvalStatus", "DRAFT");
                    }
                    case "REVISE" -> {
                        workflow.revise(employee, unit, required(input,"technicalId"),
                            required(input,"subject"), optional(input,"bodyText"));
                        result.put("approvalStatus", "DRAFT");
                    }
                    case "SUBMIT" -> {
                        workflow.submit(employee, unit, required(input,"technicalId"));
                        result.put("approvalStatus", "PENDING");
                    }
                    case "RETURN" -> {
                        workflow.returnForRevision(employee, unit, required(input,"technicalId"),
                            required(input,"explanation"));
                        result.put("approvalStatus", "RETURNED");
                    }
                    case "APPROVE_AND_SEND" -> {
                        var state = workflow.approveAndSend(employee, unit,
                            required(input,"technicalId"));
                        result.put("approvalStatus", "APPROVED").put("state", state.name());
                    }
                    default -> throw new IllegalArgumentException("Unknown approval action");
                }
                result.put("ok", true);
            }
            send(response, Status.STATUS_OK, result);
        } catch (CorrespondenceAuthorizationException denied) {
            send(response, Status.STATUS_FORBIDDEN, new JSONObject()
                .put("code", "ACCESS_DENIED").put("message", "Not authorized for this unit or action"));
        } catch (IllegalArgumentException | JSONException malformed) {
            send(response, Status.STATUS_BAD_REQUEST, new JSONObject()
                .put("code", "INVALID_APPROVAL_ACTION").put("message", malformed.getMessage()));
        }
        // Never catch repository/Oracle runtime failures here: an Alfresco
        // REQUIRED transaction must roll back all content and history writes.
    }

    private static JSONObject readBody(WebScriptRequest request) throws IOException {
        Content content = request.getContent();
        if (content == null || content.getContent() == null || content.getContent().isBlank()) {
            throw new IllegalArgumentException("JSON body required");
        }
        return new JSONObject(content.getContent());
    }

    private static String required(JSONObject body, String key) {
        String result = optional(body, key);
        if (result == null) throw new IllegalArgumentException(key + " is required");
        return result;
    }

    private static String optional(JSONObject body, String key) {
        if (!body.has(key) || body.isNull(key)) return null;
        Object raw = body.get(key);
        if (!(raw instanceof String text)) throw new IllegalArgumentException("Invalid " + key);
        String result = text.trim();
        return result.isEmpty() ? null : result;
    }

    private static void send(WebScriptResponse response, int status, JSONObject result) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(result.toString());
    }
}
