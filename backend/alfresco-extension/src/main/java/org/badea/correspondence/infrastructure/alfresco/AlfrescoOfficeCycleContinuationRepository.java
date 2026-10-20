package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.DepartmentAssignmentStatus;
import org.badea.correspondence.application.DepartmentRecipientType;
import org.badea.correspondence.application.OfficeCycleContinuationRepository;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

/**
 * Creates a new active workflow cycle without deleting or reactivating older assignments.
 * This must execute inside a REQUIRED Alfresco write transaction. Storage failures escape
 * the Web Script and roll back the snapshot, revision reset, history, and state together.
 */
public final class AlfrescoOfficeCycleContinuationRepository implements OfficeCycleContinuationRepository {
    private static final QName STATE = AlfrescoDepartmentAssignmentReader.q("state");
    private static final QName ASSIGNED_ROLE = AlfrescoDepartmentAssignmentReader.q("assignedRole");
    private static final QName REVIEW = AlfrescoDepartmentAssignmentReader.q("presidentReviewState");
    private static final QName REVIEW_REVISION = AlfrescoDepartmentAssignmentReader.q("presidentReviewRevision");
    private static final QName REVIEW_CYCLE = AlfrescoDepartmentAssignmentReader.q("presidentReviewCycle");
    private static final QName REVIEW_UPDATED_BY = AlfrescoDepartmentAssignmentReader.q("presidentReviewUpdatedByEmployeeId");
    private static final QName REVIEW_UPDATED_AT = AlfrescoDepartmentAssignmentReader.q("presidentReviewUpdatedAt");
    private static final QName SNAPSHOT_TYPE = AlfrescoDepartmentAssignmentReader.q("presidentReviewCycleSnapshot");
    private static final QName SNAPSHOT_CYCLE = AlfrescoDepartmentAssignmentReader.q("presidentCycleSnapshotNumber");
    private static final QName SNAPSHOT_REVISION = AlfrescoDepartmentAssignmentReader.q("presidentCycleSnapshotRevision");
    private static final QName SNAPSHOT_BY = AlfrescoDepartmentAssignmentReader.q("presidentCycleSnapshotUpdatedBy");
    private static final QName SNAPSHOT_AT = AlfrescoDepartmentAssignmentReader.q("presidentCycleSnapshotUpdatedAt");
    private static final QName SNAPSHOT_HASH = AlfrescoDepartmentAssignmentReader.q("presidentCycleSnapshotSha256");
    private static final String EMPTY_REVIEW = "{\"schemaVersion\":1,\"selectedRecipientIds\":[],\"selectedCopyRecipientIds\":[],\"selectedActionCodes\":[],\"otherRecipient\":\"\",\"otherAction\":\"\",\"explanationMode\":\"pen\",\"typedExplanation\":\"\",\"explanationInkDocument\":{\"schemaVersion\":1,\"pages\":[{\"id\":\"explanation-page-1\",\"strokes\":[]}]},\"pdfInkDocuments\":[]}";

    private final NodeService nodes;
    private final ContentService content;
    private final PermissionService permissions;
    private final CorrespondenceNodeLocator locator;
    private final AlfrescoCorrespondenceRepository transitions;
    private final Clock clock;

