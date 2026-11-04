package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.organization.TarasolSecretaryRoundContinuationApprovalService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** A manager approves or returns the secretary's current cycle-bound follow-up request. */
public final class TarasolSecretaryRoundContinuationApprovalWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final TarasolSecretaryRoundContinuationApprovalService approvals;
    private final boolean writing;

    public TarasolSecretaryRoundContinuationApprovalWebScript(CurrentEmployeeService employees,
            TarasolSecretaryRoundContinuationApprovalService approvals, boolean writing) {
        this.employees = Objects.requireNonNull(employees);
        this.approvals = Objects.requireNonNull(approvals);
        this.writing = writing;
    }

    @Override public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            String technicalId = request.getServiceMatch().getTemplateVars().get("technicalId");
            if (technicalId == null || !technicalId.matches("[0-9a-fA-F-]{36}"))
                throw new IllegalArgumentException("Invalid technical ID");
            JSONObject body = writing ? parseBody(request) : null;
            String code = writing ? required(body, "unitCode") : request.getParameter("unitCode");
            if (code == null || !code.matches("[0-9]{3}"))
                throw new IllegalArgumentException("Invalid manager unit");
            DepartmentCode unit = new DepartmentCode(code);
            var manager = employees.getCurrentEmployee();
            JSONObject output = new JSONObject();
            if (!writing) {
                var pending = approvals.pending(manager, unit, technicalId);
                if (pending == null) {
                    send(response, Status.STATUS_NOT_FOUND,
                        new JSONObject().put("code", "NO_PENDING_CONTINUATION"));
                    return;
                }
                output.put("requestId", pending.requestId())
                    .put("expectedCycle", pending.expectedCycle())
                    .put("ownerEmployeeId", pending.ownerEmployeeId())
                    .put("reason", pending.reason())
                    .put("status", pending.status());
            } else {
                String action = required(body, "action");
                String requestId = required(body, "requestId");
                if (!requestId.matches("[0-9a-fA-F-]{36}"))
                    throw new IllegalArgumentException("Invalid request ID");
                var decision = switch (action) {
                    case "APPROVE_AND_CONTINUE" -> {
                        if (body.length() != 4)
                            throw new IllegalArgumentException("Unexpected approval fields");
                        Object revisionValue = body.opt("documentsRevision");
                        if (!(revisionValue instanceof Number)
                                || !revisionValue.toString().matches("0|[1-9][0-9]*"))
                            throw new IllegalArgumentException("Invalid documents revision");
                        long revision;
                        try { revision = Long.parseLong(revisionValue.toString()); }
                        catch (NumberFormatException invalid) {
                            throw new IllegalArgumentException("Invalid documents revision", invalid);
                        }
                        yield approvals.approve(manager, unit, technicalId, requestId, revision);
                    }
                    case "RETURN" -> {
                        if (body.length() != 4)
                            throw new IllegalArgumentException("Unexpected return fields");
                        yield approvals.returnForRevision(manager, unit, technicalId,
                            requestId, required(body, "reason"));
                    }
                    default -> throw new IllegalArgumentException("Unknown decision");
                };
                output.put("requestId", decision.requestId())
                    .put("status", decision.status())
                    .put("currentWorkflowCycle", decision.workflowCycle());
            }
            send(response, Status.STATUS_OK, output);
        } catch (CorrespondenceAuthorizationException denied) {
            send(response, Status.STATUS_FORBIDDEN,
                new JSONObject().put("code", "ACCESS_DENIED"));
        } catch (OfficeCycleConflictException conflict) {
            send(response, 409, new JSONObject().put("code", "CYCLE_CONFLICT"));
        } catch (IllegalArgumentException | org.json.JSONException bad) {
            send(response, Status.STATUS_BAD_REQUEST,
                new JSONObject().put("code", "INVALID_REQUEST"));
        }
        // The REQUIRED Alfresco transaction must roll back on unexpected failures.
    }

    private static JSONObject parseBody(WebScriptRequest request) throws IOException {
        if (request.getContent() == null || request.getContent().getContent() == null)
            throw new IllegalArgumentException("JSON body required");
        return new JSONObject(request.getContent().getContent());
    }
    private static String required(JSONObject json, String name) {
        if (!json.has(name) || !(json.get(name) instanceof String value) || value.isBlank())
            throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
    private static void send(WebScriptResponse response, int status, JSONObject output)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(output.toString());
    }
}
