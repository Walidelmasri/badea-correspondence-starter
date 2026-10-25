package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import org.json.JSONObject;
import org.json.JSONException;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Authenticated, correspondence-authorized QuickShare creation / SMTP email / revoke. */
public final class ShareCorrespondenceDocumentWebScript extends AbstractWebScript {
    private static final Log LOG = LogFactory.getLog(ShareCorrespondenceDocumentWebScript.class);
    private final CurrentEmployeeService employees;
    private final CorrespondenceActorRoleResolver roles;
    private final CorrespondenceShareService shares;
    private final String operation;

    public ShareCorrespondenceDocumentWebScript(CurrentEmployeeService employees,
            CorrespondenceActorRoleResolver roles, CorrespondenceShareService shares,
            String operation) {
        this.employees = Objects.requireNonNull(employees);
        this.roles = Objects.requireNonNull(roles);
        this.shares = Objects.requireNonNull(shares);
        if (!java.util.Set.of("link", "existing", "email", "revoke").contains(operation))
            throw new IllegalArgumentException("Unknown share operation");
        this.operation = operation;
    }

    @Override public void execute(WebScriptRequest req, WebScriptResponse resp) throws IOException {
        try {
            String id = req.getServiceMatch().getTemplateVars().get("technicalId");
            String nodeId = req.getServiceMatch().getTemplateVars().get("nodeId");
            var employee = employees.getCurrentEmployee();
            var role = roles.resolveCurrentUserRole();
            DepartmentCode department = employee.department().map(d -> d.code()).orElse(null);
            String employeeId = employee.employeeId().value();
            JSONObject body = new JSONObject();
            if ("email".equals(operation)) {
                if (req.getContent() == null || req.getContent().getContent() == null) {
                    throw new IllegalArgumentException("Email request body is required.");
                }
                String raw = req.getContent().getContent();
                if (raw.length() > 6000) throw new IllegalArgumentException("Email request is too large.");
                body = new JSONObject(raw);
                if (body.length() != 2 || !body.has("recipientUsername") || !body.has("message")) {
                    throw new IllegalArgumentException("Only recipientUsername and message are accepted.");
                }
            }
            JSONObject result = new JSONObject();
            switch (operation) {
                case "existing" -> {
                    var link = shares.existingLink(id, nodeId, role, department, employeeId);
                    result.put("sharedId", link.sharedId()).put("url", link.url())
                            .put("filename", link.filename());
                }
                case "link" -> {
                    var link = shares.createLink(id, nodeId, role, department, employeeId);
                    result.put("sharedId", link.sharedId()).put("url", link.url())
                            .put("filename", link.filename());
                    LOG.info("BADEA_SHARE_LINK_CREATED employee=" + employeeId
                            + " correspondence=" + id + " document=" + nodeId);
                }
                case "email" -> {
                    var sent = shares.send(id, nodeId, role, department, employeeId,
                            employee.nameEnglish(), body.getString("recipientUsername"),
                            body.isNull("message") ? null : body.getString("message"));
                    result.put("status", "submitted").put("recipient", sent.recipient())
                            .put("recipientName", sent.recipientName())
                            .put("sharedId", sent.link().sharedId())
                            .put("url", sent.link().url());
                    LOG.info("BADEA_SHARE_EMAIL_SUBMITTED employee=" + employeeId
                            + " recipient=" + sent.recipient() + " correspondence=" + id
                            + " document=" + nodeId);
                }
                case "revoke" -> {
                    shares.revoke(id, nodeId, role, department, employeeId);
                    result.put("status", "revoked");
                    LOG.info("BADEA_SHARE_LINK_REVOKED employee=" + employeeId
                            + " correspondence=" + id + " document=" + nodeId);
                }
                default -> throw new IllegalStateException("Unexpected operation");
            }
            send(resp, Status.STATUS_OK, result);
        } catch (CorrespondenceAuthorizationException | AccessDeniedException exception) {
            error(resp, Status.STATUS_FORBIDDEN, "CORRESPONDENCE_FORBIDDEN");
        } catch (CorrespondenceDocumentNotFoundException | CurrentEmployeeNotFoundException exception) {
            error(resp, Status.STATUS_NOT_FOUND, "NOT_FOUND");
        } catch (EmployeeDirectoryException exception) {
            error(resp, Status.STATUS_SERVICE_UNAVAILABLE, "EMPLOYEE_DIRECTORY_UNAVAILABLE");
        } catch (MailSubmissionException exception) {
            // A timeout can occur after SMTP accepted DATA: never suggest automatic retry.
            error(resp, Status.STATUS_SERVICE_UNAVAILABLE, "EMAIL_SUBMISSION_FAILED_OR_UNCERTAIN");
        } catch (JSONException | IllegalArgumentException exception) {
            error(resp, Status.STATUS_BAD_REQUEST, "INVALID_REQUEST");
        } catch (CorrespondenceRepositoryException exception) {
            error(resp, Status.STATUS_INTERNAL_SERVER_ERROR, "CORRESPONDENCE_REPOSITORY_ERROR");
        } catch (IllegalStateException exception) {
            error(resp, 409, "SHARE_LINK_STATE_ERROR");
        }
    }

    private static void error(WebScriptResponse resp, int status, String code) throws IOException {
        send(resp, status, new JSONObject().put("code", code));
    }
    private static void send(WebScriptResponse resp, int status, JSONObject value) throws IOException {
        resp.setStatus(status);
        resp.setHeader("Cache-Control", "private, no-store");
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(value.toString());
    }
}
