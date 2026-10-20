package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.OfficeCycleContinuationService;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** POST /badea/office-continue/{technicalId}; REQUIRED transaction, no silent partial commits. */
public final class OfficeContinueWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final CorrespondenceActorRoleResolver roles;
    private final OfficeCycleContinuationService service;
    public OfficeContinueWebScript(CurrentEmployeeService employees, CorrespondenceActorRoleResolver roles,
            OfficeCycleContinuationService service) {
        this.employees = Objects.requireNonNull(employees);
        this.roles = Objects.requireNonNull(roles);
        this.service = Objects.requireNonNull(service);
    }
    @Override public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        try {
            String id = request.getServiceMatch().getTemplateVars().get("technicalId");
            if (id == null || !id.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid ID");
            // Empty request body: persisted state + REQUIRED transaction are the
            // concurrency guards. Retrying after a successful continuation gets 409.
            if (request.getContent() != null) {
                String raw = request.getContent().getContent();
                if (raw != null && !raw.isBlank() && !"{}".equals(raw.trim())) {
                    throw new IllegalArgumentException("Continuation must not include client-supplied state");
                }
            }
            var employee = employees.getCurrentEmployee();
            var role = roles.resolveCurrentUserRole();
            var command = new TransitionCorrespondenceCommand(id, role,
                    CorrespondenceAction.CONTINUE_CORRESPONDENCE, employee.employeeId(),
                    employee.department().map(d -> d.code()).orElse(null),
                    employee.nameEnglish(), employee.nameArabic(), null, "Begin another workflow round", null);
            long next = service.continueCycle(command);
            send(response, Status.STATUS_OK, new JSONObject().put("technicalId", id)
                    .put("state", "WITH_PRESIDENT_OFFICE").put("currentWorkflowCycle", next));
        } catch (CurrentEmployeeNotFoundException e) { error(response, Status.STATUS_NOT_FOUND, "EMPLOYEE_NOT_FOUND"); }
        catch (EmployeeDirectoryException e) { error(response, Status.STATUS_SERVICE_UNAVAILABLE, "EMPLOYEE_DIRECTORY_UNAVAILABLE"); }
        catch (CorrespondenceAuthorizationException e) { error(response, Status.STATUS_FORBIDDEN, "CORRESPONDENCE_FORBIDDEN"); }
        catch (OfficeCycleConflictException e) { error(response, 409, "CYCLE_CONFLICT"); }
        catch (IllegalArgumentException e) { error(response, Status.STATUS_BAD_REQUEST, "INVALID_REQUEST"); }
        catch (IllegalStateException e) { throw e; } // Do not swallow storage failures; force rollback.
    }
    private static void error(WebScriptResponse resp, int status, String code) throws IOException {
        send(resp,status,new JSONObject().put("code",code));
    }
    private static void send(WebScriptResponse resp,int status,JSONObject body) throws IOException {
        resp.setHeader("Cache-Control", "no-store");resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");resp.getWriter().write(body.toString());
    }
}
