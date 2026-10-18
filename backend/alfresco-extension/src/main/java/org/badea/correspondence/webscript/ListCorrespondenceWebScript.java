package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;

import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceDraftLister;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceMailboxLister;
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

public final class ListCorrespondenceWebScript
        extends AbstractWebScript {

    private final CurrentEmployeeService currentEmployeeService;
    private final CorrespondenceActorRoleResolver actorRoleResolver;
    private final AlfrescoCorrespondenceDraftLister draftLister;
    private final AlfrescoCorrespondenceMailboxLister mailboxLister;

    public ListCorrespondenceWebScript(
            CurrentEmployeeService currentEmployeeService,
            CorrespondenceActorRoleResolver actorRoleResolver,
            AlfrescoCorrespondenceDraftLister draftLister,
            AlfrescoCorrespondenceMailboxLister mailboxLister) {

        this.currentEmployeeService =
                Objects.requireNonNull(currentEmployeeService);

        this.actorRoleResolver =
                Objects.requireNonNull(actorRoleResolver);

        this.draftLister =
                Objects.requireNonNull(draftLister);
        this.mailboxLister = Objects.requireNonNull(mailboxLister);
    }

    @Override
    public void execute(
            WebScriptRequest request,
            WebScriptResponse response)
            throws IOException {

        try {
            CorrespondenceActorRole role =
                    actorRoleResolver.resolveCurrentUserRole();

            String box = request.getParameter("box");
            if (!"drafts".equals(box) && !"inbox".equals(box)
                    && !"sent".equals(box)) {
                throw new IllegalArgumentException("Unsupported correspondence box");
            }

            int offset = readInteger(
                    request.getParameter("offset"), 0, 0,
                    Integer.MAX_VALUE, "offset");
            int limit = readInteger(
                    request.getParameter("limit"), 25, 1, 100, "limit");

            EmployeeDirectoryEntry employee =
                    currentEmployeeService.getCurrentEmployee();
            DepartmentCode department = employee.department()
                    .map(entry -> entry.code()).orElse(null);

            JSONObject result;
            if ("drafts".equals(box)) {
                if (role != CorrespondenceActorRole.DIRECTOR || department == null) {
                    throw new CorrespondenceAuthorizationException(
                            "Director department required for drafts");
                }
                result = serialize(draftLister.list(department, offset, limit));
            } else {
                result = serializeMailbox(mailboxLister.list(
                        box, role, department, offset, limit));
            }
            writeJson(response, Status.STATUS_OK, result);

        } catch (CorrespondenceAuthorizationException exception) {

            writeError(
                    response,
                    Status.STATUS_FORBIDDEN,
                    "CORRESPONDENCE_FORBIDDEN",
                    "You are not authorized to view this correspondence mailbox.");

        } catch (CurrentEmployeeNotFoundException exception) {

            writeError(
                    response,
                    Status.STATUS_NOT_FOUND,
                    "EMPLOYEE_NOT_FOUND",
                    "No active employee profile was found.");

        } catch (EmployeeDirectoryException exception) {

            writeError(
                    response,
                    Status.STATUS_SERVICE_UNAVAILABLE,
                    "EMPLOYEE_DIRECTORY_UNAVAILABLE",
                    "The employee directory is unavailable.");

        } catch (CorrespondenceRepositoryException exception) {

            writeError(
                    response,
                    Status.STATUS_INTERNAL_SERVER_ERROR,
                    "CORRESPONDENCE_REPOSITORY_ERROR",
                    "The correspondence mailbox could not be loaded.");

        } catch (IllegalArgumentException exception) {

            writeError(
                    response,
                    Status.STATUS_BAD_REQUEST,
                    "INVALID_REQUEST",
                    exception.getMessage());
        }
    }

    private static int readInteger(
            String raw,
            int defaultValue,
            int minimum,
            int maximum,
            String fieldName) {

        if (raw == null) {
            return defaultValue;
        }

        try {
            int value = Integer.parseInt(raw.trim());

            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(
                        fieldName + " is outside the allowed range.");
            }

            return value;

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    fieldName + " must be an integer.");
        }
    }

    private static JSONObject serialize(
            AlfrescoCorrespondenceDraftLister.DraftPage page) {

        JSONObject result = new JSONObject();
        JSONArray items = new JSONArray();

        for (AlfrescoCorrespondenceDraftLister.DraftSummary draft
                : page.items()) {

            JSONObject item = new JSONObject();

            item.put("technicalId", draft.technicalId());
            item.put("subject", draft.subject());

            item.put(
                    "documentNumber",
                    draft.documentNumber() == null
                            ? JSONObject.NULL
                            : draft.documentNumber());

            item.put("createdAt", draft.createdAt().toString());
            item.put("modifiedAt", draft.modifiedAt().toString());

            items.put(item);
        }

        result.put("items", items);
        result.put("offset", page.offset());
        result.put("limit", page.limit());
        result.put("total", page.total());

        return result;
    }

    private static JSONObject serializeMailbox(
            AlfrescoCorrespondenceMailboxLister.MailboxPage page) {
        JSONObject result = new JSONObject();
        JSONArray items = new JSONArray();
        for (AlfrescoCorrespondenceMailboxLister.MailboxSummary summary : page.items()) {
            JSONObject item = new JSONObject();
            item.put("technicalId", summary.technicalId());
            item.put("subject", summary.subject());
            item.put("documentNumber", summary.documentNumber() == null
                    ? JSONObject.NULL : summary.documentNumber());
            item.put("state", summary.state());
            item.put("originDepartmentCode", summary.originDepartmentCode());
            item.put("senderName", summary.senderName());
            item.put("createdAt", summary.createdAt().toString());
            item.put("modifiedAt", summary.modifiedAt().toString());
            items.put(item);
        }
        result.put("items", items);
        result.put("offset", page.offset());
        result.put("limit", page.limit());
        result.put("total", page.total());
        return result;
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

        writeJson(response, status, result);
    }

    private static void writeJson(
            WebScriptResponse response,
            int status,
            JSONObject result)
            throws IOException {

        response.setStatus(status);

        response.setHeader(
                "Cache-Control",
                "no-store");

        response.setContentType(
                "application/json;charset=UTF-8");

        response.getWriter().write(result.toString());
    }
}