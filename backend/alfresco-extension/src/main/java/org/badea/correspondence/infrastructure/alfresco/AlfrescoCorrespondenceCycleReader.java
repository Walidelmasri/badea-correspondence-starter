package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.util.Objects;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;

/**
 * Reads the active workflow round without mutating existing Alfresco records.
 * Correspondence and assignments created before cycles existed belong to round 1.
 * A future continuation must move only the parent's active pointer; previous
 * assignment children stay immutable, visible and auditable.
 */
public final class AlfrescoCorrespondenceCycleReader {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    public static final QName CURRENT = QName.createQName(NS, "currentWorkflowCycle");
    public static final QName ASSIGNMENT = QName.createQName(NS, "assignmentCycle");
    private final NodeService nodes;

    public AlfrescoCorrespondenceCycleReader(NodeService nodes) {
        this.nodes = Objects.requireNonNull(nodes);
    }

    public long current(NodeRef correspondence) {
        return number(nodes.getProperty(correspondence, CURRENT), "currentWorkflowCycle");
    }

    public long assignment(NodeRef assignment) {
        return number(nodes.getProperty(assignment, ASSIGNMENT), "assignmentCycle");
    }

    public boolean isCurrent(NodeRef correspondence, NodeRef assignment) {
        return current(correspondence) == assignment(assignment);
    }

    public static long number(Serializable raw, String name) {
        if (raw == null) return 1L; // Legacy correspondences and assignments.
        if (!(raw instanceof Number value) || value.longValue() < 1L
                || (value instanceof java.math.BigDecimal decimal
                    && decimal.stripTrailingZeros().scale() > 0)) {
            throw new CorrespondenceRepositoryException("Invalid workflow cycle " + name);
        }
        return value.longValue();
    }
}
