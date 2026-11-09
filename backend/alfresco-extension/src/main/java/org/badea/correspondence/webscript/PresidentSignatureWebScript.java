package org.badea.correspondence.webscript;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;
import javax.imageio.ImageIO;
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

/** Only the President can manage this signature; previews contain raster pixels, never raw strokes. */
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
            if (request.getParameter("workspaceUnit") != null) {
                throw new CorrespondenceAuthorizationException("President signing has no managerial workspace");
            }
            if (roles.resolveCurrentUserRole() != CorrespondenceActorRole.PRESIDENT) {
                throw new CorrespondenceAuthorizationException("Only the President can manage the signature");
            }
            String employeeId = currentEmployee.getCurrentEmployee().employeeId().value();
            PresidentSignatureStore.Status status;
            if (write) {
                String raw = request.getContent() == null ? null : request.getContent().getContent();
                if (raw == null || raw.length() > 75000) throw new IllegalArgumentException("Invalid signature payload");
                JSONObject body = new JSONObject(raw);
                long revision = body.getLong("expectedRevision");
                if ("deactivate".equals(body.optString("action"))) {
                    status = signatures.deactivate(employeeId, revision);
                } else {
                    if (!body.getBoolean("authorizeReuse")) {
                        throw new IllegalArgumentException("Explicit authorization is required");
                    }
                    PresidentSignatureDrawing drawing = PresidentSignatureDrawing.fromJson(body.getJSONObject("drawing").toString());
                    status = signatures.register(drawing, employeeId, revision);
                }
            } else {
                status = signatures.status();
            }
            Optional<PresidentSignatureDrawing> ownSignature = status.configured()
                    ? signatures.previewForOwner(employeeId) : Optional.empty();
            JSONObject result = new JSONObject().put("configured", status.configured())
                    .put("revision", status.revision())
                    .put("updatedAt", status.updatedAt() == null ? JSONObject.NULL : status.updatedAt().toString())
                    .put("previewDataUrl", ownSignature.isPresent()
                            ? renderPreview(ownSignature.get()) : JSONObject.NULL);
            send(response, Status.STATUS_OK, result);
        } catch (CorrespondenceAuthorizationException unauthorized) {
            send(response, Status.STATUS_FORBIDDEN, new JSONObject().put("code", "PRESIDENT_ONLY"));
        } catch (IllegalStateException conflict) {
            send(response, 409, new JSONObject().put("code", "SIGNATURE_CONFLICT"));
        } catch (org.json.JSONException | IllegalArgumentException invalid) {
            send(response, Status.STATUS_BAD_REQUEST, new JSONObject().put("code", "INVALID_SIGNATURE"));
        }
    }

    private static String renderPreview(PresidentSignatureDrawing signature) throws IOException {
        int width = 520, height = 135;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(20, 46, 57));
            graphics.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (var stroke : signature.strokes()) {
                var path = new java.awt.geom.Path2D.Float();
                boolean first = true;
                for (var point : stroke) {
                    float x = point.x() * width;
                    float y = point.y() * height;
                    if (first) { path.moveTo(x, y); first = false; }
                    else path.lineTo(x, y);
                }
                graphics.draw(path);
            }
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder unavailable");
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private static void send(WebScriptResponse response, int code, JSONObject json) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json.toString());
    }
}