    public AlfrescoOfficeCycleContinuationRepository(NodeService nodes, ContentService content,
            PermissionService permissions, CorrespondenceNodeLocator locator,
            AlfrescoCorrespondenceRepository transitions, Clock clock) {
        this.nodes = Objects.requireNonNull(nodes);
        this.content = Objects.requireNonNull(content);
        this.permissions = Objects.requireNonNull(permissions);
        this.locator = Objects.requireNonNull(locator);
        this.transitions = Objects.requireNonNull(transitions);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public long continueToNextCycle(TransitionCorrespondenceCommand command) {
        boolean office = command.actorRole() == CorrespondenceActorRole.PRESIDENT_OFFICE
                && command.action() == CorrespondenceAction.CONTINUE_CORRESPONDENCE;
        boolean director = command.actorRole() == CorrespondenceActorRole.DIRECTOR
                && command.action() == CorrespondenceAction.DIRECTOR_CONTINUE_CORRESPONDENCE;
        if (!office && !director) {
            throw new CorrespondenceAuthorizationException("Invalid continuation role or action");
        }
        NodeRef parent = locator.locate(command.technicalId());
        if (permissions.hasPermission(parent, PermissionService.WRITE) != AccessStatus.ALLOWED) {
            throw new CorrespondenceAuthorizationException("Alfresco write permission denied");
        }
        long current = new AlfrescoCorrespondenceCycleReader(nodes).current(parent);
        if (current == Long.MAX_VALUE) throw new IllegalStateException("Workflow cycle counter exhausted");
        if (!CorrespondenceState.WITH_DIRECTOR.name().equals(nodes.getProperty(parent, STATE))
                || !CorrespondenceActorRole.DIRECTOR.name().equals(nodes.getProperty(parent, ASSIGNED_ROLE))) {
            throw new OfficeCycleConflictException("Correspondence already moved or is not awaiting departmental completion");
        }
        var active = new AlfrescoDepartmentAssignmentReader(nodes).readCurrent(parent);
        if (active.isEmpty() || active.stream().anyMatch(item ->
                item.recipientType() == DepartmentRecipientType.TO
                        && item.status() != DepartmentAssignmentStatus.COMPLETED)) {
            throw new OfficeCycleConflictException("All required department assignments must be completed");
        }
        // A Director cannot restart another department's assignment or another
        // cycle. Only an actual completed TO recipient in the current cycle qualifies.
        if (director && (command.actorDepartmentCode() == null || active.stream().noneMatch(item ->
                item.recipientType() == DepartmentRecipientType.TO
                && item.status() == DepartmentAssignmentStatus.COMPLETED
                && item.departmentCode().equals(command.actorDepartmentCode())))) {
            throw new CorrespondenceAuthorizationException("Completed TO assignment required");
        }
        long previousRevision = reviewRevision(parent);
        // Snapshot the editable President JSON BEFORE it is replaced. Earlier generated
        // PDFs, the action sheet and Office completion snapshots stay untouched.
        snapshotPriorReview(parent, current, previousRevision);
        ContentWriter writer = content.getWriter(parent, REVIEW, true);
        if (writer == null) throw new CorrespondenceRepositoryException("Cannot initialize next President review");
        writer.setMimetype("application/json");
        writer.setEncoding("UTF-8");
        writer.putContent(EMPTY_REVIEW);
        nodes.setProperty(parent, REVIEW_REVISION, previousRevision + 1L);
        nodes.setProperty(parent, REVIEW_CYCLE, current + 1L);
        nodes.setProperty(parent, REVIEW_UPDATED_BY, command.actorEmployeeId().value());
        nodes.setProperty(parent, REVIEW_UPDATED_AT, Date.from(clock.instant()));
        nodes.setProperty(parent, AlfrescoCorrespondenceCycleReader.CURRENT, current + 1L);
        // The history action is the institutional Office decision to start the next round.
        // Alfresco optimistic locking serializes this with simultaneous Director completions.
        transitions.applyTransition(command, CorrespondenceState.WITH_DIRECTOR,
                CorrespondenceState.WITH_PRESIDENT_OFFICE, CorrespondenceActorRole.PRESIDENT_OFFICE);
        return current + 1L;
    }

    private void snapshotPriorReview(NodeRef parent, long cycle, long revision) {
        if (cycle < 1 || revision < 0) throw new CorrespondenceRepositoryException("Invalid review cycle/revision");
        for (ChildAssociationRef association : nodes.getChildAssocs(parent)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef child = association.getChildRef();
            if (!SNAPSHOT_TYPE.equals(nodes.getType(child))) continue;
            if (Long.valueOf(cycle).equals(nodes.getProperty(child, SNAPSHOT_CYCLE))) {
                throw new IllegalStateException("President review cycle already archived");
            }
        }
        ContentReader reader = content.getReader(parent, REVIEW);
        if (reader == null || !reader.exists()) return;
        String json = reader.getContentString();
        if (json == null || json.isBlank()) return;
        Map<QName, Serializable> props = new HashMap<>();
        props.put(ContentModel.PROP_NAME, "president-review-cycle-" + cycle + ".json");
        props.put(SNAPSHOT_CYCLE, cycle);
        props.put(SNAPSHOT_REVISION, revision);
        props.put(SNAPSHOT_HASH, sha256(json));
        Serializable previousEditor = nodes.getProperty(parent, REVIEW_UPDATED_BY);
        Serializable previousAt = nodes.getProperty(parent, REVIEW_UPDATED_AT);
        if (previousEditor instanceof String editor && !editor.isBlank()) props.put(SNAPSHOT_BY, editor);
        if (previousAt instanceof Date at) props.put(SNAPSHOT_AT, at);
        ChildAssociationRef created = nodes.createNode(parent, ContentModel.ASSOC_CONTAINS,
                AlfrescoDepartmentAssignmentReader.q("president-review-cycle-" + cycle),
                SNAPSHOT_TYPE, props);
        if (created == null || created.getChildRef() == null) {
            throw new CorrespondenceRepositoryException("Cannot persist President review cycle snapshot");
        }
        ContentWriter writer = content.getWriter(created.getChildRef(), ContentModel.PROP_CONTENT, true);
        if (writer == null) throw new CorrespondenceRepositoryException("Cannot write President review cycle snapshot");
        writer.setMimetype("application/json");
        writer.setEncoding("UTF-8");
        writer.putContent(json);
    }

    private long reviewRevision(NodeRef parent) {
        Serializable raw = nodes.getProperty(parent, REVIEW_REVISION);
        if (raw == null) return 0;
        if (!(raw instanceof Number number) || number.longValue() < 0 || number.longValue() == Long.MAX_VALUE) {
            throw new CorrespondenceRepositoryException("Invalid President review revision");
        }
        return number.longValue();
    }

    private static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
