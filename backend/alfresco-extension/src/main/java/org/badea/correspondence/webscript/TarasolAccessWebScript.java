package org.badea.correspondence.webscript;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;

import org.alfresco.service.cmr.security.AuthorityService;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.CorrespondenceTransitionRepository;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.organization.TarasolAccessService;
import org.badea.correspondence.security.AuthenticatedUserProvider;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.directory.DepartmentCode;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Admin and scoped Director Team Access endpoints share the same conservative
 * mutation/authorization boundary; URL/method are fixed by separate descriptors.
 */
public final class TarasolAccessWebScript extends AbstractWebScript {
    private static final String TARASOL_ADMINS = "GROUP_BADEA_TARASOL_ADMINS";
    private static final String ALFRESCO_ADMINS = "GROUP_ALFRESCO_ADMINISTRATORS";
    private final TarasolAccessService service;
    private final CurrentEmployeeService currentEmployee;
    private final CorrespondenceActorRoleResolver roles;
    private final AuthenticatedUserProvider users;
    private final AuthorityService authorityService;
    private final CorrespondenceTransitionRepository correspondence;
    private final boolean adminMode;
    private final boolean writing;

    public TarasolAccessWebScript(TarasolAccessService service,
            CurrentEmployeeService currentEmployee,
            CorrespondenceActorRoleResolver roles,
            AuthenticatedUserProvider users,
            AuthorityService authorityService,
            CorrespondenceTransitionRepository correspondence,
            boolean adminMode, boolean writing) {
        this.service = Objects.requireNonNull(service);
        this.currentEmployee = Objects.requireNonNull(currentEmployee);
        this.roles = Objects.requireNonNull(roles);
        this.users = Objects.requireNonNull(users);
        this.authorityService = Objects.requireNonNull(authorityService);
        this.correspondence = Objects.requireNonNull(correspondence);
        this.adminMode = adminMode;
        this.writing = writing;
    }

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            EmployeeDirectoryEntry employee = currentEmployee.getCurrentEmployee();
            String employeeId = employee.employeeId().value();
            JSONObject input = writing ? body(request) : new JSONObject();
            String unit = writing ? required(input, "unitCode") : request.getParameter("unitCode");
            if (unit == null || !unit.matches("[0-9]{3}")) {
                throw new IllegalArgumentException("Valid unitCode required");
            }
            if (adminMode) {
                requireAdmin();
            } else {
                // The caller may manage only their currently appointed unit;
                // never trust the client-side workspace selection or manager ID.
                var workspace = roles.resolveWorkspace(unit, employee);
                if (workspace.role() != CorrespondenceActorRole.DIRECTOR
                        && workspace.role() != CorrespondenceActorRole.PRESIDENT_OFFICE) {
                    throw new CorrespondenceAuthorizationException("Active manager workspace required");
                }
            }
            JSONObject result;
            if (!writing) {
                result = serialize(service.read(unit, employeeId, adminMode));
            } else if (adminMode) {
                var change = new TarasolAccessService.AppointmentChange(
                    required(input,"action"), unit, input.optLong("assignmentId"),
                    optional(input,"employeeId"), optional(input,"capacity"),
                    timestamp(input,"startsAt"), timestamp(input,"endsAt"),
                    optional(input,"appointmentRef"), optional(input,"reason"));
                long id = service.changeAppointment(change, employee.employeeId());
                result = new JSONObject().put("id", id).put("ok", true);
            } else {
                var change = new TarasolAccessService.TeamChange(
                    required(input,"action"), unit, input.optLong("managerAssignmentId"),
                    input.optLong("recordId"), optional(input,"employeeId"),
                    optional(input,"delegationType"), optional(input,"caseTechnicalId"),
                    timestamp(input,"startsAt"), timestamp(input,"endsAt"),
                    optional(input,"reason"));
                if ("GRANT_DELEGATION".equals(change.action())
                        && "CASE_HANDOFF".equals(change.delegationType())) {
                    // Never allow a crafted case ID to grant another department's records.
                    var caseContext = correspondence.getAccessContext(change.caseTechnicalId());
                    DepartmentCode expected = new DepartmentCode(unit);
                    if (!expected.equals(caseContext.originDepartmentCode())
                            && !expected.equals(caseContext.assignedDepartmentCode())) {
                        throw new CorrespondenceAuthorizationException("Case is outside this unit");
                    }
                }
                long id = service.changeTeam(change, employee.employeeId());
                result = new JSONObject().put("id", id).put("ok", true);
            }
            send(response, Status.STATUS_OK, result);
        } catch (CorrespondenceAuthorizationException ex) {
            sendError(response, Status.STATUS_FORBIDDEN, "ACCESS_DENIED", "Not authorized to manage this access");
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            sendError(response, 409, "INVALID_OR_CONFLICTING_ASSIGNMENT", ex.getMessage());
        } catch (RuntimeException ex) {
            // Database/network failure must not leak SQL, credentials or schema details.
            // Mutation operations fail closed; no success response after an uncertain result.
            sendError(response, Status.STATUS_SERVICE_UNAVAILABLE,
                "TARASOL_ACCESS_UNAVAILABLE", "Organizational access could not be completed");
        }
    }

    private void requireAdmin() {
        Set<String> authorities = authorityService.getAuthoritiesForUser(users.getUsername());
        if (authorities == null || (!authorities.contains(TARASOL_ADMINS)
                && !authorities.contains(ALFRESCO_ADMINS))) {
            throw new CorrespondenceAuthorizationException("Tarasol administrator group required");
        }
    }

    private static JSONObject body(WebScriptRequest request) throws IOException {
        Content c = request.getContent();
        if (c == null || c.getContent() == null || c.getContent().isBlank()) {
            throw new IllegalArgumentException("Request JSON required");
        }
        return new JSONObject(c.getContent());
    }
    private static String required(JSONObject input, String field) {
        String value = optional(input, field);
        if (value == null) throw new IllegalArgumentException(field + " required");
        return value;
    }
    private static String optional(JSONObject input, String field) {
        if (!input.has(field) || input.isNull(field)) return null;
        Object raw = input.get(field);
        if (!(raw instanceof String)) throw new IllegalArgumentException("Invalid " + field);
        String value = ((String) raw).trim();
        return value.isEmpty() ? null : value;
    }
    private static OffsetDateTime timestamp(JSONObject input, String field) {
        String text = optional(input, field);
        return text == null ? null : OffsetDateTime.parse(text);
    }
    private static JSONObject serialize(TarasolAccessService.AccessSnapshot access) {
        JSONObject result = new JSONObject();
        JSONArray appointments = new JSONArray();
        for (var a : access.appointments()) {
            appointments.put(new JSONObject()
                .put("id", a.id()).put("unitCode", a.unitCode())
                .put("employeeId", a.employeeId()).put("capacity", a.capacity())
                .put("startsAt", a.startsAt()).put("endsAt", a.endsAt())
                .put("revokedAt", a.revokedAt()).put("active", a.active()));
        }
        result.put("appointments", appointments);
        JSONArray secretaries = new JSONArray();
        for (var entry : access.secretaries()) secretaries.put(toJson(entry));
        result.put("secretaries", secretaries);
        JSONArray delegations = new JSONArray();
        for (var entry : access.delegations()) delegations.put(toJson(entry));
        result.put("delegations", delegations);
        return result;
    }
    private static JSONObject toJson(TarasolAccessService.TeamEntry e) {
        return new JSONObject().put("id", e.id())
            .put("managerAssignmentId", e.managerAssignmentId())
            .put("employeeId", e.employeeId()).put("type", e.type())
            .put("caseTechnicalId", e.caseTechnicalId())
            .put("startsAt", e.startsAt()).put("endsAt", e.endsAt())
            .put("revokedAt", e.revokedAt()).put("active", e.active());
    }
    private static void sendError(WebScriptResponse r, int status, String code, String msg)
            throws IOException {
        send(r,status,new JSONObject().put("code",code).put("message",msg));
    }
    private static void send(WebScriptResponse r, int status, JSONObject json) throws IOException {
        r.setStatus(status);
        r.setContentType("application/json;charset=UTF-8");
        r.getWriter().write(json.toString());
    }
}
