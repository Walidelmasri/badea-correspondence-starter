package org.badea.correspondence.webscript;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import org.badea.correspondence.application.CreateCorrespondenceCommand;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceRepository;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.DualManagerInitiationAuthorizer;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

import org.json.JSONException;
import org.json.JSONObject;

import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class CreateCorrespondenceWebScript
        extends AbstractWebScript {

    private final CurrentEmployeeService currentEmployeeService;

    private final CorrespondenceActorRoleResolver actorRoleResolver;

    private final AlfrescoCorrespondenceRepository correspondenceRepository;
    private final TarasolOrganizationReader organizationReader;

    public CreateCorrespondenceWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver actorRoleResolver,
            AlfrescoCorrespondenceRepository correspondenceRepository) {
        this(currentEmployeeService, actorRoleResolver, correspondenceRepository, null);
    }

    public CreateCorrespondenceWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver actorRoleResolver,
            AlfrescoCorrespondenceRepository correspondenceRepository,
            TarasolOrganizationReader organizationReader) {
        this.currentEmployeeService = Objects.requireNonNull(currentEmployeeService);
        this.actorRoleResolver = Objects.requireNonNull(actorRoleResolver);
        this.correspondenceRepository = Objects.requireNonNull(correspondenceRepository);
        this.organizationReader = organizationReader;
    }

    @Override
    public void execute(
            WebScriptRequest request,
            WebScriptResponse response)
            throws IOException {

        try {
            /*
             * Director requests retain their existing API contract.
             * Executive requests must carry a validated executiveCompose
             * object so none of the executive form fields are silently lost.
             * The role is derived from Alfresco authentication, never JSON.
             */
            CorrespondenceActorRole actorRole =
                    actorRoleResolver.resolveCurrentUserRole();

            JSONObject body =
                    readRequestBody(request);

            String executiveComposeJson = null;

            /*
             * The originating department and creator identity
             * always come from the authenticated employee.
             */
            EmployeeDirectoryEntry employee =
                    currentEmployeeService.getCurrentEmployee();
            var requestedWorkspace = request.getParameter("workspaceUnit");
            var workspace = requestedWorkspace == null ? null
                : actorRoleResolver.resolveWorkspace(requestedWorkspace, employee);
            if (workspace != null) actorRole = workspace.role();
            // The workspace role is authoritative; never validate only the
            // caller's old primary role when interpreting executive fields.
            if (actorRole == CorrespondenceActorRole.DIRECTOR) {
                if (body.has("executiveCompose")) {
                    // A selected Director workspace identifies the originating unit;
                    // neither a forged body role nor group membership alone is sufficient.
                    if (workspace == null) {
                        throw new CorrespondenceAuthorizationException("Select a managerial workspace");
                    }
                    DualManagerInitiationAuthorizer.requireAuthorized(
                        employee.employeeId(), workspace.unit(), actorRoleResolver, organizationReader);
                    executiveComposeJson = ExecutiveComposePayload.normalize(body);
                }
            } else {
                executiveComposeJson = ExecutiveComposePayload.normalize(body);
            }
            DepartmentDirectoryEntry department =
                    employee.department().orElse(null);
            if (workspace != null) {
                department = new DepartmentDirectoryEntry(workspace.unit(),
                    workspace.unit().value(), null);
            }

            if (department == null) {

                writeError(
                        response,
                        Status.STATUS_CONFLICT,
                        "EMPLOYEE_DEPARTMENT_REQUIRED",
                        "The authenticated employee does not have "
                                + "an active department assignment.");

                return;
            }

            CreateCorrespondenceCommand command =
                    new CreateCorrespondenceCommand(
                            department.code(),
                            requiredText(body, "subject"),
                            optionalText(body, "bodyText"),
                            optionalText(body, "documentNumber"),
                            optionalDate(body, "documentDate"),
                            optionalText(body, "sourceText"),
                            employee.employeeId(),
                            employee.nameEnglish(),
                            employee.nameArabic(),
                            actorRole,
                            executiveComposeJson);

            String technicalId =
                    correspondenceRepository.createDraft(command);

            JSONObject result = new JSONObject();

            result.put(
                    "technicalId",
                    technicalId);

            result.put(
                    "state",
                    CorrespondenceState.DRAFT.name());

            writeJson(
                    response,
                    Status.STATUS_CREATED,
                    result);

        } catch (CorrespondenceAuthorizationException exception) {

            writeError(
                    response,
                    Status.STATUS_FORBIDDEN,
                    "CORRESPONDENCE_FORBIDDEN",
                    "The authenticated user is not authorized "
                            + "to create correspondence drafts.");

        } catch (CurrentEmployeeNotFoundException exception) {

            writeError(
                    response,
                    Status.STATUS_NOT_FOUND,
                    "EMPLOYEE_NOT_FOUND",
                    "No active employee profile was found "
                            + "for the authenticated user.");

        } catch (org.badea.correspondence.directory.DepartmentDirectoryException exception) {
            writeError(response, Status.STATUS_SERVICE_UNAVAILABLE,
                    "ORGANIZATION_DIRECTORY_UNAVAILABLE", "The Tarasol directory is unavailable.");

        } catch (EmployeeDirectoryException exception) {

            writeError(
                    response,
                    Status.STATUS_SERVICE_UNAVAILABLE,
                    "EMPLOYEE_DIRECTORY_UNAVAILABLE",
                    "The employee directory is currently unavailable.");

        } catch (JSONException
                | DateTimeParseException
                | IllegalArgumentException exception) {

            writeError(
                    response,
                    Status.STATUS_BAD_REQUEST,
                    "INVALID_REQUEST",
                    exception.getMessage());

        } catch (CorrespondenceRepositoryException exception) {

            writeError(
                    response,
                    Status.STATUS_INTERNAL_SERVER_ERROR,
                    "CORRESPONDENCE_STORAGE_ERROR",
                    exception.getMessage());
        }
    }

    private static JSONObject readRequestBody(
            WebScriptRequest request)
            throws IOException {

        Content content =
                request.getContent();

        if (content == null) {

            throw new IllegalArgumentException(
                    "Request body is required.");
        }

        String raw =
                content.getContent();

        if (raw == null || raw.isBlank()) {

            throw new IllegalArgumentException(
                    "Request body is required.");
        }

        return new JSONObject(raw);
    }

    private static String requiredText(
            JSONObject body,
            String propertyName) {

        if (!body.has(propertyName)
                || body.isNull(propertyName)) {

            throw new IllegalArgumentException(
                    propertyName + " is required.");
        }

        String value =
                body.getString(propertyName).trim();

        if (value.isEmpty()) {

            throw new IllegalArgumentException(
                    propertyName + " must not be blank.");
        }

        return value;
    }

    private static String optionalText(
            JSONObject body,
            String propertyName) {

        if (!body.has(propertyName)
                || body.isNull(propertyName)) {

            return null;
        }

        String value =
                body.getString(propertyName).trim();

        return value.isEmpty()
                ? null
                : value;
    }

    private static LocalDate optionalDate(
            JSONObject body,
            String propertyName) {

        String value =
                optionalText(
                        body,
                        propertyName);

        return value == null
                ? null
                : LocalDate.parse(value);
    }

    private static void writeError(
            WebScriptResponse response,
            int status,
            String code,
            String message)
            throws IOException {

        JSONObject json = new JSONObject();

        json.put("code", code);
        json.put("message", message);

        writeJson(
                response,
                status,
                json);
    }

    private static void writeJson(
            WebScriptResponse response,
            int status,
            JSONObject json)
            throws IOException {

        response.setStatus(status);

        response.setContentType(
                "application/json;charset=UTF-8");

        response.getWriter().write(
                json.toString());
    }
}