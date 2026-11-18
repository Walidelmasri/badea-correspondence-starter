package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.PresidentSignatureDrawing;
import org.badea.correspondence.application.PresidentSignatureStore;
import org.json.JSONObject;

/**
 * Independent, privately stored signature slots and append-only audit records.
 * The legacy single signature is intentionally NOT used or migrated: it belongs to
 * a previous test owner and may only be retired by a targeted administrator action.
 */
public final class AlfrescoPresidentSignatureStore implements PresidentSignatureStore {
    private static final String URI = "http://www.badea.org/model/correspondence/1.0";
    private static final QName TYPE = q("presidentSignature");
    private static final QName REVISION = q("signatureRevision");
    private static final QName OWNER = q("signatureOwnerEmployeeId");
    private static final QName SAVED = q("signatureSavedAt");
    private static final QName ACTIVE = q("signatureConfigured");
    private static final QName REVIEW_OWNER = q("presidentReviewUpdatedByEmployeeId");
    private static final String SLOT_PREFIX = ".badea-presidential-signature-slot-";
    private static final String FROZEN_PREFIX = ".badea-presidential-review-signature-r";
    private final NodeService nodes;
    private final ContentService content;
    private final PermissionService permissions;
    private final CorrespondenceFolderLocator folders;
    private final CorrespondenceNodeLocator correspondence;
    private final Clock clock;

