package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.organization.TarasolSecretaryRoundTwoRequestService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Stage a secretary follow-up request; never starts a new round without manager approval. */
public final class TarasolSecretaryRoundTwoRequestWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final TarasolSecretaryRoundTwoRequestService requests;
    private final boolean writing;

    public TarasolSecretaryRoundTwoRequestWebScript(CurrentEmployeeService employees,
            TarasolSecretaryRoundTwoRequestService requests, boolean writing) {
        this.employees = Objects.requireNonNull(employees);
        this.requests = Objects.requireNonNull(requests);
        this.writing = writing;
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
            var outcome = writing ? requests.submit(employee, unit, id, reason(request))
                : requests.current(employee, unit, id);
            if (outcome == null) { respond(response, Status.STATUS_NOT_FOUND,
                    new JSONObject().put("code", "NO_CURRENT_REQUEST")); return; }
            respond(response, Status.STATUS_OK, new JSONObject()
                .put("requestId", outcome.requestId())
                .put("technicalId", outcome.technicalId())
                .put("unitCode", outcome.unitCode())
                .put("status", outcome.status())
                .put("expectedCycle", outcome.expectedCycle())
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
        if (request.getContent() == null || request.getContent().getContent() == null)
            throw new IllegalArgumentException("JSON body required");
        JSONObject json = new JSONObject(request.getContent().getContent());
        if (json.length() != 1 || !json.has("reason") || !(json.get("reason") instanceof String text))
            throw new IllegalArgumentException("Only reason is supported");
        return text;
    }

    private static void respond(WebScriptResponse response, int status, JSONObject result) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(result.toString());
    }
}
