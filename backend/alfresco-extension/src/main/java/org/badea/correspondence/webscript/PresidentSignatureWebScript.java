package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.PresidentSignatureDrawing;
import org.badea.correspondence.application.PresidentSignatureStore;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** No endpoint ever returns the reusable signature's raw strokes. */
public final class PresidentSignatureWebScript extends AbstractWebScript {
    private final CorrespondenceActorRoleResolver roles;
    private final CurrentEmployeeService currentEmployee;
    private final PresidentSignatureStore signatures;
    private final boolean write;

    public PresidentSignatureWebScript(CorrespondenceActorRoleResolver roles,
            CurrentEmployeeService currentEmployee, PresidentSignatureStore signatures, boolean write) {
        this.roles = Objects.requireNonNull(roles);
        this.currentEmployee = Objects.requireNonNull(currentEmployee);
        this.signatures = Objects.requireNonNull(signatures);
        this.write = write;
    }

    @Override public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        try {
            if (roles.resolveCurrentUserRole() != CorrespondenceActorRole.PRESIDENT) {
                throw new CorrespondenceAuthorizationException("Only the President can manage the signature");
            }
            PresidentSignatureStore.Status status;
            if (write) {
                String raw = request.getContent() == null ? null : request.getContent().getContent();
                if (raw == null || raw.length() > 75000) throw new IllegalArgumentException("Invalid signature payload");
                JSONObject body = new JSONObject(raw);
                if (!body.getBoolean("authorizeReuse")) {
                    throw new IllegalArgumentException("Explicit authorization is required");
                }
                long revision = body.getLong("expectedRevision");
                PresidentSignatureDrawing drawing = PresidentSignatureDrawing.fromJson(body.getJSONObject("drawing").toString());
                String employeeId = currentEmployee.getCurrentEmployee().employeeId().value();
                status = signatures.register(drawing, employeeId, revision);
            } else {
                status = signatures.status();
            }
            JSONObject result = new JSONObject().put("configured", status.configured())
                    .put("revision", status.revision())
                    .put("updatedAt", status.updatedAt() == null ? JSONObject.NULL : status.updatedAt().toString());
            send(response, Status.STATUS_OK, result);
        } catch (CorrespondenceAuthorizationException unauthorized) {
            send(response, Status.STATUS_FORBIDDEN, new JSONObject().put("code", "PRESIDENT_ONLY"));
        } catch (IllegalStateException conflict) {
            send(response, 409, new JSONObject().put("code", "SIGNATURE_CONFLICT"));
        } catch (org.json.JSONException | IllegalArgumentException invalid) {
            send(response, Status.STATUS_BAD_REQUEST, new JSONObject().put("code", "INVALID_SIGNATURE"));
        }
    }

    private static void send(WebScriptResponse response, int code, JSONObject json) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json.toString());
    }
}
