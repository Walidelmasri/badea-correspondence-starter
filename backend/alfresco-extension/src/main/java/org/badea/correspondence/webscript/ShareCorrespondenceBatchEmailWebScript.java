package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.application.CorrespondenceShareService;
import org.badea.correspondence.application.CurrentEmployeeNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceRepositoryException;
import org.badea.correspondence.infrastructure.mail.SmtpShareMailSender.MailSubmissionException;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** One authorized batch and one SMTP message; shares must never be created from arbitrary node IDs. */
public final class ShareCorrespondenceBatchEmailWebScript extends AbstractWebScript {
    private final CurrentEmployeeService current;
    private final CorrespondenceActorRoleResolver roles;
    private final CorrespondenceShareService shares;

    public ShareCorrespondenceBatchEmailWebScript(CurrentEmployeeService current,
            CorrespondenceActorRoleResolver roles, CorrespondenceShareService shares) {
        this.current = Objects.requireNonNull(current);
        this.roles = Objects.requireNonNull(roles);
        this.shares = Objects.requireNonNull(shares);
    }

    @Override public void execute(WebScriptRequest req, WebScriptResponse resp) throws IOException {
        try {
            String technicalId = req.getServiceMatch().getTemplateVars().get("technicalId");
            if (req.getContent() == null) throw new IllegalArgumentException("Missing body");
            String raw = req.getContent().getContent();
            if (raw == null || raw.length() > 6000) throw new IllegalArgumentException("Invalid body");
            JSONObject body = new JSONObject(raw);
            if (body.length() != 3 || !body.has("nodeIds") || !body.has("recipientUsername")
                    || !body.has("message")) throw new IllegalArgumentException("Invalid fields");
            JSONArray ids = body.getJSONArray("nodeIds");
            if (ids.length() < 1 || ids.length() > 10) throw new IllegalArgumentException("Invalid count");
            List<String> nodeIds = new ArrayList<>();
            for (int i = 0; i < ids.length(); i++) nodeIds.add(ids.getString(i));
            var employee = current.getCurrentEmployee();
            var role = roles.resolveCurrentUserRole();
            DepartmentCode department = employee.department().map(d -> d.code()).orElse(null);
            var result = shares.sendBatch(technicalId, nodeIds, role, department,
                    employee.employeeId().value(), employee.nameEnglish(),
                    body.getString("recipientUsername"),
                    body.isNull("message") ? null : body.getString("message"));
            JSONArray links = new JSONArray();
            for (int i = 0; i < result.links().size(); i++) {
                var link = result.links().get(i);
                links.put(new JSONObject().put("nodeId", nodeIds.get(i))
                        .put("sharedId", link.sharedId())
                        .put("url", link.url()).put("filename", link.filename()));
            }
            send(resp, Status.STATUS_OK, new JSONObject().put("status", "submitted")
                    .put("recipient", result.recipient()).put("recipientName", result.recipientName())
                    .put("links", links));
        } catch (CorrespondenceAuthorizationException | AccessDeniedException exception) {
            error(resp, Status.STATUS_FORBIDDEN, "CORRESPONDENCE_FORBIDDEN");
        } catch (CorrespondenceDocumentNotFoundException | CurrentEmployeeNotFoundException exception) {
            error(resp, Status.STATUS_NOT_FOUND, "NOT_FOUND");
        } catch (EmployeeDirectoryException exception) {
            error(resp, Status.STATUS_SERVICE_UNAVAILABLE, "EMPLOYEE_DIRECTORY_UNAVAILABLE");
        } catch (MailSubmissionException exception) {
            error(resp, Status.STATUS_SERVICE_UNAVAILABLE, "EMAIL_SUBMISSION_FAILED_OR_UNCERTAIN");
        } catch (JSONException | IllegalArgumentException exception) {
            error(resp, Status.STATUS_BAD_REQUEST, "INVALID_REQUEST");
        } catch (CorrespondenceRepositoryException exception) {
            error(resp, Status.STATUS_INTERNAL_SERVER_ERROR, "CORRESPONDENCE_REPOSITORY_ERROR");
        } catch (IllegalStateException exception) {
            error(resp, 409, "SHARE_LINK_STATE_ERROR");
        }
    }

    private static void error(WebScriptResponse response, int status, String code) throws IOException {
        send(response, status, new JSONObject().put("code", code));
    }
    private static void send(WebScriptResponse response, int status, JSONObject value) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status);
        response.getWriter().write(value.toString());
    }
}
