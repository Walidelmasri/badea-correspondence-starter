package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.organization.TarasolSecretaryRoundContinuationApprovalService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Manager-only discovery of secretary continuation requests inside the same Inbox workflow. */
public final class TarasolSecretaryRoundContinuationQueueWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final TarasolSecretaryRoundContinuationApprovalService approvals;

    public TarasolSecretaryRoundContinuationQueueWebScript(CurrentEmployeeService employees,
            TarasolSecretaryRoundContinuationApprovalService approvals) {
        this.employees = Objects.requireNonNull(employees);
        this.approvals = Objects.requireNonNull(approvals);
    }

    @Override public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            String code = request.getParameter("unitCode");
            if (code == null || !code.matches("[0-9]{3}"))
                throw new IllegalArgumentException("Valid unit code required");
            int offset = integer(request.getParameter("offset"), 0);
            int limit = integer(request.getParameter("limit"), 10);
            var page = approvals.pendingQueue(employees.getCurrentEmployee(),
                new DepartmentCode(code), offset, limit);
            JSONArray items = new JSONArray();
            for (var item : page.items()) {
                items.put(new JSONObject()
                    .put("technicalId", item.technicalId())
                    .put("requestId", item.requestId())
                    .put("unitCode", item.unitCode())
                    .put("ownerEmployeeId", item.ownerEmployeeId())
                    .put("subject", item.subject())
                    .put("documentNumber", item.documentNumber() == null
                        ? JSONObject.NULL : item.documentNumber())
                    .put("reason", item.reason())
                    .put("expectedCycle", item.expectedCycle())
                    .put("requestedAt", item.requestedAt().toString()));
            }
            send(response, Status.STATUS_OK, new JSONObject().put("items", items)
                .put("offset", page.offset()).put("limit", page.limit())
                .put("total", page.total()));
        } catch (CorrespondenceAuthorizationException denied) {
            send(response, Status.STATUS_FORBIDDEN,
                new JSONObject().put("code", "ACCESS_DENIED"));
        } catch (IllegalArgumentException invalid) {
            send(response, Status.STATUS_BAD_REQUEST,
                new JSONObject().put("code", "INVALID_REQUEST"));
        }
    }

    private static int integer(String text, int fallback) {
        if (text == null) return fallback;
        try { return Integer.parseInt(text); }
        catch (NumberFormatException bad) { throw new IllegalArgumentException("Invalid paging", bad); }
    }
    private static void send(WebScriptResponse response, int status, JSONObject body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body.toString());
    }
}
