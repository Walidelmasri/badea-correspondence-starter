package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/** Only read markers, never official workflow state. Monotonic per-employee action sequence. */
public final class AlfrescoCorrespondenceReadReceipts {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final QName TYPE_RECEIPT = q("readReceipt");
    private static final QName TYPE_ACTION = q("workflowAction");
    private static final QName PROP_EMPLOYEE = q("readEmployeeKey");
    private static final QName PROP_SEQUENCE = q("readActionSequence");
    private static final QName PROP_ACTION_SEQUENCE = q("actionSequence");
    private static final QName PROP_TARGET_ROLE = q("targetRole");
    private static final QName PROP_TARGET_DEPT = q("targetDepartmentCode");
    private static final QName PROP_ACTOR_ID = q("actorEmployeeId");

    private final NodeService nodes;
    private final CorrespondenceNodeLocator locator;

    public AlfrescoCorrespondenceReadReceipts(NodeService nodes, CorrespondenceNodeLocator locator) {
        this.nodes = Objects.requireNonNull(nodes);
        this.locator = Objects.requireNonNull(locator);
    }

    public long latestIncoming(NodeRef correspondence, CorrespondenceActorRole role,
            DepartmentCode department, String employeeId, boolean directorReceived) {
        long latest = 0;
        for (ChildAssociationRef association : nodes.getChildAssocs(correspondence)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef child = association.getChildRef();
            if (!TYPE_ACTION.equals(nodes.getType(child))) continue;
            if (!role.name().equals(nodes.getProperty(child, PROP_TARGET_ROLE))) continue;
            if (role == CorrespondenceActorRole.DIRECTOR) {
                if (!directorReceived || department == null) continue;
                Object target = nodes.getProperty(child, PROP_TARGET_DEPT);
                if (target != null && !department.value().equals(target)) continue;
            }
            // The sender's own action is not an incoming notification.
            if (employeeId.equals(nodes.getProperty(child, PROP_ACTOR_ID))) continue;
            Object raw = nodes.getProperty(child, PROP_ACTION_SEQUENCE);
            if (!(raw instanceof Number value) || value.longValue() < 1) {
                throw new CorrespondenceRepositoryException("Invalid incoming action sequence");
            }
            latest = Math.max(latest, value.longValue());
        }
        return latest;
    }

    public boolean isUnread(NodeRef correspondence, String employeeId, long incomingSequence) {
        return incomingSequence > 0 && lastRead(correspondence, employeeId) < incomingSequence;
    }

    public long lastRead(NodeRef correspondence, String employeeId) {
        // Read state is internal metadata. The caller's correspondence access
        // has already been checked before listing this node.
        return AuthenticationUtil.runAsSystem(() -> lastReadInternal(correspondence, employeeId));
    }

    private long lastReadInternal(NodeRef correspondence, String employeeId) {
        NodeRef receipt = find(correspondence, employeeId);
        if (receipt == null) return 0;
        Object raw = nodes.getProperty(receipt, PROP_SEQUENCE);
        if (!(raw instanceof Number number) || number.longValue() < 0) {
            throw new CorrespondenceRepositoryException("Invalid read receipt sequence");
        }
        return number.longValue();
    }

    /** Caller must enforce correspondence read authorization before invoking. */
    public long markRead(String technicalId, CorrespondenceActorRole role,
            DepartmentCode department, String employeeId, long observedSequence,
            boolean directorReceived) {
        if (employeeId == null || employeeId.isBlank() || observedSequence < 0) {
            throw new IllegalArgumentException("Invalid read marker");
        }
        return AuthenticationUtil.runAsSystem(() ->
                markReadInternal(technicalId, role, department, employeeId,
                        observedSequence, directorReceived));
    }

    private long markReadInternal(String technicalId, CorrespondenceActorRole role,
            DepartmentCode department, String employeeId, long observedSequence,
            boolean directorReceived) {
        NodeRef correspondence = locator.locate(technicalId);
        long incoming = latestIncoming(correspondence, role, department, employeeId, directorReceived);
        long safeSequence = Math.min(incoming, observedSequence);
        long previous = lastReadInternal(correspondence, employeeId);
        if (safeSequence <= previous) return previous;
        NodeRef receipt = find(correspondence, employeeId);
        if (receipt == null) {
            String name = "read-receipt-" + hash(employeeId);
            Map<QName, Serializable> props = new HashMap<>();
            props.put(ContentModel.PROP_NAME, name);
            props.put(PROP_EMPLOYEE, hash(employeeId));
            props.put(PROP_SEQUENCE, safeSequence);
            nodes.createNode(correspondence, ContentModel.ASSOC_CONTAINS, q(name), TYPE_RECEIPT, props);
        } else {
            nodes.setProperty(receipt, PROP_SEQUENCE, safeSequence);
        }
        return safeSequence;
    }

    private NodeRef find(NodeRef correspondence, String employeeId) {
        NodeRef match = null;
        for (ChildAssociationRef association : nodes.getChildAssocs(correspondence)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef child = association.getChildRef();
            if (!TYPE_RECEIPT.equals(nodes.getType(child))) continue;
            if (!hash(employeeId).equals(nodes.getProperty(child, PROP_EMPLOYEE))) continue;
            if (match != null) throw new CorrespondenceRepositoryException("Duplicate employee read receipts");
            match = child;
        }
        return match;
    }

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static QName q(String name) { return QName.createQName(NS, name); }
}
