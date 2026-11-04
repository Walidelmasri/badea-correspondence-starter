package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
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
import org.badea.correspondence.application.DepartmentAssignmentStatus;
import org.badea.correspondence.application.DepartmentRecipientType;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.workflow.CorrespondenceState;

/** Persists a secretary's pending follow-up request without advancing the workflow cycle. */
public final class AlfrescoSecretaryRoundTwoRequestRepository {
    private static final QName TYPE = q("secretaryRoundTwoRequest");
    private static final QName STATUS = q("roundTwoRequestStatus");
    private static final QName REQUEST_ID = q("roundTwoRequestId");
    private static final QName UNIT = q("roundTwoRequestUnit");
    private static final QName OWNER = q("roundTwoRequestOwner");
    private static final QName REASON = q("roundTwoRequestReason");
    private static final QName CYCLE = q("roundTwoRequestCycle");
    private static final QName REQUESTED_AT = q("roundTwoRequestAt");
    private static final QName PENDING_ID = q("roundTwoPendingRequestId");
    private static final QName STATE = q("state");
    private static final QName ASSIGNED_ROLE = q("assignedRole");
    private final NodeService nodes;
    private final CorrespondenceNodeLocator locator;
    private final Clock clock;

    public record Request(String requestId, String technicalId, String unitCode,
            String ownerEmployeeId, String reason, long expectedCycle, String status,
            Instant requestedAt) { }

    public AlfrescoSecretaryRoundTwoRequestRepository(NodeService nodes,
            CorrespondenceNodeLocator locator, Clock clock) {
        this.nodes = Objects.requireNonNull(nodes);
        this.locator = Objects.requireNonNull(locator);
        this.clock = Objects.requireNonNull(clock);
    }

    public Request submit(String technicalId, DepartmentCode unit, String ownerEmployeeId,
            String reason) {
        if (ownerEmployeeId == null || ownerEmployeeId.isBlank())
            throw new IllegalArgumentException("Authenticated secretary required");
        if (reason == null || reason.isBlank() || reason.length() > 2000)
            throw new IllegalArgumentException("Reason must contain 1-2000 characters");
        NodeRef caseNode = locator.locate(technicalId);
        long cycle = requireCompletedTo(caseNode, unit);
        for (ChildAssociationRef assoc : nodes.getChildAssocs(caseNode)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(assoc.getTypeQName())) continue;
            NodeRef node = assoc.getChildRef();
            if (!TYPE.equals(nodes.getType(node))) continue;
            if (!"PENDING".equals(nodes.getProperty(node, STATUS))) continue;
            long expected = AlfrescoCorrespondenceCycleReader.number(
                    nodes.getProperty(node, CYCLE), "roundTwoRequestCycle");
            if (expected != cycle) continue;
            Request existing = read(technicalId, node);
            if (existing.unitCode().equals(unit.value())
                    && existing.ownerEmployeeId().equals(ownerEmployeeId)
                    && existing.reason().equals(reason)) return existing;
            throw new OfficeCycleConflictException("Another follow-up request is pending for this cycle");
        }
        String id = UUID.randomUUID().toString();
        Instant now = clock.instant();
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_NAME, "secretary-round-two-" + id);
        properties.put(REQUEST_ID, id);
        properties.put(UNIT, unit.value());
        properties.put(OWNER, ownerEmployeeId);
        properties.put(REASON, reason);
        properties.put(CYCLE, cycle);
        properties.put(STATUS, "PENDING");
        properties.put(REQUESTED_AT, Date.from(now));
        ChildAssociationRef created = nodes.createNode(caseNode, ContentModel.ASSOC_CONTAINS,
                q("secretary-round-two-" + id), TYPE, properties);
        if (created == null || created.getChildRef() == null)
            throw new CorrespondenceRepositoryException("Cannot persist secretary follow-up request");
        // Touch the correspondence parent in the same REQUIRED transaction so
        // simultaneous requests conflict under Alfresco optimistic locking.
        // This is not a workflow-state or cycle change.
        nodes.setProperty(caseNode, PENDING_ID, id);
        return new Request(id, technicalId, unit.value(), ownerEmployeeId, reason,
                cycle, "PENDING", now);
    }

    public Request current(String technicalId, DepartmentCode unit, String ownerEmployeeId) {
        NodeRef caseNode = locator.locate(technicalId);
        long cycle = new AlfrescoCorrespondenceCycleReader(nodes).current(caseNode);
        Request latest = null;
        for (ChildAssociationRef assoc : nodes.getChildAssocs(caseNode)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(assoc.getTypeQName())) continue;
            NodeRef node = assoc.getChildRef();
            if (!TYPE.equals(nodes.getType(node))) continue;
            Request request = read(technicalId, node);
            if (!request.unitCode().equals(unit.value())
                    || !request.ownerEmployeeId().equals(ownerEmployeeId)
                    || request.expectedCycle() != cycle) continue;
            if (latest == null || request.requestedAt().isAfter(latest.requestedAt())) latest = request;
        }
        return latest;
    }

    private long requireCompletedTo(NodeRef caseNode, DepartmentCode unit) {
        if (!CorrespondenceState.WITH_DIRECTOR.name().equals(nodes.getProperty(caseNode, STATE))
                || !"DIRECTOR".equals(nodes.getProperty(caseNode, ASSIGNED_ROLE)))
            throw new OfficeCycleConflictException("Correspondence is not at the department");
        var assignments = new AlfrescoDepartmentAssignmentReader(nodes).readCurrent(caseNode);
        if (assignments.isEmpty() || assignments.stream().anyMatch(item ->
                item.recipientType() == DepartmentRecipientType.TO
                && item.status() != DepartmentAssignmentStatus.COMPLETED))
            throw new OfficeCycleConflictException("Department assignments are not complete");
        boolean recipient = assignments.stream().anyMatch(item ->
                item.recipientType() == DepartmentRecipientType.TO
                && item.status() == DepartmentAssignmentStatus.COMPLETED
                && unit.equals(item.departmentCode()));
        if (!recipient) throw new OfficeCycleConflictException("Unit has no completed TO assignment");
        return new AlfrescoCorrespondenceCycleReader(nodes).current(caseNode);
    }

    private Request read(String technicalId, NodeRef node) {
        try {
            String id = value(node, REQUEST_ID);
            String unit = value(node, UNIT);
            String owner = value(node, OWNER);
            String reason = value(node, REASON);
            String status = value(node, STATUS);
            long cycle = AlfrescoCorrespondenceCycleReader.number(
                    nodes.getProperty(node, CYCLE), "roundTwoRequestCycle");
            Serializable time = nodes.getProperty(node, REQUESTED_AT);
            if (!(time instanceof Date date)
                    || !java.util.Set.of("PENDING", "RETURNED", "APPROVED").contains(status))
                throw new IllegalArgumentException("Invalid request state/timestamp");
            return new Request(id, technicalId, unit, owner, reason, cycle, status, date.toInstant());
        } catch (RuntimeException invalid) {
            throw new CorrespondenceRepositoryException("Invalid saved secretary follow-up request", invalid);
        }
    }

    private String value(NodeRef node, QName key) {
        Serializable raw = nodes.getProperty(node, key);
        if (!(raw instanceof String text) || text.isBlank())
            throw new CorrespondenceRepositoryException("Invalid secretary follow-up property");
        return text;
    }

    private static QName q(String field) { return AlfrescoDepartmentAssignmentReader.q(field); }
}
