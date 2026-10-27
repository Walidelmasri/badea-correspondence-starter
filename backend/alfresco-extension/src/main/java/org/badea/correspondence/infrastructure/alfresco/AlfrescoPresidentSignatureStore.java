package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.PresidentSignatureDrawing;
import org.badea.correspondence.application.PresidentSignatureStore;

/** One private repository node, not part of any correspondence's source PDF collection. */
public final class AlfrescoPresidentSignatureStore implements PresidentSignatureStore {
    private static final String URI = "http://www.badea.org/model/correspondence/1.0";
    private static final QName TYPE = q("presidentSignature");
    private static final QName REVISION = q("signatureRevision");
    private static final QName OWNER = q("signatureOwnerEmployeeId");
    private static final QName SAVED = q("signatureSavedAt");
    private static final QName REVIEW_OWNER = q("presidentReviewUpdatedByEmployeeId");
    private static final String NAME = ".badea-presidential-signature-private";
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

    @Override public Status status() {
        return AuthenticationUtil.runAsSystem(() -> {
            NodeRef node = locate();
            return node == null ? new Status(false, 0, null) : statusOf(node);
        });
    }

    @Override public Status register(PresidentSignatureDrawing drawing, String employeeId, long expectedRevision) {
        Objects.requireNonNull(drawing);
        if (employeeId == null || employeeId.isBlank() || expectedRevision < 0) {
            throw new IllegalArgumentException("Invalid signature registration");
        }
        return AuthenticationUtil.runAsSystem(() -> {
            NodeRef node = locate();
            long current = node == null ? 0 : revision(node);
            if (current != expectedRevision) {
                throw new IllegalStateException("Signature changed since it was loaded");
            }
            if (node != null && !employeeId.equals(nodes.getProperty(node, OWNER))) {
                // A new President's signature must be enrolled via a separate,
                // audited succession process, not by silently replacing this one.
                throw new IllegalStateException("Stored signature belongs to another President");
            }
            if (node == null) {
                NodeRef parent = folders.locateCorrespondenceRoot();
                node = nodes.createNode(parent, ContentModel.ASSOC_CONTAINS,
                        QName.createQName(URI, "presidentSignaturePrivate"), TYPE,
                        Map.<QName, Serializable>of(ContentModel.PROP_NAME, NAME)).getChildRef();
                // This reusable signing asset must not inherit the DMS site permissions.
                permissions.setInheritParentPermissions(node, false);
            }
            ContentWriter writer = content.getWriter(node, ContentModel.PROP_CONTENT, true);
            if (writer == null) throw new CorrespondenceRepositoryException("Signature writer unavailable");
            writer.setMimetype("application/json");
            writer.setEncoding("UTF-8");
            writer.putContent(drawing.toJson());
            nodes.setProperty(node, REVISION, current + 1);
            nodes.setProperty(node, OWNER, employeeId);
            nodes.setProperty(node, SAVED, Date.from(clock.instant()));
            return statusOf(node);
        });
    }

    @Override public Optional<PresidentSignatureDrawing> loadForReview(String correspondenceId) {
        Objects.requireNonNull(correspondenceId);
        return AuthenticationUtil.runAsSystem(() -> {
            NodeRef currentCorrespondence = correspondence.locate(correspondenceId);
            Object reviewer = nodes.getProperty(currentCorrespondence, REVIEW_OWNER);
            NodeRef node = locate();
            if (node == null || reviewer == null || !reviewer.toString().equals(nodes.getProperty(node, OWNER))) {
                return Optional.empty();
            }
            ContentReader reader = content.getReader(node, ContentModel.PROP_CONTENT);
            if (reader == null || !reader.exists()) return Optional.empty();
            return Optional.of(PresidentSignatureDrawing.fromJson(reader.getContentString()));
        });
    }

    private NodeRef locate() {
        return nodes.getChildByName(folders.locateCorrespondenceRoot(), ContentModel.ASSOC_CONTAINS, NAME);
    }

    private Status statusOf(NodeRef node) {
        Object at = nodes.getProperty(node, SAVED);
        return new Status(true, revision(node), at instanceof Date date ? date.toInstant() : null);
    }

    private long revision(NodeRef node) {
        Object stored = nodes.getProperty(node, REVISION);
        if (stored == null) return 0;
        if (!(stored instanceof Number number) || number.longValue() < 0)
            throw new CorrespondenceRepositoryException("Invalid signature revision");
        return number.longValue();
    }

    private static QName q(String name) { return QName.createQName(URI, name); }
}
