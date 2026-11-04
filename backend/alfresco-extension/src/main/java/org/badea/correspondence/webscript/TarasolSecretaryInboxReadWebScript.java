package org.badea.correspondence.webscript;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.application.CorrespondenceDocumentStream;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.organization.TarasolSecretaryInboxReadService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Secretary-only read projection using the same response shape as existing viewer APIs. */
public final class TarasolSecretaryInboxReadWebScript extends AbstractWebScript {
    private enum Mode { DETAILS, HISTORY, DOCUMENT }
    private final CurrentEmployeeService employees;
    private final TarasolSecretaryInboxReadService reader;
    private final Mode mode;

    public TarasolSecretaryInboxReadWebScript(CurrentEmployeeService employees,
            TarasolSecretaryInboxReadService reader, String mode) {
        this.employees = Objects.requireNonNull(employees);
        this.reader = Objects.requireNonNull(reader);
        this.mode = Mode.valueOf(Objects.requireNonNull(mode));
    }

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        CorrespondenceDocumentStream document = null;
        try {
            String unitCode = request.getParameter("unitCode");
            if (unitCode == null || !unitCode.matches("[0-9]{3}"))
                throw new IllegalArgumentException("Valid unitCode required");
            var vars = request.getServiceMatch().getTemplateVars();
            String technicalId = vars.get("technicalId");
            if (technicalId == null || technicalId.isBlank())
                throw new IllegalArgumentException("Technical ID required");
            var actor = employees.getCurrentEmployee();
            var unit = new DepartmentCode(unitCode);
            switch (mode) {
                case DETAILS -> {
                    respond(response, Status.STATUS_OK,
                            GetCorrespondenceDetailsWebScript.serialize(
                                    reader.details(actor, unit, technicalId)));
                    return;
                }
                case HISTORY -> {
                    JSONArray items = new JSONArray();
                    for (var action : reader.history(actor, unit, technicalId)) {
                        items.put(new JSONObject()
                            .put("sequence", action.sequence())
                            .put("actionType", action.actionType())
                            .put("actorRole", action.actorRole())
                            .put("actorNameEnglish", action.actorNameEnglish())
                            .put("actorNameArabic", nullable(action.actorNameArabic()))
                            .put("fromState", action.fromState())
                            .put("toState", action.toState())
                            .put("actedAt", action.actedAt().toString())
                            .put("explanation", nullable(action.explanation())));
                    }
                    respond(response, Status.STATUS_OK,
                            new JSONObject().put("items", items).put("total", items.length()));
                    return;
                }
                case DOCUMENT -> {
                    String nodeId = vars.get("nodeId");
                    if (nodeId == null || nodeId.isBlank())
                        throw new IllegalArgumentException("Document ID required");
                    document = reader.document(actor, unit, technicalId, nodeId);
                }
            }
        } catch (CorrespondenceAuthorizationException denied) {
            error(response, Status.STATUS_FORBIDDEN, "ACCESS_DENIED");
            return;
        } catch (CurrentEmployeeNotFoundException missing) {
            error(response, Status.STATUS_NOT_FOUND, "EMPLOYEE_NOT_FOUND");
            return;
        } catch (EmployeeDirectoryException unavailable) {
            error(response, Status.STATUS_SERVICE_UNAVAILABLE, "EMPLOYEE_DIRECTORY_UNAVAILABLE");
            return;
        } catch (CorrespondenceDocumentNotFoundException missing) {
            error(response, Status.STATUS_NOT_FOUND, "DOCUMENT_NOT_FOUND");
            return;
        } catch (CorrespondenceRepositoryException failed) {
            error(response, Status.STATUS_INTERNAL_SERVER_ERROR, "CORRESPONDENCE_REPOSITORY_ERROR");
            return;
        } catch (IllegalArgumentException invalid) {
            error(response, Status.STATUS_BAD_REQUEST, "INVALID_REQUEST");
            return;
        }
        // Authorization, the membership check and node ownership completed before streaming.
        response.setStatus(Status.STATUS_OK);
        response.setContentType(document.mimeType());
        response.setHeader("X-Content-Type-Options", "nosniff");
        String disposition = "application/pdf".equalsIgnoreCase(document.mimeType())
                ? "inline" : "attachment";
        String encodedFilename = URLEncoder.encode(document.filename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition", disposition
                + "; filename=\"document\"; filename*=UTF-8''" + encodedFilename);
        try (InputStream content = document.content()) {
            content.transferTo(response.getOutputStream());
        }
    }

    private static Object nullable(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static void error(WebScriptResponse response, int status, String code)
            throws IOException {
        respond(response, status, new JSONObject().put("code", code)
                .put("message", "Unable to retrieve secretary Inbox content."));
    }

    private static void respond(WebScriptResponse response, int status, JSONObject body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body.toString());
    }
}
