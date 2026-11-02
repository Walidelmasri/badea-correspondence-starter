package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.badea.correspondence.application.OfficeCompletionService;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.OfficeCompletionState;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.InstitutionalMailboxReadScope;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class GetOfficeCompletionWebScript extends AbstractWebScript {
    private final CorrespondenceActorRoleResolver roles;
    private final OfficeCompletionService service;
    private final CurrentEmployeeService employees;
    public GetOfficeCompletionWebScript(CorrespondenceActorRoleResolver roles,
            OfficeCompletionService service) {
        this(roles, service, null);
    }
    public GetOfficeCompletionWebScript(CorrespondenceActorRoleResolver roles,
            OfficeCompletionService service, CurrentEmployeeService employees) {
        this.roles=Objects.requireNonNull(roles); this.service=Objects.requireNonNull(service);
        this.employees=employees;
    }
    @Override public void execute(WebScriptRequest request,WebScriptResponse response) throws IOException {
        try {
            String id=id(request);
            var role=roles.resolveCurrentUserRole();
            if (request.getParameter("workspaceUnit") != null) {
                if (employees == null) throw new CorrespondenceAuthorizationException("Workspace unavailable");
                role=roles.resolveWorkspace(request.getParameter("workspaceUnit"),
                    employees.getCurrentEmployee()).role();
            }
            final var activeRole = role;
            long presidentRevision=InstitutionalMailboxReadScope.read(activeRole,
                    () -> service.presidentRevision(id,activeRole));
            Optional<OfficeCompletionState> current=InstitutionalMailboxReadScope.read(activeRole,
                    () -> service.load(id,activeRole));
            JSONObject result=new JSONObject();
            result.put("technicalId",id);
            result.put("presidentReviewRevision",presidentRevision);
            result.put("revision",current.map(OfficeCompletionState::revision)
                    .orElseGet(() -> InstitutionalMailboxReadScope.read(activeRole,
                            () -> service.headRevision(id,activeRole))));
            if (current.isPresent()) {
                OfficeCompletionState snapshot=current.orElseThrow();
                result.put("state",new JSONObject(snapshot.json()));
                result.put("updatedByEmployeeId",snapshot.updatedByEmployeeId());
                result.put("updatedByNameEnglish",snapshot.updatedByNameEnglish());
                result.put("updatedByNameArabic",nullable(snapshot.updatedByNameArabic()));
                result.put("updatedAt",snapshot.updatedAt().toString());
            } else {
                result.put("state",JSONObject.NULL);
                result.put("updatedByEmployeeId",JSONObject.NULL);
                result.put("updatedByNameEnglish",JSONObject.NULL);
                result.put("updatedByNameArabic",JSONObject.NULL);
                result.put("updatedAt",JSONObject.NULL);
            }
            send(response,Status.STATUS_OK,result);
        } catch (CorrespondenceAuthorizationException e) { error(response,Status.STATUS_FORBIDDEN,"CORRESPONDENCE_FORBIDDEN"); }
        catch (CorrespondenceRepositoryException e) { error(response,Status.STATUS_INTERNAL_SERVER_ERROR,"OFFICE_COMPLETION_STORAGE_ERROR"); }
        catch (IllegalArgumentException e) { error(response,Status.STATUS_BAD_REQUEST,"INVALID_REQUEST"); }
    }
    private static String id(WebScriptRequest r) {
        String id=r.getServiceMatch().getTemplateVars().get("technicalId");
        if (id==null||id.isBlank()) throw new IllegalArgumentException("technicalId is required");
        return id.trim();
    }
    private static Object nullable(String s) {return s==null?JSONObject.NULL:s;}
    private static void error(WebScriptResponse r,int code,String name) throws IOException {
        JSONObject body=new JSONObject();body.put("code",name);send(r,code,body);
    }
    private static void send(WebScriptResponse r,int code,JSONObject body) throws IOException {
        r.setHeader("Cache-Control","no-store");r.setStatus(code);
        r.setContentType("application/json;charset=UTF-8");r.getWriter().write(body.toString());
    }
}
