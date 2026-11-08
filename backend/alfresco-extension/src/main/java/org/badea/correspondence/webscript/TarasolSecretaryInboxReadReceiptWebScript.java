package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceReadReceipts;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.organization.TarasolSecretarySharedInboxService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Writes only the authenticated secretary's per-employee read marker; never Director actions. */
public final class TarasolSecretaryInboxReadReceiptWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final TarasolSecretarySharedInboxService inbox;
    private final AlfrescoCorrespondenceReadReceipts receipts;

    public TarasolSecretaryInboxReadReceiptWebScript(CurrentEmployeeService employees,
            TarasolSecretarySharedInboxService inbox, AlfrescoCorrespondenceReadReceipts receipts) {
        this.employees = Objects.requireNonNull(employees);
        this.inbox = Objects.requireNonNull(inbox);
        this.receipts = Objects.requireNonNull(receipts);
    }

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        try {
            String unitCode = request.getParameter("unitCode");
            if (unitCode == null || !unitCode.matches("[0-9]{3}"))
                throw new IllegalArgumentException("Valid unitCode required");
            String id = request.getServiceMatch().getTemplateVars().get("technicalId");
            if (id == null || !id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
                throw new IllegalArgumentException("Canonical correspondence ID required");
            JSONObject body = new JSONObject(request.getContent().getContent());
            Object rawSequence = body.opt("throughSequence");
            if (!(rawSequence instanceof Number number)
                    || !number.toString().matches("[0-9]+"))
                throw new IllegalArgumentException("Valid read sequence required");
            long sequence = Long.parseLong(number.toString());

            var actor = employees.getCurrentEmployee();
            var unit = new DepartmentCode(unitCode);
            String employeeId = actor.employeeId().value();
            // readCase checks BOTH the secretary Alfresco group and active Oracle
            // assignment, then confirms that this correspondence belongs to the
            // selected unit's shared Inbox BEFORE any privileged receipt write.
            long applied = inbox.readCase(actor, unit, id,
                    () -> receipts.markRead(id, CorrespondenceActorRole.DIRECTOR,
                            unit, employeeId, sequence, true));
            respond(response, Status.STATUS_OK,
                    new JSONObject().put("readThroughSequence", applied));
        } catch (CorrespondenceAuthorizationException denied) {
            error(response, Status.STATUS_FORBIDDEN, "ACCESS_DENIED");
        } catch (CurrentEmployeeNotFoundException missing) {
            error(response, Status.STATUS_NOT_FOUND, "EMPLOYEE_NOT_FOUND");
        } catch (EmployeeDirectoryException unavailable) {
            error(response, Status.STATUS_SERVICE_UNAVAILABLE, "EMPLOYEE_DIRECTORY_UNAVAILABLE");
        } catch (CorrespondenceRepositoryException failed) {
            error(response, Status.STATUS_INTERNAL_SERVER_ERROR, "READ_STATE_UNAVAILABLE");
        } catch (IllegalArgumentException | JSONException invalid) {
            error(response, Status.STATUS_BAD_REQUEST, "INVALID_REQUEST");
        }
    }

    private static void error(WebScriptResponse response, int status, String code)
            throws IOException {
        respond(response, status, new JSONObject().put("code", code));
    }
    private static void respond(WebScriptResponse response, int status, JSONObject body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body.toString());
    }
}
