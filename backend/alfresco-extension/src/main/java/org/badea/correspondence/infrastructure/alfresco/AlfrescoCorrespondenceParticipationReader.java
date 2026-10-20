package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CorrespondenceParticipation;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/** Read-only projection of persistent workflow actions for mailbox visibility. */
public final class AlfrescoCorrespondenceParticipationReader {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final QName TYPE = q("workflowAction");
    private final NodeService nodes;

    public AlfrescoCorrespondenceParticipationReader(NodeService nodes) {
        this.nodes = Objects.requireNonNull(nodes);
    }

    public List<CorrespondenceParticipation> read(NodeRef correspondence) {
        List<CorrespondenceParticipation> result = new ArrayList<>();
        for (ChildAssociationRef association : nodes.getChildAssocs(correspondence)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef action = association.getChildRef();
            if (!TYPE.equals(nodes.getType(action))) continue;
            Serializable when = nodes.getProperty(action, q("actedAt"));
            if (!(when instanceof Date date)) {
                throw new CorrespondenceRepositoryException("Invalid historical action timestamp");
            }
            String target = optional(action, "targetRole");
            String department = optional(action, "targetDepartmentCode");
            result.add(new CorrespondenceParticipation(
                    required(action, "actorEmployeeId"),
                    role(required(action, "actorRole")),
                    target == null ? null : role(target),
                    department == null ? null : new DepartmentCode(department),
                    date.toInstant()));
        }
        return List.copyOf(result);
    }

    private static CorrespondenceActorRole role(String value) {
        try { return CorrespondenceActorRole.valueOf(value); }
        catch (IllegalArgumentException exception) {
            throw new CorrespondenceRepositoryException("Invalid historical correspondence role", exception);
        }
    }

    private String required(NodeRef node, String name) {
        String value = optional(node, name);
        if (value == null || value.isBlank())
            throw new CorrespondenceRepositoryException("Missing historical action field: " + name);
        return value;
    }

    private String optional(NodeRef node, String name) {
        Serializable value = nodes.getProperty(node, q(name));
        if (value == null) return null;
        if (!(value instanceof String text))
            throw new CorrespondenceRepositoryException("Invalid historical action field: " + name);
        return text;
    }

    private static QName q(String name) { return QName.createQName(NS, name); }
}
