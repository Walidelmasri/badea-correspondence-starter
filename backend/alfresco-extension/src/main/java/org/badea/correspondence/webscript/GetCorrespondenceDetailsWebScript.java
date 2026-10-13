package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;

import org.badea.correspondence.application.CorrespondenceDetails;
import org.badea.correspondence.application.CorrespondenceDetailsService;
import org.badea.correspondence.application.CorrespondenceDocument;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class GetCorrespondenceDetailsWebScript
        extends AbstractWebScript {

    private final CurrentEmployeeService currentEmployeeService;
    private final CorrespondenceActorRoleResolver actorRoleResolver;
    private final CorrespondenceDetailsService detailsService;

    public GetCorrespondenceDetailsWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver actorRoleResolver,
            CorrespondenceDetailsService detailsService) {

        this.currentEmployeeService =
                Objects.requireNonNull(currentEmployeeService);

        this.actorRoleResolver =
                Objects.requireNonNull(actorRoleResolver);

        this.detailsService =
                Objects.requireNonNull(detailsService);
    }

    @Override
    public void execute(
            WebScriptRequest request,
            WebScriptResponse response)
            throws IOException {

        try {
            String technicalId = readTechnicalId(request);

            CorrespondenceActorRole actorRole =
                    actorRoleResolver.resolveCurrentUserRole();

            EmployeeDirectoryEntry employee =
                    currentEmployeeService.getCurrentEmployee();

            DepartmentCode actorDepartmentCode =
                    employee.department()
                            .map(department -> department.code())
                            .orElse(null);

            CorrespondenceDetails details =
                    detailsService.getDetails(
                            technicalId,
                            actorRole,
                            actorDepartmentCode);

            writeJson(
                    response,
                    Status.STATUS_OK,
                    serialize(details));

        } catch (CurrentEmployeeNotFoundException exception) {

            writeError(
                    response,
                    Status.STATUS_NOT_FOUND,
                    "EMPLOYEE_NOT_FOUND",
                    "No active employee profile was found for the authenticated user.");

        } catch (EmployeeDirectoryException exception) {

            writeError(
                    response,
                    Status.STATUS_SERVICE_UNAVAILABLE,
                    "EMPLOYEE_DIRECTORY_UNAVAILABLE",
                    "The employee directory is currently unavailable.");

        } catch (CorrespondenceAuthorizationException exception) {

            writeError(
                    response,
                    Status.STATUS_FORBIDDEN,
                    "CORRESPONDENCE_FORBIDDEN",
                    "The authenticated user is not authorized to view this correspondence.");

        } catch (CorrespondenceRepositoryException exception) {

            writeError(
                    response,
                    Status.STATUS_INTERNAL_SERVER_ERROR,
                    "CORRESPONDENCE_REPOSITORY_ERROR",
                    "The correspondence details could not be loaded.");

        } catch (IllegalArgumentException exception) {

            writeError(
                    response,
                    Status.STATUS_BAD_REQUEST,
                    "INVALID_REQUEST",
                    exception.getMessage());
        }
    }

    private static JSONObject serialize(
            CorrespondenceDetails details) {

        JSONObject result = new JSONObject();

        result.put(
                "technicalId",
                details.technicalId());

        result.put(
                "state",
                details.state().name());

        result.put(
                "subject",
                details.subject());

        putNullable(
                result,
                "bodyText",
                details.bodyText());

        putNullable(
                result,
                "documentNumber",
                details.documentNumber());

        putNullable(
                result,
                "documentDate",
                details.documentDate() == null
                        ? null
                        : details.documentDate().toString());

        putNullable(
                result,
                "sourceText",
                details.sourceText());

        result.put(
                "originDepartmentCode",
                details.originDepartmentCode());

        result.put(
                "createdByNameEnglish",
                details.createdByNameEnglish());

        putNullable(
                result,
                "createdByNameArabic",
                details.createdByNameArabic());

        JSONArray documents = new JSONArray();

        for (CorrespondenceDocument document : details.documents()) {

            JSONObject item = new JSONObject();

            item.put(
                    "nodeId",
                    document.nodeId());

            item.put(
                    "filename",
                    document.filename());

            item.put(
                    "mimeType",
                    document.mimeType());

            item.put(
                    "sizeBytes",
                    document.sizeBytes());

            documents.put(item);
        }

        result.put(
                "documents",
                documents);

        return result;
    }

    private static void putNullable(
            JSONObject target,
            String property,
            String value) {

        target.put(
                property,
                value == null
                        ? JSONObject.NULL
                        : value);
    }

    private static String readTechnicalId(
            WebScriptRequest request) {

        String technicalId =
                request.getServiceMatch()
                        .getTemplateVars()
                        .get("technicalId");

        if (technicalId == null || technicalId.isBlank()) {

            throw new IllegalArgumentException(
                    "technicalId must not be blank");
        }

        return technicalId.trim();
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

    private static void writeError(
            WebScriptResponse response,
            int status,
            String code,
            String message)
            throws IOException {

        JSONObject result = new JSONObject();

        result.put("code", code);
        result.put("message", message);

        writeJson(
                response,
                status,
                result);
    }
}