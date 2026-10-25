package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Limited active-only recipient lookup, for authenticated correspondence participants. */
public final class SearchShareRecipientsWebScript extends AbstractWebScript {
    private final CurrentEmployeeService current;
    private final CorrespondenceActorRoleResolver roles;
    private final EmployeeDirectory directory;

    public SearchShareRecipientsWebScript(CurrentEmployeeService current,
            CorrespondenceActorRoleResolver roles, EmployeeDirectory directory) {
        this.current = Objects.requireNonNull(current);
        this.roles = Objects.requireNonNull(roles);
        this.directory = Objects.requireNonNull(directory);
    }

    @Override public void execute(WebScriptRequest req, WebScriptResponse resp) throws IOException {
        resp.setHeader("Cache-Control", "private, no-store");
        resp.setContentType("application/json;charset=UTF-8");
        try {
            current.getCurrentEmployee();
            roles.resolveCurrentUserRole();
            String query = req.getParameter("q");
            if (query == null || query.trim().length() < 2 || query.trim().length() > 60) {
                resp.setStatus(Status.STATUS_BAD_REQUEST);
                resp.getWriter().write(new JSONObject().put("code", "INVALID_QUERY").toString());
                return;
            }
            JSONArray employees = new JSONArray();
            for (var employee : directory.searchActive(query)) {
                employees.put(new JSONObject()
                        .put("username", employee.username().value())
                        .put("nameEnglish", employee.nameEnglish())
                        .put("nameArabic", employee.nameArabic()));
            }
            resp.setStatus(Status.STATUS_OK);
            resp.getWriter().write(new JSONObject().put("employees", employees).toString());
        } catch (CorrespondenceAuthorizationException | AccessDeniedException exception) {
            resp.setStatus(Status.STATUS_FORBIDDEN);
            resp.getWriter().write(new JSONObject().put("code", "FORBIDDEN").toString());
        } catch (EmployeeDirectoryException exception) {
            resp.setStatus(Status.STATUS_SERVICE_UNAVAILABLE);
            resp.getWriter().write(new JSONObject().put("code", "EMPLOYEE_DIRECTORY_UNAVAILABLE").toString());
        }
    }
}
