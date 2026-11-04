package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.alfresco.service.cmr.security.AuthorityService;
import org.badea.correspondence.security.AuthenticatedUserProvider;

import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DepartmentDirectoryException;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;
import org.badea.correspondence.organization.TarasolEffectiveAccessReader;
import org.badea.correspondence.organization.TarasolSecretaryGroupPolicy;
import org.json.JSONArray;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONException;
import org.json.JSONObject;

import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/**
 * Returns the authenticated employee and their resolved
 * correspondence workflow role.
 *
 * Membership in both Office and Directors groups is supported;
 * President membership remains exclusive. Organizational capacities
 * are sourced from Tarasol-owned assignments.
 */
public final class CurrentEmployeeWebScript
        extends AbstractWebScript {

    private static final Log LOGGER =
            LogFactory.getLog(CurrentEmployeeWebScript.class);

    private final CurrentEmployeeService currentEmployeeService;

    private final CorrespondenceActorRoleResolver roleResolver;
    private final TarasolOrganizationReader organizationReader;
    private final AuthorityService authorityService;
    private final AuthenticatedUserProvider userProvider;
    private final TarasolEffectiveAccessReader effectiveAccess;

    public CurrentEmployeeWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver roleResolver) {

        this(currentEmployeeService, roleResolver, null, null, null, null);
    }

    public CurrentEmployeeWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver roleResolver,
            TarasolOrganizationReader organizationReader) {
        this(currentEmployeeService, roleResolver, organizationReader, null, null, null);
    }

    public CurrentEmployeeWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver roleResolver,
            TarasolOrganizationReader organizationReader,
            AuthorityService authorityService,
            AuthenticatedUserProvider userProvider) {
        this(currentEmployeeService, roleResolver, organizationReader,
            authorityService, userProvider, null);
    }

    public CurrentEmployeeWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver roleResolver,
            TarasolOrganizationReader organizationReader,
            AuthorityService authorityService,
            AuthenticatedUserProvider userProvider,
            TarasolEffectiveAccessReader effectiveAccess) {
        this.currentEmployeeService = Objects.requireNonNull(currentEmployeeService);
        this.roleResolver = Objects.requireNonNull(roleResolver);
        this.organizationReader = organizationReader;
        this.authorityService = authorityService;
        this.userProvider = userProvider;
        this.effectiveAccess = effectiveAccess;
    }

    @Override
    public void execute(
            WebScriptRequest request,
            WebScriptResponse response)
            throws IOException {

        try {
            // Existing workflow groups remain unchanged. Secretary access
            // requires its Alfresco group AND an active Oracle unit assignment;
            // neither alone confers a Director workspace.
            CorrespondenceActorRole role;
            EmployeeDirectoryEntry employee;
            List<TarasolEffectiveAccessReader.SecretaryUnit> secretaryUnits = List.of();
            List<TarasolEffectiveAccessReader.SecretaryUnit> delegationUnits = List.of();
            Set<CorrespondenceActorRole> groups;
            try {
                role = roleResolver.resolveCurrentUserRole();
                employee = currentEmployeeService.getCurrentEmployee();
                groups = roleResolver.resolveCurrentUserRoles();
                if (groups == null || groups.isEmpty()) groups = Set.of(role);
            } catch (CorrespondenceAuthorizationException denied) {
                if (authorityService == null || userProvider == null) throw denied;
                Set<String> authorities = authorityService.getAuthoritiesForUser(userProvider.getUsername());
                if (authorities == null) throw denied;
                // Never disguise conflicting President/Office/Director groups as
                // a secretary login. Preserve President-role exclusivity.
                if (authorities.contains("GROUP_BADEA_CORRESPONDENCE_PRESIDENT")
                        || authorities.contains("GROUP_BADEA_CORRESPONDENCE_PRESIDENT_OFFICE")
                        || authorities.contains("GROUP_BADEA_CORRESPONDENCE_DIRECTORS")) throw denied;
                employee = currentEmployeeService.getCurrentEmployee();
                if (authorities.contains("GROUP_BADEA_TARASOL_ADMINS")
                        || authorities.contains("GROUP_ALFRESCO_ADMINISTRATORS")) {
                    role = CorrespondenceActorRole.TARASOL_ADMIN;
                } else {
                    if (effectiveAccess == null) throw denied;
                    if (authorities.contains(TarasolSecretaryGroupPolicy.AUTHORITY)) {
                        secretaryUnits = effectiveAccess.findCurrentSecretaryUnits(employee.employeeId());
                    }
                    if (secretaryUnits.isEmpty()) {
                        delegationUnits = effectiveAccess.findCurrentDelegationUnits(employee.employeeId());
                        if (delegationUnits.isEmpty()) throw denied;
                        role = CorrespondenceActorRole.TARASOL_DELEGATE;
                    } else {
                        role = CorrespondenceActorRole.SECRETARY;
                    }
                }
                groups = Set.of();
            }
            if (effectiveAccess != null && secretaryUnits.isEmpty()
                    && role != CorrespondenceActorRole.SECRETARY
                    && TarasolSecretaryGroupPolicy.isMember(authorityService, userProvider)) {
                // Optional secretary workspaces must not prevent established
                // Director/Office/President logins when the new grant read fails.
                try {
                    secretaryUnits = effectiveAccess.findCurrentSecretaryUnits(employee.employeeId());
                } catch (IllegalStateException temporaryDirectoryFailure) {
                    LOGGER.warn("Optional Tarasol secretary workspaces are unavailable");
                }
            }
            if (effectiveAccess != null && delegationUnits.isEmpty()) {
                try {
                    delegationUnits = effectiveAccess.findCurrentDelegationUnits(employee.employeeId());
                } catch (IllegalStateException unavailable) {
                    if (role == CorrespondenceActorRole.TARASOL_DELEGATE) throw unavailable;
                    LOGGER.warn("Optional Tarasol delegation workspaces unavailable");
                }
            }
            List<ManagerWorkspaceView> workspaces = new ArrayList<>();
            if (organizationReader != null && role != CorrespondenceActorRole.PRESIDENT) {
                for (var assignment : organizationReader.findCurrentManagerWorkspaces(employee.employeeId())) {
                    CorrespondenceActorRole workspaceRole =
                        "101".equals(assignment.code().value())
                        ? CorrespondenceActorRole.PRESIDENT_OFFICE
                        : CorrespondenceActorRole.DIRECTOR;
                    // Office capacity requires an Office group; other manager
                    // capacities require an existing correspondence group.
                    if (workspaceRole == CorrespondenceActorRole.PRESIDENT_OFFICE
                            && !groups.contains(CorrespondenceActorRole.PRESIDENT_OFFICE)) continue;
                    if (workspaceRole == CorrespondenceActorRole.DIRECTOR
                            && !groups.contains(CorrespondenceActorRole.DIRECTOR)) continue;
                    workspaces.add(new ManagerWorkspaceView(
                        assignment.code().value(), assignment.nameEnglish(),
                        assignment.nameArabic(), assignment.capacity(), workspaceRole));
                }
            }

            boolean tarasolAdministrator = false;
            if (authorityService != null && userProvider != null) {
                Set<String> authorities = authorityService.getAuthoritiesForUser(userProvider.getUsername());
                tarasolAdministrator = authorities != null &&
                    (authorities.contains("GROUP_BADEA_TARASOL_ADMINS")
                        || authorities.contains("GROUP_ALFRESCO_ADMINISTRATORS"));
            }
            response.setHeader("Cache-Control", "no-store");

            writeJson(
                    response,
                    Status.STATUS_OK,
                    toJson(employee, role, groups, workspaces, secretaryUnits,
                        delegationUnits, tarasolAdministrator));

        } catch (IllegalStateException temporaryGrantFailure) {

            LOGGER.error("Failed to evaluate required Tarasol secretary grants", temporaryGrantFailure);
            writeError(response, Status.STATUS_SERVICE_UNAVAILABLE,
                    "TARASOL_ACCESS_UNAVAILABLE", "Access verification is temporarily unavailable.");

        } catch (CorrespondenceAuthorizationException exception) {

            writeError(
                    response,
                    Status.STATUS_FORBIDDEN,
                    "CORRESPONDENCE_FORBIDDEN",
                    "You do not have access to Tarasol.");

        } catch (CurrentEmployeeNotFoundException exception) {

            writeError(
                    response,
                    Status.STATUS_NOT_FOUND,
                    "EMPLOYEE_NOT_FOUND",
                    "No active employee profile was found "
                            + "for the authenticated user.");

        } catch (DepartmentDirectoryException exception) {

            LOGGER.error("Failed to read active Tarasol organizational assignments.", exception);
            writeError(response, Status.STATUS_SERVICE_UNAVAILABLE,
                    "ORGANIZATION_DIRECTORY_UNAVAILABLE",
                    "The organizational directory is currently unavailable.");

        } catch (EmployeeDirectoryException exception) {

            LOGGER.error(
                    "Failed to resolve the authenticated employee "
                            + "from the BADEA employee directory.",
                    exception);

            writeError(
                    response,
                    Status.STATUS_SERVICE_UNAVAILABLE,
                    "EMPLOYEE_DIRECTORY_UNAVAILABLE",
                    "The employee directory is currently unavailable.");
        }
    }

    private record ManagerWorkspaceView(
            String code, String nameEnglish, String nameArabic,
            String capacity, CorrespondenceActorRole role) { }

    private static JSONObject toJson(
            EmployeeDirectoryEntry employee,
            CorrespondenceActorRole role,
            Set<CorrespondenceActorRole> roles,
            List<ManagerWorkspaceView> workspaces,
            List<TarasolEffectiveAccessReader.SecretaryUnit> secretaryUnits,
            List<TarasolEffectiveAccessReader.SecretaryUnit> delegationUnits,
            boolean tarasolAdministrator) {

        try {
            JSONObject json = new JSONObject();

            json.put(
                    "employeeId",
                    employee.employeeId().value());

            json.put(
                    "username",
                    employee.username().value());

            json.put(
                    "nameEnglish",
                    employee.nameEnglish());

            json.put(
                    "nameArabic",
                    nullableJsonValue(employee.nameArabic()));

            json.put(
                    "correspondenceRole",
                    role == null
                            ? JSONObject.NULL
                            : role.name());

            JSONArray allRoles = new JSONArray();
            roles.stream().map(Enum::name).sorted().forEach(allRoles::put);
            json.put("correspondenceRoles", allRoles);

            JSONArray assignments = new JSONArray();
            for (ManagerWorkspaceView workspace : workspaces) {
                JSONObject item = new JSONObject();
                item.put("code", workspace.code());
                item.put("nameEnglish", workspace.nameEnglish());
                item.put("nameArabic", nullableJsonValue(workspace.nameArabic()));
                item.put("capacity", workspace.capacity());
                item.put("role", workspace.role().name());
                assignments.put(item);
            }
            json.put("managerWorkspaces", assignments);
            JSONArray secretaryWorkspaceList = new JSONArray();
            for (var unit : secretaryUnits) {
                secretaryWorkspaceList.put(new JSONObject()
                    .put("code", unit.code())
                    .put("nameEnglish", unit.nameEnglish())
                    .put("nameArabic", nullableJsonValue(unit.nameArabic())));
            }
            json.put("secretaryUnits", secretaryWorkspaceList);
            JSONArray delegateWorkspaceList = new JSONArray();
            for (var unit : delegationUnits) {
                delegateWorkspaceList.put(new JSONObject()
                    .put("code", unit.code())
                    .put("nameEnglish", unit.nameEnglish())
                    .put("nameArabic", nullableJsonValue(unit.nameArabic())));
            }
            json.put("delegationUnits", delegateWorkspaceList);
            json.put("tarasolAdministrator", tarasolAdministrator);

            json.put(
                    "department",
                    employee.department()
                            .<Object>map(
                                    CurrentEmployeeWebScript::toJson)
                            .orElse(JSONObject.NULL));

            return json;

        } catch (JSONException exception) {
            throw new WebScriptException(
                    "Unable to serialize current employee response.",
                    exception);
        }
    }

    private static JSONObject toJson(
            DepartmentDirectoryEntry department) {

        JSONObject json = new JSONObject();

        json.put("code", department.code().value());
        json.put("nameEnglish", department.nameEnglish());

        json.put(
                "nameArabic",
                nullableJsonValue(department.nameArabic()));

        return json;
    }

    private static Object nullableJsonValue(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static void writeError(
            WebScriptResponse response,
            int status,
            String code,
            String message)
            throws IOException {

        JSONObject error = new JSONObject();

        error.put("code", code);
        error.put("message", message);

        writeJson(response, status, error);
    }

    private static void writeJson(
            WebScriptResponse response,
            int status,
            JSONObject json)
            throws IOException {

        response.setStatus(status);
        response.setContentType(
                "application/json;charset=UTF-8");

        response.getWriter().write(json.toString());
    }
}