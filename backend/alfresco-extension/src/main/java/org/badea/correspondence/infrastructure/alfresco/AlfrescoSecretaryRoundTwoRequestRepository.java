package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.DepartmentAssignmentStatus;
import org.badea.correspondence.application.DepartmentRecipientType;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
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
    private static final QName DECIDED_BY = q("roundTwoRequestDecidedBy");
    private static final QName DECIDED_AT = q("roundTwoRequestDecidedAt");
    private static final QName RETURN_REASON = q("roundTwoRequestReturnReason");
    private static final QName RETURN_VERSION = q("roundTwoRequestReturnVersion");
    private static final QName DECISION_EVENT = q("secretaryRoundTwoDecisionEvent");
    private static final QName EVENT_REQUEST = q("roundTwoDecisionRequestId");
    private static final QName EVENT_ACTION = q("roundTwoDecisionAction");
    private static final QName EVENT_ACTOR = q("roundTwoDecisionActor");
    private static final QName EVENT_AT = q("roundTwoDecisionAt");
    private static final QName EVENT_REASON = q("roundTwoDecisionReason");
    private static final QName EVENT_VERSION = q("roundTwoDecisionReturnVersion");
    private static final QName STATE = q("state");
    private static final QName ASSIGNED_ROLE = q("assignedRole");
    private final NodeService nodes;
    private final CorrespondenceNodeLocator locator;
    private final Clock clock;

    public record Request(String requestId, String technicalId, String unitCode,
            String ownerEmployeeId, String reason, long expectedCycle, String status,
            Instant requestedAt, String returnReason, long returnVersion) {
        public Request(String requestId, String technicalId, String unitCode,
                String ownerEmployeeId, String reason, long expectedCycle, String status,
                Instant requestedAt, String returnReason) {
            this(requestId, technicalId, unitCode, ownerEmployeeId, reason,
                expectedCycle, status, requestedAt, returnReason, 0);
        }
        public Request(String requestId, String technicalId, String unitCode,
                String ownerEmployeeId, String reason, long expectedCycle, String status,
                Instant requestedAt) {
            this(requestId, technicalId, unitCode, ownerEmployeeId, reason,
                expectedCycle, status, requestedAt, null, 0);
        }
    }

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
            long expected = AlfrescoCorrespondenceCycleReader.number(
                    nodes.getProperty(node, CYCLE), "roundTwoRequestCycle");
            if (expected != cycle) continue;
            Request existing = read(technicalId, node);
            if ("PREPARING".equals(existing.status())
                    && existing.unitCode().equals(unit.value())
                    && existing.ownerEmployeeId().equals(ownerEmployeeId)
                    && existing.reason().equals(reason)) {
                if (nodes.getProperty(caseNode, PENDING_ID) != null)
                    throw new OfficeCycleConflictException("An active request marker already exists");
                return existing;
            }
            if (!"APPROVED".equals(existing.status()))
                throw new OfficeCycleConflictException("An existing follow-up request must be resolved first");
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
        properties.put(STATUS, "PREPARING");
        properties.put(RETURN_VERSION, 0L);
        properties.put(REQUESTED_AT, Date.from(now));
        ChildAssociationRef created = nodes.createNode(caseNode, ContentModel.ASSOC_CONTAINS,
                q("secretary-round-two-" + id), TYPE, properties);
        if (created == null || created.getChildRef() == null)
            throw new CorrespondenceRepositoryException("Cannot persist secretary follow-up request");
        // Touch the correspondence parent in the same REQUIRED transaction so
        // simultaneous requests conflict under Alfresco optimistic locking.
        // This is not a workflow-state or cycle change.
        nodes.setProperty(caseNode, PENDING_ID, null);
        return new Request(id, technicalId, unit.value(), ownerEmployeeId, reason,
                cycle, "PREPARING", now);
    }

    /** Read-only eligibility derived from the same persisted assignments and cycle guard as submit().
     * A caller MUST separately enforce secretary group, active assignment and shared-case access.
     */
    public Eligibility eligibility(String technicalId, DepartmentCode unit, String ownerEmployeeId) {
        NodeRef parent = locator.locate(technicalId);
        long cycle = new AlfrescoCorrespondenceCycleReader(nodes).current(parent);
        Request owned = current(technicalId, unit, ownerEmployeeId);
        try {
            requireCompletedTo(parent, unit);
        } catch (OfficeCycleConflictException notReady) {
            return new Eligibility(false, false, owned == null ? null : owned.status(),
                    cycle + 1, "DEPARTMENT_NOT_READY");
        }
        Object pointer = nodes.getProperty(parent, PENDING_ID);
        for (ChildAssociationRef assoc : nodes.getChildAssocs(parent)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(assoc.getTypeQName())) continue;
            NodeRef child = assoc.getChildRef();
            if (!TYPE.equals(nodes.getType(child))) continue;
            if (AlfrescoCorrespondenceCycleReader.number(nodes.getProperty(child, CYCLE),
                    "roundTwoRequestCycle") != cycle) continue;
            Request saved = read(technicalId, child);
            if ("APPROVED".equals(saved.status())) continue;
            if (owned != null && saved.requestId().equals(owned.requestId())) {
                boolean canEdit = ("PREPARING".equals(saved.status())
                        || "RETURNED".equals(saved.status())) && pointer == null;
                boolean canRead = canEdit || ("PENDING".equals(saved.status())
                        && saved.requestId().equals(pointer));
                return new Eligibility(false, canRead, saved.status(), cycle + 1,
                        canRead ? "OWN_REQUEST" : "REQUEST_CONFLICT");
            }
            return new Eligibility(false, false, null, cycle + 1, "REQUEST_CONFLICT");
        }
        if (pointer != null) return new Eligibility(false, false, null,
                cycle + 1, "REQUEST_CONFLICT");
        return new Eligibility(true, false, null, cycle + 1, "READY");
    }

    public record Eligibility(boolean canPrepare, boolean canReadRequest,
            String requestStatus, long nextCycle, String reasonCode) { }

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

    /** Private, cycle-bound record; the caller must enforce the secretary group and Oracle grant. */
    public Request editable(String technicalId, DepartmentCode unit, String ownerEmployeeId,
            String requestId) {
        NodeRef parent = locator.locate(technicalId);
        return requireEditable(parent, technicalId, unit, ownerEmployeeId, requestId).request();
    }

    /** Explanation edits never clear the manager's return reason or trigger a workflow transition. */
    public Request revise(String technicalId, DepartmentCode unit, String ownerEmployeeId,
            String requestId, long expectedReturnVersion, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 2000)
            throw new IllegalArgumentException("Follow-up reason must contain 1-2000 characters");
        NodeRef parent = locator.locate(technicalId);
        Pending entry = requireEditable(parent, technicalId, unit, ownerEmployeeId, requestId);
        requireRevisionVersion(entry.request(), expectedReturnVersion);
        String clean = reason.trim();
        if (!clean.equals(entry.request().reason())) {
            nodes.setProperty(entry.node(), REASON, clean);
            recordEvent(parent, entry.request(), "REVISED", ownerEmployeeId, clean,
                expectedReturnVersion);
        }
        return read(technicalId, entry.node());
    }

    /** Explicit first submission; a prepared request never enters the manager queue before this. */
    public Request submitPrepared(String technicalId, DepartmentCode unit, String ownerEmployeeId,
            String requestId) {
        NodeRef parent = locator.locate(technicalId);
        Pending entry = requireEditable(parent, technicalId, unit, ownerEmployeeId, requestId);
        if (!"PREPARING".equals(entry.request().status())
                || entry.request().returnVersion() != 0)
            throw new OfficeCycleConflictException("Request was already submitted or returned");
        return publish(parent, technicalId, entry, ownerEmployeeId, "SUBMITTED");
    }

    /** Only a returned request can be resubmitted with its current return token. */
    public Request resubmit(String technicalId, DepartmentCode unit, String ownerEmployeeId,
            String requestId, long expectedReturnVersion) {
        NodeRef parent = locator.locate(technicalId);
        Pending entry = requireEditable(parent, technicalId, unit, ownerEmployeeId, requestId);
        requireReturnedVersion(entry.request(), expectedReturnVersion);
        return publish(parent, technicalId, entry, ownerEmployeeId, "RESUBMITTED");
    }

    private Request publish(NodeRef parent, String technicalId, Pending entry,
            String ownerEmployeeId, String event) {
        // Publish the manager-visible pointer in this same REQUIRED transaction.
        // This write and staged-document mutations compete on the parent node.
        nodes.setProperty(entry.node(), STATUS, "PENDING");
        nodes.setProperty(entry.node(), REQUESTED_AT, Date.from(clock.instant()));
        recordEvent(parent, entry.request(), event, ownerEmployeeId, null,
            entry.request().returnVersion());
        nodes.setProperty(parent, PENDING_ID, entry.request().requestId());
        return read(technicalId, entry.node());
    }

    private Pending requireEditable(NodeRef parent, String technicalId, DepartmentCode unit,
            String ownerEmployeeId, String requestId) {
        if (requestId == null || !requestId.matches("[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Valid request ID required");
        long cycle = requireCompletedTo(parent, unit);
        for (ChildAssociationRef assoc : nodes.getChildAssocs(parent)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(assoc.getTypeQName())) continue;
            NodeRef child = assoc.getChildRef();
            if (!TYPE.equals(nodes.getType(child))
                    || !requestId.equals(nodes.getProperty(child, REQUEST_ID))) continue;
            Request stored = read(technicalId, child);
            if (!ownerEmployeeId.equals(stored.ownerEmployeeId()))
                throw new CorrespondenceAuthorizationException("Only the request creator may revise");
            if (!unit.value().equals(stored.unitCode()) || stored.expectedCycle() != cycle)
                throw new OfficeCycleConflictException("Request cycle or unit has changed");
            Object pointer = nodes.getProperty(parent, PENDING_ID);
            if (("PENDING".equals(stored.status()) && requestId.equals(pointer))
                    || (("RETURNED".equals(stored.status())
                        || "PREPARING".equals(stored.status())) && pointer == null))
                return new Pending(child, stored);
            throw new OfficeCycleConflictException("Request is not editable");
        }
        throw new OfficeCycleConflictException("Request is not editable");
    }

    private static void requireRevisionVersion(Request request, long expectedReturnVersion) {
        if ("PREPARING".equals(request.status()) && expectedReturnVersion == 0
                && request.returnVersion() == 0) return;
        requireReturnedVersion(request, expectedReturnVersion);
    }

    private static void requireReturnedVersion(Request request, long expectedReturnVersion) {
        if (!"RETURNED".equals(request.status())
                || expectedReturnVersion < 0 || request.returnVersion() != expectedReturnVersion)
            throw new OfficeCycleConflictException("Return version is stale or already resubmitted");
    }

    /** Reads only the request currently awaiting a decision for this unit and workflow cycle. */
    public Request pending(String technicalId, DepartmentCode unit) {
        NodeRef parent = locator.locate(technicalId);
        Serializable raw = nodes.getProperty(parent, PENDING_ID);
        if (!(raw instanceof String id) || id.isBlank()) return null;
        return pendingRequest(parent, technicalId, unit, id).request();
    }

    /** The caller must run this method and continuation in one REQUIRED Alfresco transaction. */
    public Decision approve(String technicalId, DepartmentCode unit, String requestId,
            String managerEmployeeId, LongSupplier continuation) {
        Objects.requireNonNull(continuation);
        NodeRef parent = locator.locate(technicalId);
        Pending entry = pendingRequest(parent, technicalId, unit, requestId);
        long nextCycle = continuation.getAsLong();
        if (nextCycle != entry.request().expectedCycle() + 1)
            throw new OfficeCycleConflictException("Unexpected continuation cycle");
        saveDecision(parent, entry, managerEmployeeId, "APPROVED", null);
        return new Decision(requestId, technicalId, "APPROVED", nextCycle);
    }

    /** A return never mutates the correspondence's workflow state or cycle. */
    public Decision returnForRevision(String technicalId, DepartmentCode unit,
            String requestId, String managerEmployeeId, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 2000)
            throw new IllegalArgumentException("Return reason must contain 1-2000 characters");
        NodeRef parent = locator.locate(technicalId);
        Pending entry = pendingRequest(parent, technicalId, unit, requestId);
        saveDecision(parent, entry, managerEmployeeId, "RETURNED", reason.trim());
        return new Decision(requestId, technicalId, "RETURNED", entry.request().expectedCycle());
    }

    public record Decision(String requestId, String technicalId, String status, long workflowCycle) { }
    private record Pending(NodeRef node, Request request) { }

    private Pending pendingRequest(NodeRef parent, String technicalId,
            DepartmentCode unit, String requestId) {
        if (requestId == null || !requestId.matches("[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Valid request ID required");
        if (!requestId.equals(nodes.getProperty(parent, PENDING_ID)))
            throw new OfficeCycleConflictException("Request is no longer pending");
        long cycle = requireCompletedTo(parent, unit);
        for (ChildAssociationRef assoc : nodes.getChildAssocs(parent)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(assoc.getTypeQName())) continue;
            NodeRef child = assoc.getChildRef();
            if (!TYPE.equals(nodes.getType(child))) continue;
            if (!requestId.equals(nodes.getProperty(child, REQUEST_ID))) continue;
            Request request = read(technicalId, child);
            if (!"PENDING".equals(request.status()) || !unit.value().equals(request.unitCode())
                    || request.expectedCycle() != cycle)
                throw new OfficeCycleConflictException("Request is stale or belongs to another unit");
            return new Pending(child, request);
        }
        throw new OfficeCycleConflictException("Pending request not found");
    }

    private void saveDecision(NodeRef parent, Pending entry,
            String employeeId, String status, String returnReason) {
        if (employeeId == null || employeeId.isBlank())
            throw new IllegalArgumentException("Manager identity required");
        NodeRef request = entry.node();
        long version = entry.request().returnVersion();
        if ("RETURNED".equals(status)) {
            version = Math.addExact(version, 1);
            nodes.setProperty(request, RETURN_VERSION, version);
        }
        nodes.setProperty(request, STATUS, status);
        nodes.setProperty(request, DECIDED_BY, employeeId);
        nodes.setProperty(request, DECIDED_AT, Date.from(clock.instant()));
        if (returnReason != null) nodes.setProperty(request, RETURN_REASON, returnReason);
        recordEvent(parent, entry.request(), status, employeeId, returnReason, version);
        // Optimistic concurrency on the parent serializes decisions and further requests.
        nodes.setProperty(parent, PENDING_ID, null);
    }

    /** Append-only sibling nodes retain each decision across repeated return/resubmit cycles. */
    private void recordEvent(NodeRef parent, Request request, String action, String actor,
            String reason, long version) {
        String id = UUID.randomUUID().toString();
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_NAME, "secretary-decision-" + id);
        properties.put(EVENT_REQUEST, request.requestId());
        properties.put(EVENT_ACTION, action);
        properties.put(EVENT_ACTOR, actor);
        properties.put(EVENT_AT, Date.from(clock.instant()));
        properties.put(EVENT_VERSION, version);
        if (reason != null) properties.put(EVENT_REASON, reason);
        ChildAssociationRef created = nodes.createNode(parent, ContentModel.ASSOC_CONTAINS,
            q("secretary-decision-" + id), DECISION_EVENT, properties);
        if (created == null || created.getChildRef() == null)
            throw new CorrespondenceRepositoryException("Unable to persist secretary decision history");
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
                    || !java.util.Set.of("PREPARING", "PENDING", "RETURNED", "APPROVED").contains(status))
                throw new IllegalArgumentException("Invalid request state/timestamp");
            Serializable returned = nodes.getProperty(node, RETURN_REASON);
            if (returned != null && !(returned instanceof String))
                throw new IllegalArgumentException("Invalid return reason");
            Serializable versionValue = nodes.getProperty(node, RETURN_VERSION);
            if (versionValue != null && !(versionValue instanceof Number))
                throw new IllegalArgumentException("Invalid return version");
            long version = versionValue == null ? 0 : ((Number) versionValue).longValue();
            if (version < 0 || (versionValue instanceof java.math.BigDecimal decimal
                    && decimal.stripTrailingZeros().scale() > 0))
                throw new IllegalArgumentException("Invalid return version");
            return new Request(id, technicalId, unit, owner, reason, cycle, status,
                date.toInstant(), returned == null ? null : (String) returned, version);
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
