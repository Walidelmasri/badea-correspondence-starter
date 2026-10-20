package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.DepartmentAssignment;
import org.badea.correspondence.application.DepartmentAssignmentService;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** GET lists authorized assignments; POST completes one Director TO assignment. */
public final class DepartmentAssignmentsWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final CorrespondenceActorRoleResolver roles;
    private final DepartmentAssignmentService service;
    private final boolean completion;
    public DepartmentAssignmentsWebScript(CurrentEmployeeService employees,
            CorrespondenceActorRoleResolver roles,DepartmentAssignmentService service,boolean completion) {
        this.employees=Objects.requireNonNull(employees);
        this.roles=Objects.requireNonNull(roles);
        this.service=Objects.requireNonNull(service);
        this.completion=completion;
    }
    @Override public void execute(WebScriptRequest req,WebScriptResponse resp) throws IOException {
        String id=req.getServiceMatch().getTemplateVars().get("technicalId");
        if (id==null||!id.matches("[0-9a-fA-F-]{36}")) {
            error(resp,Status.STATUS_BAD_REQUEST,"INVALID_REQUEST");return;
        }
        try {
            var employee=employees.getCurrentEmployee();
            var role=roles.resolveCurrentUserRole();
            DepartmentCode department=employee.department().map(v->v.code()).orElse(null);
            JSONArray items=new JSONArray();
            if (completion) {
                String assignmentId=req.getServiceMatch().getTemplateVars().get("assignmentId");
                if (assignmentId==null || !assignmentId.matches("[0-9a-fA-F-]{36}")
                        || req.getContent()==null || req.getContent().getContent()==null)
                    throw new IllegalArgumentException("Invalid completion payload");
                JSONObject body=new JSONObject(req.getContent().getContent());
                if (body.length()!=1) throw new IllegalArgumentException("Unexpected fields");
                String response=body.getString("responseText").trim();
                items.put(serialize(service.complete(id,assignmentId,role,department,
                        employee.employeeId(),employee.nameEnglish(),response)));
            } else {
                for (DepartmentAssignment assignment:service.list(id,role,department,employee.employeeId().value()))
                    items.put(serialize(assignment));
            }
            JSONObject result=new JSONObject();
            result.put("technicalId",id);result.put("items",items);result.put("total",items.length());
            send(resp,Status.STATUS_OK,result);
        } catch (CorrespondenceAuthorizationException forbidden) {
            error(resp,Status.STATUS_FORBIDDEN,"CORRESPONDENCE_FORBIDDEN");
        } catch (IllegalArgumentException invalid) {
            error(resp,Status.STATUS_BAD_REQUEST,"INVALID_REQUEST");
        }
        // Any storage failure escapes to force rollback of REQUIRED transaction.
    }
    private static JSONObject serialize(DepartmentAssignment a) {
        JSONObject result=new JSONObject();
        result.put("assignmentId",a.assignmentId());
        result.put("departmentCode",a.departmentCode().value());
        result.put("recipientType",a.recipientType().name());
        result.put("status",a.status().name());
        result.put("assignedAt",a.assignedAt().toString());
        result.put("responseText",a.responseText()==null?JSONObject.NULL:a.responseText());
        result.put("completedAt",a.completedAt()==null?JSONObject.NULL:a.completedAt().toString());
        return result;
    }
    private static void error(WebScriptResponse resp,int status,String code)throws IOException {
        send(resp,status,new JSONObject().put("code",code));
    }
    private static void send(WebScriptResponse resp,int status,JSONObject body)throws IOException {
        resp.setStatus(status);resp.setHeader("Cache-Control","no-store");
        resp.setContentType("application/json;charset=UTF-8");resp.getWriter().write(body.toString());
    }
}
