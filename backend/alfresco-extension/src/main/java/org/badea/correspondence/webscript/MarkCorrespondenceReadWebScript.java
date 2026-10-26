package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CorrespondenceDetailsService;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceReadReceipts;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** A read acknowledgement cannot modify workflow state or mark anyone else as read. */
public final class MarkCorrespondenceReadWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final CorrespondenceActorRoleResolver roles;
    private final CorrespondenceDetailsService details;
    private final AlfrescoCorrespondenceReadReceipts receipts;

    public MarkCorrespondenceReadWebScript(CurrentEmployeeService employees,
            CorrespondenceActorRoleResolver roles, CorrespondenceDetailsService details,
            AlfrescoCorrespondenceReadReceipts receipts) {
        this.employees = Objects.requireNonNull(employees);
        this.roles = Objects.requireNonNull(roles);
        this.details = Objects.requireNonNull(details);
        this.receipts = Objects.requireNonNull(receipts);
    }

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        try {
            String id = request.getServiceMatch().getTemplateVars().get("technicalId");
            if (id == null || id.isBlank()) throw new IllegalArgumentException("technicalId required");
            JSONObject body = new JSONObject(request.getContent().getContent());
            if (!body.has("throughSequence") || body.isNull("throughSequence"))
                throw new IllegalArgumentException("throughSequence required");
            long sequence = body.getLong("throughSequence");
            if (sequence < 0) throw new IllegalArgumentException("Invalid read sequence");
            var employee = employees.getCurrentEmployee();
            CorrespondenceActorRole role = roles.resolveCurrentUserRole();
            DepartmentCode department = employee.department().map(d -> d.code()).orElse(null);
            String employeeId = employee.employeeId().value();
            // Mandatory existing authorization check before any write.
            var authorized = details.getDetails(id, role, department, employeeId);
            // The repository checks incoming target role and department.
            // The existing details service has already authorized this employee.
            long applied = receipts.markRead(id, role, department, employeeId,
                    sequence, role == CorrespondenceActorRole.DIRECTOR);
            respond(response, Status.STATUS_OK, new JSONObject().put("readThroughSequence", applied));
        } catch (CorrespondenceAuthorizationException ex) {
            error(response, Status.STATUS_FORBIDDEN, "CORRESPONDENCE_FORBIDDEN");
        } catch (CurrentEmployeeNotFoundException ex) {
            error(response, Status.STATUS_NOT_FOUND, "EMPLOYEE_NOT_FOUND");
        } catch (EmployeeDirectoryException ex) {
            error(response, Status.STATUS_SERVICE_UNAVAILABLE, "EMPLOYEE_DIRECTORY_UNAVAILABLE");
        } catch (CorrespondenceRepositoryException ex) {
            error(response, Status.STATUS_INTERNAL_SERVER_ERROR, "READ_STATE_UNAVAILABLE");
        } catch (IllegalArgumentException | org.json.JSONException ex) {
            error(response, Status.STATUS_BAD_REQUEST, "INVALID_REQUEST");
        }
    }
    private static void error(WebScriptResponse response, int code, String name) throws IOException {
        respond(response, code, new JSONObject().put("code", name));
    }
    private static void respond(WebScriptResponse response, int code, JSONObject body) throws IOException {
        response.setStatus(code);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body.toString());
    }
}