    public AlfrescoPresidentSignatureStore(NodeService nodes, ContentService content,
            PermissionService permissions, CorrespondenceFolderLocator folders,
            CorrespondenceNodeLocator correspondence, Clock clock) {
        this.nodes = Objects.requireNonNull(nodes);
        this.content = Objects.requireNonNull(content);
        this.permissions = Objects.requireNonNull(permissions);
        this.folders = Objects.requireNonNull(folders);
        this.correspondence = Objects.requireNonNull(correspondence);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override public Status status() { return status(1); }
    @Override public Status status(int slot) {
        checkSlot(slot);
        return AuthenticationUtil.runAsSystem(() -> {
            NodeRef node = locateSlot(slot);
            return node == null ? new Status(false, 0, null) : statusOf(node);
        });
    }

    @Override public Status register(PresidentSignatureDrawing drawing, String actor, long revision) {
        return register(1, drawing, actor, revision);
    }
    @Override public Status register(int slot, PresidentSignatureDrawing drawing,
            String actor, long expectedRevision) {
        checkSlot(slot);
        Objects.requireNonNull(drawing);
        checkActor(actor, expectedRevision);
        return AuthenticationUtil.runAsSystem(() -> {
            NodeRef node = locateSlot(slot);
            long current = node == null ? 0 : revision(node);
            if (current != expectedRevision) throw new IllegalStateException("Signature changed since loaded");
            String priorHash = node == null || !statusOf(node).configured() ? null
                    : hash(readDrawing(node).orElseThrow().toJson());
            if (node == null) node = newPrivateNode(folders.locateCorrespondenceRoot(),
                    SLOT_PREFIX + slot + "-private", TYPE);
            String json = drawing.toJson();
            write(node, json);
            nodes.setProperty(node, ACTIVE, true);
            nodes.setProperty(node, REVISION, current + 1);
            nodes.setProperty(node, OWNER, actor);
            nodes.setProperty(node, SAVED, Date.from(clock.instant()));
            appendAudit(slot, current + 1, current == 0 ? "CREATED" : "REPLACED",
                    actor, priorHash, hash(json));
            return statusOf(node);
        });
    }

    @Override public Status deactivate(String actor, long revision) {
        return deactivate(1, actor, revision);
    }
    @Override public Status deactivate(int slot, String actor, long expectedRevision) {
        checkSlot(slot);
        checkActor(actor, expectedRevision);
        return AuthenticationUtil.runAsSystem(() -> {
            NodeRef node = locateSlot(slot);
            if (node == null || revision(node) != expectedRevision || !statusOf(node).configured()) {
                throw new IllegalStateException("Signature changed or is inactive");
            }
            String priorHash = hash(readDrawing(node).orElseThrow().toJson());
            write(node, "{}");
            nodes.setProperty(node, ACTIVE, false);
            nodes.setProperty(node, REVISION, expectedRevision + 1);
            nodes.setProperty(node, OWNER, actor);
            nodes.setProperty(node, SAVED, Date.from(clock.instant()));
            appendAudit(slot, expectedRevision + 1, "REMOVED", actor, priorHash, null);
            return statusOf(node);
        });
    }

    @Override public Optional<PresidentSignatureDrawing> previewForOwner(String actor) {
        return previewForOwner(1, actor);
    }
    @Override public Optional<PresidentSignatureDrawing> previewForOwner(int slot, String actor) {
        checkSlot(slot);
        if (actor == null || actor.isBlank()) return Optional.empty();
        // The Web Script has already verified the live PRESIDENT role. The last
        // editor is recorded for audit, not treated as a permanent owner lock.
        return AuthenticationUtil.runAsSystem(() -> {
            NodeRef node = locateSlot(slot);
            return node == null || !statusOf(node).configured() ? Optional.empty() : readDrawing(node);
        });
    }

    @Override public List<AuditEntry> audit(int slot) {
        checkSlot(slot);
        return AuthenticationUtil.runAsSystem(() -> {
            List<AuditEntry> events = new ArrayList<>();
            for (ChildAssociationRef association : nodes.getChildAssocs(folders.locateCorrespondenceRoot())) {
                NodeRef child = association.getChildRef();
                String name = (String) nodes.getProperty(child, ContentModel.PROP_NAME);
                if (name == null || !name.startsWith(SLOT_PREFIX + slot + "-audit-r")) continue;
                ContentReader reader = content.getReader(child, ContentModel.PROP_CONTENT);
                if (reader == null || !reader.exists()) throw new CorrespondenceRepositoryException("Missing signature audit event");
                JSONObject event = new JSONObject(reader.getContentString());
                events.add(new AuditEntry(slot, event.getLong("revision"), event.getString("action"),
                        event.getString("actorEmployeeId"), Instant.parse(event.getString("at")),
                        event.optString("previousSha256", null), event.optString("newSha256", null)));
            }
            events.sort((a, b) -> Long.compare(b.revision(), a.revision()));
            return List.copyOf(events);
        });
    }

    @Override public void freezeForReview(String technicalId, String actor,
            long reviewRevision, int slot, long signatureRevision) {
        checkSlot(slot);
        checkActor(actor, signatureRevision);
        if (reviewRevision < 1) throw new IllegalArgumentException("Review revision required");
        AuthenticationUtil.runAsSystem(() -> {
            NodeRef caseNode = correspondence.locate(technicalId);
            String reviewOwner = (String) nodes.getProperty(caseNode, REVIEW_OWNER);
            if (!actor.equals(reviewOwner)) throw new IllegalStateException("President review owner changed");
            String name = FROZEN_PREFIX + reviewRevision;
            if (nodes.getChildByName(caseNode, ContentModel.ASSOC_CONTAINS, name) != null) {
                throw new IllegalStateException("Signature already frozen for this review");
            }
            NodeRef current = locateSlot(slot);
            if (current == null || !statusOf(current).configured() || revision(current) != signatureRevision) {
                throw new IllegalStateException("Selected signature changed; reload and select again");
            }
            String drawingJson = readDrawing(current).orElseThrow().toJson();
            JSONObject payload = new JSONObject()
                    .put("schemaVersion", 1).put("slot", slot)
                    .put("signatureRevision", signatureRevision).put("reviewRevision", reviewRevision)
                    .put("actorEmployeeId", actor).put("frozenAt", clock.instant().toString())
                    .put("sha256", hash(drawingJson)).put("drawing", new JSONObject(drawingJson));
            NodeRef frozen = newPrivateNode(caseNode, name, TYPE);
            write(frozen, payload.toString());
            nodes.setProperty(frozen, ACTIVE, true);
            nodes.setProperty(frozen, OWNER, actor);
            nodes.setProperty(frozen, REVISION, signatureRevision);
            nodes.setProperty(frozen, SAVED, Date.from(clock.instant()));
            return null;
        });
    }

    @Override public Optional<PresidentSignatureDrawing> loadForReview(String technicalId) {
        return AuthenticationUtil.runAsSystem(() -> {
            NodeRef current = correspondence.locate(technicalId);
            Object property = nodes.getProperty(current, q("presidentReviewRevision"));
            long revision = property instanceof Number n ? n.longValue() : 0;
            return loadFrozen(technicalId, revision);
        });
    }
    @Override public Optional<PresidentSignatureDrawing> loadForReview(String technicalId, long reviewRevision) {
        return AuthenticationUtil.runAsSystem(() -> loadFrozen(technicalId, reviewRevision));
    }
    private Optional<PresidentSignatureDrawing> loadFrozen(String technicalId, long reviewRevision) {
        if (reviewRevision < 1) return Optional.empty();
        NodeRef node = correspondence.locate(technicalId);
        NodeRef frozen = nodes.getChildByName(node, ContentModel.ASSOC_CONTAINS,
                FROZEN_PREFIX + reviewRevision);
        if (frozen == null) return Optional.empty();
        ContentReader reader = content.getReader(frozen, ContentModel.PROP_CONTENT);
        if (reader == null || !reader.exists()) throw new CorrespondenceRepositoryException("Frozen signature missing");
        JSONObject json = new JSONObject(reader.getContentString());
        if (json.getLong("reviewRevision") != reviewRevision
                || !Objects.equals(json.getString("actorEmployeeId"), nodes.getProperty(node, REVIEW_OWNER))) {
            throw new CorrespondenceRepositoryException("Frozen signature does not match approved review");
        }
        String drawing = PresidentSignatureDrawing.fromJson(json.getJSONObject("drawing").toString()).toJson();
        if (!hash(drawing).equals(json.getString("sha256"))) {
            throw new CorrespondenceRepositoryException("Frozen signature hash mismatch");
        }
        return Optional.of(PresidentSignatureDrawing.fromJson(drawing));
    }

    private void appendAudit(int slot, long revision, String action, String actor,
            String priorHash, String newHash) {
        String name = SLOT_PREFIX + slot + "-audit-r" + revision;
        NodeRef audit = newPrivateNode(folders.locateCorrespondenceRoot(), name, ContentModel.TYPE_CONTENT);
        JSONObject event = new JSONObject().put("schemaVersion", 1).put("slot", slot)
                .put("revision", revision).put("action", action)
                .put("actorEmployeeId", actor).put("at", clock.instant().toString());
        if (priorHash != null) event.put("previousSha256", priorHash);
        if (newHash != null) event.put("newSha256", newHash);
        write(audit, event.toString());
    }

    private NodeRef newPrivateNode(NodeRef parent, String name, QName type) {
        NodeRef result = nodes.createNode(parent, ContentModel.ASSOC_CONTAINS,
                QName.createQName(URI, name), type,
                Map.<QName, Serializable>of(ContentModel.PROP_NAME, name)).getChildRef();
        permissions.setInheritParentPermissions(result, false);
        return result;
    }
    private NodeRef locateSlot(int slot) {
        return nodes.getChildByName(folders.locateCorrespondenceRoot(),
                ContentModel.ASSOC_CONTAINS, SLOT_PREFIX + slot + "-private");
    }
    private void write(NodeRef node, String json) {
        ContentWriter writer = content.getWriter(node, ContentModel.PROP_CONTENT, true);
        if (writer == null) throw new CorrespondenceRepositoryException("Signature content writer unavailable");
        writer.setMimetype("application/json");
        writer.setEncoding("UTF-8");
        writer.putContent(json);
    }
    private Optional<PresidentSignatureDrawing> readDrawing(NodeRef node) {
        ContentReader reader = content.getReader(node, ContentModel.PROP_CONTENT);
        if (reader == null || !reader.exists()) return Optional.empty();
        return Optional.of(PresidentSignatureDrawing.fromJson(reader.getContentString()));
    }
    private Status statusOf(NodeRef node) {
        Object at = nodes.getProperty(node, SAVED);
        return new Status(Boolean.TRUE.equals(nodes.getProperty(node, ACTIVE)), revision(node),
                at instanceof Date date ? date.toInstant() : null);
    }
    private long revision(NodeRef node) {
        Object value = nodes.getProperty(node, REVISION);
        if (value == null) return 0;
        if (!(value instanceof Number number) || number.longValue() < 0)
            throw new CorrespondenceRepositoryException("Invalid signature revision");
        return number.longValue();
    }
    private static String hash(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
    private static void checkSlot(int slot) {
        if (slot != 1 && slot != 2) throw new IllegalArgumentException("Invalid signature slot");
    }
    private static void checkActor(String employeeId, long revision) {
        if (employeeId == null || employeeId.isBlank() || revision < 0)
            throw new IllegalArgumentException("Invalid signature actor or revision");
    }
    private static QName q(String name) { return QName.createQName(URI, name); }
}
