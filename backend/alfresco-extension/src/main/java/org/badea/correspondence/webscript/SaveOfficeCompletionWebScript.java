package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.OfficeCompletionService;
import org.badea.correspondence.application.OfficeCompletionState;
import org.badea.correspondence.application.OfficeCompletionStateConflictException;
import org.badea.correspondence.application.SaveOfficeCompletionCommand;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class SaveOfficeCompletionWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final CorrespondenceActorRoleResolver roles;
    private final OfficeCompletionService service;
    public SaveOfficeCompletionWebScript(CurrentEmployeeService employees,
            CorrespondenceActorRoleResolver roles,OfficeCompletionService service) {
        this.employees=Objects.requireNonNull(employees);this.roles=Objects.requireNonNull(roles);
        this.service=Objects.requireNonNull(service);
    }
    @Override public void execute(WebScriptRequest req,WebScriptResponse resp) throws IOException {
        try {
            String id=req.getServiceMatch().getTemplateVars().get("technicalId");
            if (id==null||id.isBlank()) throw new IllegalArgumentException("technicalId required");
            Content content=req.getContent();
            if (content==null||content.getContent()==null||content.getContent().isBlank()) {
                throw new IllegalArgumentException("Request body required");
            }
            JSONObject body=new JSONObject(content.getContent());
            long expected=requiredRevision(body,"expectedRevision");
            long expectedPresident=requiredRevision(body,"expectedPresidentReviewRevision");
            JSONObject state=body.getJSONObject("state");
            var role=roles.resolveCurrentUserRole();
            var employee=employees.getCurrentEmployee();
            OfficeCompletionState saved=service.save(new SaveOfficeCompletionCommand(
                    id,expected,expectedPresident,employee.employeeId(),
                    employee.nameEnglish(),employee.nameArabic(),state.toString()),role);
            JSONObject answer=new JSONObject();
            answer.put("technicalId",id);
            answer.put("revision",saved.revision());
            answer.put("presidentReviewRevision",saved.presidentReviewRevision());
            answer.put("state",new JSONObject(saved.json()));
            answer.put("updatedByEmployeeId",saved.updatedByEmployeeId());
            answer.put("updatedAt",saved.updatedAt().toString());
            send(resp,Status.STATUS_OK,answer);
        } catch (CurrentEmployeeNotFoundException e) {error(resp,Status.STATUS_NOT_FOUND,"EMPLOYEE_NOT_FOUND");}
        catch (EmployeeDirectoryException e) {error(resp,Status.STATUS_SERVICE_UNAVAILABLE,"EMPLOYEE_DIRECTORY_UNAVAILABLE");}
        catch (CorrespondenceAuthorizationException e) {error(resp,Status.STATUS_FORBIDDEN,"CORRESPONDENCE_FORBIDDEN");}
        catch (OfficeCompletionStateConflictException e) {error(resp,409,"OFFICE_COMPLETION_CONFLICT");}
        catch (CorrespondenceRepositoryException e) {error(resp,Status.STATUS_INTERNAL_SERVER_ERROR,"OFFICE_COMPLETION_STORAGE_ERROR");}
        catch (IllegalArgumentException|JSONException e) {error(resp,Status.STATUS_BAD_REQUEST,"INVALID_REQUEST");}
    }
    private static long requiredRevision(JSONObject body,String field) {
        Object value=body.opt(field);
        if (!(value instanceof Number number) || number.longValue()<0
                || number.doubleValue()!=number.longValue()) {
            throw new IllegalArgumentException(field+" must be a nonnegative integer");
        }
        return number.longValue();
    }
    private static void error(WebScriptResponse r,int code,String name) throws IOException {
        JSONObject body=new JSONObject();body.put("code",name);send(r,code,body);
    }
    private static void send(WebScriptResponse r,int code,JSONObject body) throws IOException {
        r.setHeader("Cache-Control","no-store");r.setStatus(code);
        r.setContentType("application/json;charset=UTF-8");r.getWriter().write(body.toString());
    }
}
