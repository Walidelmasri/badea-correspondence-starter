package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.organization.TarasolSecretaryRoundTwoRequestService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Stage a secretary follow-up request; never starts a new round without manager approval. */
public final class TarasolSecretaryRoundTwoRequestWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final TarasolSecretaryRoundTwoRequestService requests;
    private final String mode;

    public TarasolSecretaryRoundTwoRequestWebScript(CurrentEmployeeService employees,
            TarasolSecretaryRoundTwoRequestService requests, boolean writing) {
        this(employees, requests, writing ? "SUBMIT" : "CURRENT");
    }

    public TarasolSecretaryRoundTwoRequestWebScript(CurrentEmployeeService employees,
            TarasolSecretaryRoundTwoRequestService requests, String mode) {
        this.employees = Objects.requireNonNull(employees);
        this.requests = Objects.requireNonNull(requests);
        if (!java.util.Set.of("CURRENT", "SUBMIT", "REVISE", "RESUBMIT").contains(mode))
            throw new IllegalArgumentException("Unknown secretary request mode");
        this.mode = mode;
    }

    @Override public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            String id = request.getServiceMatch().getTemplateVars().get("technicalId");
            String code = request.getParameter("unitCode");
            if (code == null || !code.matches("[0-9]{3}"))
                throw new IllegalArgumentException("Secretary unit required");
            DepartmentCode unit = new DepartmentCode(code);
            var employee = employees.getCurrentEmployee();
            var outcome = switch (mode) {
                case "CURRENT" -> requests.current(employee, unit, id);
                case "SUBMIT" -> requests.submit(employee, unit, id, reason(request));
                case "REVISE" -> {
                    JSONObject payload = body(request);
                    requireFields(payload, "requestId", "returnVersion", "reason");
                    yield requests.revise(employee, unit, id, requestId(payload),
                        version(payload), text(payload, "reason"));
                }
                case "RESUBMIT" -> {
                    JSONObject payload = body(request);
                    requireFields(payload, "requestId", "returnVersion");
                    yield requests.resubmit(employee, unit, id, requestId(payload),
                        version(payload));
                }
                default -> throw new IllegalStateException("Unsupported request mode");
            };
            if (outcome == null) { respond(response, Status.STATUS_NOT_FOUND,
                    new JSONObject().put("code", "NO_CURRENT_REQUEST")); return; }
            respond(response, Status.STATUS_OK, new JSONObject()
                .put("requestId", outcome.requestId())
                .put("technicalId", outcome.technicalId())
                .put("unitCode", outcome.unitCode())
                .put("status", outcome.status())
                .put("expectedCycle", outcome.expectedCycle())
                .put("returnVersion", outcome.returnVersion())
                .put("reason", outcome.reason())
                .put("returnReason", outcome.returnReason() == null
                    ? JSONObject.NULL : outcome.returnReason())
                .put("requestedAt", outcome.requestedAt().toString()));
        } catch (CorrespondenceAuthorizationException denied) {
            respond(response, Status.STATUS_FORBIDDEN, new JSONObject().put("code", "ACCESS_DENIED"));
        } catch (OfficeCycleConflictException conflict) {
            respond(response, 409, new JSONObject().put("code", "CYCLE_CONFLICT"));
        } catch (IllegalArgumentException invalid) {
            respond(response, Status.STATUS_BAD_REQUEST, new JSONObject().put("code", "INVALID_REQUEST"));
        }
        // Unexpected repository/Oracle errors escape the REQUIRED Alfresco transaction.
    }

    private static String reason(WebScriptRequest request) throws IOException {
        JSONObject json = body(request);
        requireFields(json, "reason");
        return text(json, "reason");
    }

    private static JSONObject body(WebScriptRequest request) throws IOException {
        if (request.getContent() == null || request.getContent().getContent() == null)
            throw new IllegalArgumentException("JSON body required");
        try { return new JSONObject(request.getContent().getContent()); }
        catch (JSONException malformed) { throw new IllegalArgumentException("Invalid JSON", malformed); }
    }

    private static void requireFields(JSONObject json, String... fields) {
        if (json.length() != fields.length
                || java.util.Arrays.stream(fields).anyMatch(field -> !json.has(field)))
            throw new IllegalArgumentException("Unexpected request body");
    }

    private static String text(JSONObject json, String field) {
        Object raw = json.opt(field);
        if (!(raw instanceof String value))
            throw new IllegalArgumentException("Invalid " + field);
        return value;
    }

    private static String requestId(JSONObject json) {
        String id = text(json, "requestId");
        try {
            if (java.util.UUID.fromString(id).toString().equals(id)) return id;
        } catch (IllegalArgumentException invalid) { /* rejected below */ }
        throw new IllegalArgumentException("Invalid request ID");
    }

    private static long version(JSONObject json) {
        Object raw = json.opt("returnVersion");
        if (!(raw instanceof Number) || !raw.toString().matches("0|[1-9][0-9]*"))
            throw new IllegalArgumentException("Invalid return version");
        try { return Long.parseLong(raw.toString()); }
        catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid return version", invalid);
        }
    }

    private static void respond(WebScriptResponse response, int status, JSONObject result) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(result.toString());
    }
}
