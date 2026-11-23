package org.badea.correspondence.webscript;

import org.badea.correspondence.security.CorrespondenceWorkspaceContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.badea.correspondence.application.CorrespondenceDetailsService;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceHistoryReader;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.InstitutionalMailboxReadScope;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Reads history only after the existing correspondence read authorization. */
public final class GetCorrespondenceHistoryWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final CorrespondenceActorRoleResolver roles;
    private final CorrespondenceDetailsService details;
    private final AlfrescoCorrespondenceHistoryReader reader;
    private final EmployeeDirectory directory;

    public GetCorrespondenceHistoryWebScript(CurrentEmployeeService employees,
            CorrespondenceActorRoleResolver roles, CorrespondenceDetailsService details,
            AlfrescoCorrespondenceHistoryReader reader, EmployeeDirectory directory) {
        this.employees = Objects.requireNonNull(employees);
        this.roles = Objects.requireNonNull(roles);
        this.details = Objects.requireNonNull(details);
        this.reader = Objects.requireNonNull(reader);
        this.directory = Objects.requireNonNull(directory);
    }

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        try {
            String id = request.getServiceMatch().getTemplateVars().get("technicalId");
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CorrespondenceActorRole role = roles.resolveCurrentUserRole();
            EmployeeDirectoryEntry employee = employees.getCurrentEmployee();
            DepartmentCode department = employee.department()
                    .map(d -> d.code()).orElse(null);

            var authorizedWorkspace = CorrespondenceWorkspaceContext.resolve(roles,

                request.getParameter("workspaceUnit"), employee);

            role = authorizedWorkspace.role();

            department = authorizedWorkspace.unit();
            // Authenticated role + persisted correspondence participation grant
            // institutional visibility, while private drafts remain private.
            final var approvedRole = role;
            final var approvedDepartment = department;
            List<AlfrescoCorrespondenceHistoryReader.ActionEntry> history =
                    InstitutionalMailboxReadScope.readValidatedWorkspace(approvedRole, () -> {
                        details.getDetails(id, approvedRole, approvedDepartment,
                                employee.employeeId().value());
                        return reader.list(id);
                    });
            JSONArray items = new JSONArray();
            Map<String, Optional<String>> arabicNames = new HashMap<>();
            for (var a : history) {
                JSONObject item = new JSONObject();
                item.put("sequence", a.sequence());
                item.put("actionType", a.actionType());
                item.put("actorRole", a.actorRole());
                item.put("actorNameEnglish", a.actorNameEnglish());
                item.put("actorNameArabic", nullable(displayArabicName(a, arabicNames)));
                item.put("fromState", a.fromState());
                item.put("toState", a.toState());
                item.put("actedAt", a.actedAt().toString());
                item.put("explanation", nullable(a.explanation()));
                items.put(item);
            }
            JSONObject output = new JSONObject();
            output.put("items", items);
            output.put("total", items.length());
            response.setHeader("Cache-Control", "no-store");
            respond(response, Status.STATUS_OK, output);
        } catch (CorrespondenceAuthorizationException e) {
            error(response, Status.STATUS_FORBIDDEN, "CORRESPONDENCE_FORBIDDEN");
        } catch (CurrentEmployeeNotFoundException e) {
            error(response, Status.STATUS_NOT_FOUND, "EMPLOYEE_NOT_FOUND");
        } catch (org.badea.correspondence.directory.DepartmentDirectoryException e) {
            error(response, Status.STATUS_SERVICE_UNAVAILABLE, "ORGANIZATION_DIRECTORY_UNAVAILABLE");
        } catch (EmployeeDirectoryException e) {
            error(response, Status.STATUS_SERVICE_UNAVAILABLE, "EMPLOYEE_DIRECTORY_UNAVAILABLE");
        } catch (CorrespondenceRepositoryException e) {
            error(response, Status.STATUS_INTERNAL_SERVER_ERROR, "CORRESPONDENCE_REPOSITORY_ERROR");
        } catch (IllegalArgumentException e) {
            error(response, Status.STATUS_BAD_REQUEST, "INVALID_REQUEST");
        }
    }

    // Display-only fallback for older department completions that did not store
    // actorNameArabic. Never write to immutable workflow history.
    private String displayArabicName(AlfrescoCorrespondenceHistoryReader.ActionEntry action,
            Map<String, Optional<String>> cache) {
        if (action.actorNameArabic() != null && !action.actorNameArabic().isBlank()) {
            return action.actorNameArabic();
        }
        if (!"COMPLETE_DEPARTMENT_ASSIGNMENT".equals(action.actionType())
                || action.actorEmployeeId() == null || action.actorEmployeeId().isBlank()) {
            return action.actorNameArabic();
        }
        Optional<String> resolved = cache.computeIfAbsent(action.actorEmployeeId(), id -> {
            try {
                return directory.findActiveByEmployeeId(new EmployeeId(id))
                        .map(EmployeeDirectoryEntry::nameArabic)
                        .filter(name -> !name.isBlank());
            } catch (EmployeeDirectoryException lookupFailed) {
                // Preserve history availability and the existing English fallback.
                return Optional.empty();
            }
        });
        return resolved.orElse(action.actorNameArabic());
    }

    private static Object nullable(String s) { return s == null ? JSONObject.NULL : s; }
    private static void error(WebScriptResponse r, int code, String name) throws IOException {
        JSONObject out = new JSONObject();
        out.put("code", name);
        out.put("message", "Unable to retrieve correspondence history.");
        r.setHeader("Cache-Control", "no-store");
        respond(r, code, out);
    }
    private static void respond(WebScriptResponse r, int code, JSONObject body)
            throws IOException {
        r.setStatus(code);
        r.setContentType("application/json;charset=UTF-8");
        r.getWriter().write(body.toString());
    }
}
