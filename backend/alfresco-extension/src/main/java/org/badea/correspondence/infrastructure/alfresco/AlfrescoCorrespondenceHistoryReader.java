package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;

/** Reads immutable bc:workflowAction children, never source PDF attachments. */
public final class AlfrescoCorrespondenceHistoryReader {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final QName TYPE_ACTION = q("workflowAction");
    private final NodeService nodes;
    private final CorrespondenceNodeLocator locator;

    public AlfrescoCorrespondenceHistoryReader(
            NodeService nodes, CorrespondenceNodeLocator locator) {
        this.nodes = Objects.requireNonNull(nodes);
        this.locator = Objects.requireNonNull(locator);
    }

    public List<ActionEntry> list(String technicalId) {
        NodeRef correspondence = locator.locate(technicalId);
        List<ActionEntry> entries = new ArrayList<>();
        for (ChildAssociationRef assoc : nodes.getChildAssocs(correspondence)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(assoc.getTypeQName())) continue;
            NodeRef child = assoc.getChildRef();
            if (!TYPE_ACTION.equals(nodes.getType(child))) continue;
            Serializable sequence = nodes.getProperty(child, q("actionSequence"));
            Serializable date = nodes.getProperty(child, q("actedAt"));
            if (!(sequence instanceof Number n) || n.longValue() < 1L
                    || !(date instanceof Date d)) {
                throw new CorrespondenceRepositoryException("Invalid workflow history action.");
            }
            entries.add(new ActionEntry(
                n.longValue(), required(child,"actionType"),
                required(child,"actorRole"), required(child,"actorNameEnglish"),
                optional(child,"actorNameArabic"), required(child,"fromState"),
                required(child,"toState"), d.toInstant(), optional(child,"explanation")));
        }
        entries.sort(Comparator.comparingLong(ActionEntry::sequence));
        for (int i=1; i<entries.size(); i++) {
            if (entries.get(i).sequence() == entries.get(i-1).sequence()) {
                throw new CorrespondenceRepositoryException("Duplicate workflow history sequence.");
            }
        }
        return List.copyOf(entries);
    }

    private String required(NodeRef node, String name) {
        String v = optional(node, name);
        if (v == null || v.isBlank()) {
            throw new CorrespondenceRepositoryException("Missing workflow action field: " + name);
        }
        return v;
    }

    private String optional(NodeRef node, String name) {
        Serializable v = nodes.getProperty(node, q(name));
        if (v == null) return null;
        if (!(v instanceof String s)) {
            throw new CorrespondenceRepositoryException("Invalid workflow action field: " + name);
        }
        return s;
    }

    private static QName q(String local) { return QName.createQName(NS, local); }

    public record ActionEntry(long sequence, String actionType, String actorRole,
            String actorNameEnglish, String actorNameArabic, String fromState,
            String toState, Instant actedAt, String explanation) {}
}
