package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.DepartmentAssignment;
import org.badea.correspondence.application.DepartmentAssignmentStatus;
import org.badea.correspondence.application.DepartmentRecipientType;
import org.badea.correspondence.directory.DepartmentCode;

/** Single strict projection used by authorization, mailboxes, and director workflow. */
public final class AlfrescoDepartmentAssignmentReader {
    public static final String NS = "http://www.badea.org/model/correspondence/1.0";
    public static final QName TYPE = q("departmentAssignment");
    private final NodeService nodes;

    public AlfrescoDepartmentAssignmentReader(NodeService nodes) {
        this.nodes = Objects.requireNonNull(nodes);
    }

    public List<DepartmentAssignment> read(NodeRef parent) {
        String technicalId = str(parent, "technicalId", true);
        List<DepartmentAssignment> result = new ArrayList<>();
        for (ChildAssociationRef child : nodes.getChildAssocs(parent)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(child.getTypeQName())) continue;
            NodeRef node = child.getChildRef();
            if (!TYPE.equals(nodes.getType(node))) continue;
            try {
                String id = str(node, "assignmentId", true);
                String dept = str(node, "assignmentDepartmentCode", true);
                var kind = DepartmentRecipientType.valueOf(str(node, "assignmentRecipientType", true));
                var status = DepartmentAssignmentStatus.valueOf(str(node, "assignmentStatus", true));
                String by = str(node, "assignmentByEmployeeId", true);
                Instant at = time(node, "assignmentAt", true);
                String doneBy = str(node, "assignmentCompletedByEmployeeId", false);
                Instant doneAt = time(node, "assignmentCompletedAt", false);
                String response = str(node, "assignmentResponseText", false);
                result.add(new DepartmentAssignment(id, technicalId, new DepartmentCode(dept),
                        kind, status, by, at, doneBy, doneAt, response));
            } catch (RuntimeException invalid) {
                throw new CorrespondenceRepositoryException("Invalid persisted departmental assignment", invalid);
            }
        }
        return List.copyOf(result);
    }

    private String str(NodeRef node, String key, boolean required) {
        Serializable raw = nodes.getProperty(node, q(key));
        if (raw == null && !required) return null;
        if (!(raw instanceof String s) || s.isBlank())
            throw new CorrespondenceRepositoryException("Invalid assignment property " + key);
        return s;
    }
    private Instant time(NodeRef node, String key, boolean required) {
        Serializable raw = nodes.getProperty(node, q(key));
        if (raw == null && !required) return null;
        if (!(raw instanceof Date date))
            throw new CorrespondenceRepositoryException("Invalid assignment timestamp " + key);
        return date.toInstant();
    }
    public static QName q(String name) { return QName.createQName(NS, name); }
}
