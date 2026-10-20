package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.OfficeCompletionStateConflictException;
import org.badea.correspondence.application.OfficeDispatchCommand;
import org.badea.correspondence.application.OfficeDistributionService;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** One Office dispatch is atomic: PDF, recipients, immutable action, and state. */
public final class OfficeDispatchWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final CorrespondenceActorRoleResolver roles;
    private final OfficeDistributionService service;
    public OfficeDispatchWebScript(CurrentEmployeeService employees,
            CorrespondenceActorRoleResolver roles,OfficeDistributionService service) {
        this.employees=Objects.requireNonNull(employees);
        this.roles=Objects.requireNonNull(roles);
        this.service=Objects.requireNonNull(service);
    }
    @Override public void execute(WebScriptRequest req,WebScriptResponse resp) throws IOException {
        String id=req.getServiceMatch().getTemplateVars().get("technicalId");
        if (id==null||!id.matches("[0-9a-fA-F-]{36}")) {
            answer(resp,Status.STATUS_BAD_REQUEST,"INVALID_REQUEST",null);return;
        }
        if (req.getContent()==null||req.getContent().getContent()==null) {
            answer(resp,Status.STATUS_BAD_REQUEST,"INVALID_REQUEST",null);return;
        }
        try {
            JSONObject body=new JSONObject(req.getContent().getContent());
            if (body.length()!=2) throw new IllegalArgumentException("Unexpected fields");
            long president=revision(body,"expectedPresidentReviewRevision");
            long office=revision(body,"expectedOfficeRevision");
            var employee=employees.getCurrentEmployee();
            String dispatchId=service.distribute(new OfficeDispatchCommand(
                    id,president,office,employee.employeeId(),employee.nameEnglish(),
                    employee.nameArabic()),roles.resolveCurrentUserRole());
            JSONObject response=new JSONObject();
            response.put("technicalId",id);
            response.put("dispatchId",dispatchId);
            response.put("state","WITH_DIRECTOR");
            answer(resp,Status.STATUS_OK,null,response);
        } catch (CorrespondenceAuthorizationException forbidden) {
            answer(resp,Status.STATUS_FORBIDDEN,"CORRESPONDENCE_FORBIDDEN",null);
        } catch (OfficeCompletionStateConflictException stale) {
            answer(resp,409,"OFFICE_COMPLETION_CONFLICT",null);
        } catch (IllegalArgumentException invalid) {
            answer(resp,Status.STATUS_BAD_REQUEST,"INVALID_REQUEST",null);
        }
        // Runtime errors from Alfresco persistence/PDF generation intentionally escape:
        // catches returning HTTP 500 here would allow Alfresco to commit partial writes.
    }
    private static long revision(JSONObject body,String name) {
        Object raw=body.opt(name);
        if (!(raw instanceof Number number) || number.longValue()<1
                || number.doubleValue()!=number.longValue())
            throw new IllegalArgumentException("Invalid snapshot revision");
        return number.longValue();
    }
    private static void answer(WebScriptResponse response,int status,String error,JSONObject data) throws IOException {
        response.setStatus(status);response.setHeader("Cache-Control","no-store");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(error==null ? data.toString():new JSONObject().put("code",error).toString());
    }
}
