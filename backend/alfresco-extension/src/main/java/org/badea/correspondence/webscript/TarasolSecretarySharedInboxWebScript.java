package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceMailboxLister;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.organization.TarasolSecretarySharedInboxService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Same summary contract as /badea/correspondence?box=inbox, but secretary scoped. */
public final class TarasolSecretarySharedInboxWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final TarasolSecretarySharedInboxService inbox;

    public TarasolSecretarySharedInboxWebScript(CurrentEmployeeService employees,
            TarasolSecretarySharedInboxService inbox) {
        this.employees = Objects.requireNonNull(employees);
        this.inbox = Objects.requireNonNull(inbox);
    }

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            String unitCode = request.getParameter("unitCode");
            if (unitCode == null || !unitCode.matches("[0-9]{3}")) {
                throw new IllegalArgumentException("Valid unitCode required");
            }
            String view = request.getParameter("view");
            if (view == null) view = "needs_action";
            int offset = integer(request.getParameter("offset"), 0, 0, Integer.MAX_VALUE);
            int limit = integer(request.getParameter("limit"), 10, 1, 100);
            var page = inbox.list(employees.getCurrentEmployee(), new DepartmentCode(unitCode),
                view, offset, limit);
            JSONArray items = new JSONArray();
            for (AlfrescoCorrespondenceMailboxLister.MailboxSummary item : page.items()) {
                items.put(new JSONObject()
                    .put("technicalId", item.technicalId())
                    .put("subject", item.subject())
                    .put("documentNumber", nullable(item.documentNumber()))
                    .put("state", item.state())
                    .put("originDepartmentCode", item.originDepartmentCode())
                    .put("senderName", item.senderName())
                    .put("needsAction", item.needsAction())
                    .put("unread", item.unread())
                    .put("incomingSequence", item.incomingSequence())
                    .put("createdAt", item.createdAt().toString())
                    .put("modifiedAt", item.modifiedAt().toString()));
            }
            send(response, Status.STATUS_OK, new JSONObject()
                .put("items", items).put("offset", page.offset())
                .put("limit", page.limit()).put("total", page.total()));
        } catch (CorrespondenceAuthorizationException denied) {
            error(response, Status.STATUS_FORBIDDEN, "ACCESS_DENIED");
        } catch (CurrentEmployeeNotFoundException missing) {
            error(response, Status.STATUS_NOT_FOUND, "EMPLOYEE_NOT_FOUND");
        } catch (EmployeeDirectoryException unavailable) {
            error(response, Status.STATUS_SERVICE_UNAVAILABLE, "EMPLOYEE_DIRECTORY_UNAVAILABLE");
        } catch (CorrespondenceRepositoryException failed) {
            error(response, Status.STATUS_INTERNAL_SERVER_ERROR, "CORRESPONDENCE_REPOSITORY_ERROR");
        } catch (IllegalArgumentException invalid) {
            error(response, Status.STATUS_BAD_REQUEST, "INVALID_REQUEST");
        }
        // Do not suppress unexpected Oracle failures: they must be diagnosable.
    }

    private static int integer(String value, int defaultValue, int min, int max) {
        if (value == null) return defaultValue;
        try {
            int number = Integer.parseInt(value);
            if (number < min || number > max) throw new IllegalArgumentException("Invalid pagination");
            return number;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid pagination", failure);
        }
    }

    private static Object nullable(String value) { return value == null ? JSONObject.NULL : value; }

    private static void error(WebScriptResponse response, int status, String code)
            throws IOException {
        send(response, status, new JSONObject().put("code", code)
            .put("message", "Unable to retrieve secretary Inbox."));
    }

    private static void send(WebScriptResponse response, int status, JSONObject json)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json.toString());
    }
}
