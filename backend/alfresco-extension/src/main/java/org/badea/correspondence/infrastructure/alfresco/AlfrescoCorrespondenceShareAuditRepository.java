package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CorrespondenceShareAuditRepository;

/** Append-only metadata children; does not rewrite source PDFs or workflow history. */
public final class AlfrescoCorrespondenceShareAuditRepository
        implements CorrespondenceShareAuditRepository {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final QName TYPE = q("shareAuditEvent");
    private final NodeService nodes;
    private final CorrespondenceNodeLocator locator;

    public AlfrescoCorrespondenceShareAuditRepository(NodeService nodes,
            CorrespondenceNodeLocator locator) {
        this.nodes = Objects.requireNonNull(nodes);
        this.locator = Objects.requireNonNull(locator);
    }

    @Override public void append(Entry entry) {
        Objects.requireNonNull(entry);
        NodeRef parent = locator.locate(entry.technicalId());
        String name = "share-event-" + UUID.randomUUID();
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_NAME, name);
        properties.put(q("shareEventKind"), Objects.requireNonNull(entry.event()));
        properties.put(q("shareEventDocumentNodeId"), Objects.requireNonNull(entry.nodeId()));
        properties.put(q("shareEventActorEmployeeId"), Objects.requireNonNull(entry.actorEmployeeId()));
        properties.put(q("shareEventAt"), new Date());
        if (entry.recipient() != null) properties.put(q("shareEventRecipient"), entry.recipient());
        if (entry.sharedId() != null) properties.put(q("shareEventSharedId"), entry.sharedId());
        if (entry.comment() != null && !entry.comment().isBlank())
            properties.put(q("shareEventComment"), entry.comment());
        ChildAssociationRef child = nodes.createNode(parent, ContentModel.ASSOC_CONTAINS,
                q(name), TYPE, properties);
        if (child == null || child.getChildRef() == null) {
            throw new CorrespondenceRepositoryException("Share audit event was not persisted.");
        }
    }
    private static QName q(String name) { return QName.createQName(NS, name); }
}
