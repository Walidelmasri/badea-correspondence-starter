package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.OfficeCycleContinuationService;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** A completed TO Director may initiate another audited round of the same correspondence. */
public final class DirectorContinueWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final CorrespondenceActorRoleResolver roles;
    private final OfficeCycleContinuationService cycles;
    public DirectorContinueWebScript(CurrentEmployeeService employees,
            CorrespondenceActorRoleResolver roles, OfficeCycleContinuationService cycles) {
        this.employees = Objects.requireNonNull(employees);
        this.roles = Objects.requireNonNull(roles);
        this.cycles = Objects.requireNonNull(cycles);
    }
    @Override public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        try {
            String id = request.getServiceMatch().getTemplateVars().get("technicalId");
            if (id == null || !id.matches("[0-9a-fA-F-]{36}") || request.getContent() == null)
                throw new IllegalArgumentException("Invalid correspondence ID or body");
            JSONObject json = new JSONObject(request.getContent().getContent());
            if (json.length() != 1 || !json.has("reason"))
                throw new IllegalArgumentException("Only reason is supported");
            String reason = json.getString("reason").trim();
            if (reason.isEmpty() || reason.length() > 2000)
                throw new IllegalArgumentException("Reason must contain 1-2000 characters");
            var employee = employees.getCurrentEmployee();
            CorrespondenceActorRole role = roles.resolveCurrentUserRole();
            if (role != CorrespondenceActorRole.DIRECTOR || employee.department().isEmpty())
                throw new CorrespondenceAuthorizationException("A Director with department is required");
            var command = new TransitionCorrespondenceCommand(id, role,
                    CorrespondenceAction.DIRECTOR_CONTINUE_CORRESPONDENCE,
                    employee.employeeId(), employee.department().get().code(),
                    employee.nameEnglish(), employee.nameArabic(), null, reason, null);
            long next = cycles.continueCycle(command);
            send(response, Status.STATUS_OK, new JSONObject().put("technicalId", id)
                    .put("state", "WITH_PRESIDENT_OFFICE").put("currentWorkflowCycle", next));
        } catch (CorrespondenceAuthorizationException e) { fail(response, Status.STATUS_FORBIDDEN, "CORRESPONDENCE_FORBIDDEN"); }
          catch (OfficeCycleConflictException e) { fail(response, 409, "CYCLE_CONFLICT"); }
          catch (IllegalArgumentException | org.json.JSONException e) { fail(response, Status.STATUS_BAD_REQUEST, "INVALID_REQUEST"); }
          catch (IllegalStateException e) { throw e; } // REQUIRED Alfresco transaction must roll back.
    }
    private static void fail(WebScriptResponse response, int status, String code) throws IOException {
        send(response, status, new JSONObject().put("code", code));
    }
    private static void send(WebScriptResponse response, int status, JSONObject body) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body.toString());
    }
}
