package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.Objects;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CorrespondenceParticipation;
import org.badea.correspondence.application.DepartmentAssignmentStatus;
import org.badea.correspondence.application.DepartmentRecipientType;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

/**
 * Read-only institutional mailbox projection. Original correspondence nodes never move.
 * Current assignment controls actionable items; immutable transitions control historical
 * received/sent visibility. Repository scanning should be indexed for large deployments.
 */
public final class AlfrescoCorrespondenceMailboxLister {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final QName TYPE_CORRESPONDENCE = q("correspondence");
    private static final QName TYPE_OFFICE_SNAPSHOT = q("officeCompletionSnapshot");
    private static final QName PROP_TECHNICAL_ID = q("technicalId");
    private static final QName PROP_STATE = q("state");
    private static final QName PROP_ORIGIN = q("originDepartmentCode");
    private static final QName PROP_ASSIGNED_ROLE = q("assignedRole");
    private static final QName PROP_ASSIGNED_DEPARTMENT = q("assignedDepartmentCode");
    private static final QName PROP_SUBJECT = q("subject");
    private static final QName PROP_DOCUMENT_NUMBER = q("documentNumber");
    private static final QName PROP_SENDER = q("createdByNameEnglish");
    private static final QName PROP_CREATION_ROLE = q("createdByCorrespondenceRole");
    private static final QName PROP_CREATED_BY_ID = q("createdByEmployeeId");
    private static final QName PROP_SECRETARY_OWNER = q("secretaryOwnerEmployeeId");
    private static final QName PROP_SECRETARY_STATUS = q("secretaryApprovalStatus");
    private static final QName TYPE_WORKFLOW_ACTION = q("workflowAction");
    private static final QName PROP_ACTION_SEQUENCE = q("actionSequence");
    private static final QName PROP_ACTION_TYPE = q("actionType");
    private static final QName PROP_EXPLANATION = q("explanation");
    private static final QName PROP_PRESIDENT_REVISION = q("presidentReviewRevision");
    private static final QName PROP_PRESIDENT_UPDATED = q("presidentReviewUpdatedAt");
    private static final QName PROP_PRESIDENT_REVIEW_OWNER = q("presidentReviewUpdatedByEmployeeId");
    private static final QName PROP_OFFICE_REVISION = q("officeCompletionCurrentRevision");
    private static final QName PROP_OFFICE_SNAPSHOT_REVISION = q("officeSnapshotRevision");
    private static final QName PROP_OFFICE_UPDATED = q("officeActedAt");
    private static final QName PROP_OFFICE_SNAPSHOT_OWNER = q("officeActorEmployeeId");
    private static final QName TYPE_SECRETARY_ROUND_REQUEST = q("secretaryRoundTwoRequest");
    private static final QName PROP_REQUEST_OWNER = q("roundTwoRequestOwner");
    private static final QName PROP_REQUEST_UNIT = q("roundTwoRequestUnit");
    private static final QName PROP_REQUEST_STATUS = q("roundTwoRequestStatus");
    private static final QName PROP_REQUEST_CYCLE = q("roundTwoRequestCycle");
    private static final QName PROP_PENDING_REQUEST_ID = q("roundTwoPendingRequestId");

    private final NodeService nodes;
    private final PermissionService permissions;
    private final CorrespondenceFolderLocator folders;
    private final AlfrescoCorrespondenceParticipationReader participationReader;
    private final AlfrescoDepartmentAssignmentReader departmentAssignments;
    private final AlfrescoCorrespondenceReadReceipts readReceipts;

    public AlfrescoCorrespondenceMailboxLister(NodeService nodes,
            PermissionService permissions, CorrespondenceFolderLocator folders) {
        this(nodes, permissions, folders, null);
    }

    public AlfrescoCorrespondenceMailboxLister(NodeService nodes,
            PermissionService permissions, CorrespondenceFolderLocator folders,
            AlfrescoCorrespondenceReadReceipts readReceipts) {
        this.nodes = Objects.requireNonNull(nodes);
        this.permissions = Objects.requireNonNull(permissions);
        this.folders = Objects.requireNonNull(folders);
        this.readReceipts = readReceipts;
        this.participationReader = new AlfrescoCorrespondenceParticipationReader(nodes);
        this.departmentAssignments = new AlfrescoDepartmentAssignmentReader(nodes);
    }

    /** Backward-compatible API; production passes authenticated employee identity. */
    public MailboxPage list(String box, CorrespondenceActorRole role,
            DepartmentCode department, int offset, int limit) {
        return list(box, role, department, null, "all", offset, limit);
    }

    public MailboxPage list(String box, CorrespondenceActorRole role,
            DepartmentCode department, String employeeId, String view,
            int offset, int limit) {
        return listInternal(box, role, department, employeeId, view, offset, limit, false);
    }

    /** Separate user-action projection: Director work is visible but not actionable by a secretary.
     * Caller must enforce both the secretary gateway group and active Oracle assignment.
     */
    public MailboxPage listSecretaryInbox(DepartmentCode department, String employeeId,
            String view, int offset, int limit) {
        Objects.requireNonNull(department, "Secretary unit required");
        if (employeeId == null || employeeId.isBlank())
            throw new IllegalArgumentException("Secretary identity required");
        return listInternal("inbox", CorrespondenceActorRole.DIRECTOR,
                department, employeeId, view, offset, limit, true);
    }

    private MailboxPage listInternal(String box, CorrespondenceActorRole role,
            DepartmentCode department, String employeeId, String view,
            int offset, int limit, boolean secretaryProjection) {
        Objects.requireNonNull(role, "actorRole must not be null");
        if (!"inbox".equals(box) && !"sent".equals(box) && !"drafts".equals(box))
            throw new IllegalArgumentException("Unsupported mailbox box");
        if (offset < 0 || limit < 1 || limit > 100)
            throw new IllegalArgumentException("Invalid mailbox pagination");
        if (role == CorrespondenceActorRole.DIRECTOR && department == null) throw forbidden();
        if (!"all".equals(view) && !"needs_action".equals(view)
                && !"handled".equals(view) && !"archived".equals(view)
                && !"unread".equals(view)
                && !(secretaryProjection && "returned".equals(view)))
            throw new IllegalArgumentException("Invalid inbox view");

        BoundedMailboxPageCollector<MailboxSummary> matches = new BoundedMailboxPageCollector<>(
                offset, limit, Comparator.comparing(MailboxSummary::modifiedAt)
                    .reversed().thenComparing(MailboxSummary::technicalId));
        NodeRef root = folders.locateCorrespondenceRoot();
        int deniedFolders = 0;
        boolean deniedChildren = false;
        for (ChildAssociationRef folderAssociation : nodes.getChildAssocs(root)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(folderAssociation.getTypeQName())) continue;
            NodeRef folder = folderAssociation.getChildRef();
            if (permissions.hasPermission(folder, PermissionService.READ) != AccessStatus.ALLOWED) {
                deniedFolders++;
                continue;
            }
            if (!ContentModel.TYPE_FOLDER.equals(nodes.getType(folder))) continue;
            deniedChildren |= collect(folder, box, role, department, employeeId, view,
                    secretaryProjection, matches::add);
        }
        // Do not silently return a partial/empty President mailbox on ACL denial.
        // Never bypass Alfresco permissions or disclose inaccessible content.
        if (role == CorrespondenceActorRole.PRESIDENT
                && (deniedFolders > 0 || deniedChildren)) {
            throw new CorrespondenceMailboxPermissionException();
        }

        return new MailboxPage(matches.page(), offset, limit, matches.total());
    }

    /** Exact-case membership check using the same Director Inbox criteria as list().
     * Caller must separately check secretary group and active Oracle unit grant.
     * This method must be invoked inside the existing institutional read scope.
     */
    public boolean containsDirectorInboxCase(String technicalId, DepartmentCode department) {
        Objects.requireNonNull(technicalId, "technicalId must not be null");
        Objects.requireNonNull(department, "department must not be null");
        if (!java.util.UUID.fromString(technicalId).toString().equals(technicalId)) {
            throw new IllegalArgumentException("Canonical correspondence ID required");
        }
        NodeRef root = folders.locateCorrespondenceRoot();
        boolean seen = false;
        boolean visible = false;
        for (ChildAssociationRef folderAssoc : nodes.getChildAssocs(root)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(folderAssoc.getTypeQName())) continue;
            NodeRef folder = folderAssoc.getChildRef();
            if (!ContentModel.TYPE_FOLDER.equals(nodes.getType(folder))) continue;
            for (ChildAssociationRef assoc : nodes.getChildAssocs(folder)) {
                if (!ContentModel.ASSOC_CONTAINS.equals(assoc.getTypeQName())) continue;
                NodeRef node = assoc.getChildRef();
                if (!TYPE_CORRESPONDENCE.equals(nodes.getType(node))) continue;
                if (!technicalId.equals(optional(node, PROP_TECHNICAL_ID))) continue;
                if (seen) throw new CorrespondenceRepositoryException("Duplicate correspondence ID");
                seen = true;
                CorrespondenceState state = readState(node);
                if (state == CorrespondenceState.DRAFT) continue;
                boolean receivedByUnit = departmentAssignments.read(node).stream()
                        .anyMatch(a -> a.isVisibleTo(department));
                boolean activeAssignment = receivedByUnit && departmentAssignments.readCurrent(node)
                        .stream().anyMatch(a -> a.isActionableFor(department));
                boolean active = isAssigned(node, state, CorrespondenceActorRole.DIRECTOR,
                        department) || activeAssignment;
                boolean receivedByHistory = participationReader.read(node).stream()
                        .anyMatch(a -> a.receivedBy(CorrespondenceActorRole.DIRECTOR, department));
                visible = active || receivedByUnit || receivedByHistory;
            }
        }
        return visible;
    }

    private boolean collect(NodeRef folder, String box, CorrespondenceActorRole role,
            DepartmentCode department, String employeeId, String view,
            boolean secretaryProjection, Consumer<MailboxSummary> matches) {
        boolean deniedChild = false;
        for (ChildAssociationRef association : nodes.getChildAssocs(folder)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef node = association.getChildRef();
            if (permissions.hasPermission(node, PermissionService.READ) != AccessStatus.ALLOWED) {
                deniedChild = true;
                continue;
            }
            if (!TYPE_CORRESPONDENCE.equals(nodes.getType(node))) continue;

            CorrespondenceState state = readState(node);
            // Personal returned secretary drafts are first-class secretary Inbox
            // rows. They share the SAME bounded collector, sort order, and page
            // total as routed correspondence; never append a second UI list.
            if (state == CorrespondenceState.DRAFT && secretaryProjection
                    && "inbox".equals(box)) {
                if (("all".equals(view) || "needs_action".equals(view)
                        || "returned".equals(view))
                        && department != null
                        && department.value().equals(optional(node, PROP_ORIGIN))
                        && employeeId != null
                        && employeeId.equals(optional(node, PROP_SECRETARY_OWNER))
                        && "RETURNED".equals(optional(node, PROP_SECRETARY_STATUS))) {
                    matches.accept(new MailboxSummary(
                        required(node, PROP_TECHNICAL_ID), required(node, PROP_SUBJECT),
                        optional(node, PROP_DOCUMENT_NUMBER), state.name(),
                        department.value(), required(node, PROP_SENDER),
                        timestamp(node, ContentModel.PROP_CREATED),
                        timestamp(node, ContentModel.PROP_MODIFIED), true, false, 0,
                        "RETURNED", latestSecretaryReturnReason(node)));
                }
                continue;
            }
            String origin = required(node, PROP_ORIGIN);
            var distributed = departmentAssignments.read(node);
            boolean directorReceived = role == CorrespondenceActorRole.DIRECTOR && department != null
                    && distributed.stream().anyMatch(a -> a.isVisibleTo(department));
            var activeAssignments = departmentAssignments.readCurrent(node);
            boolean directorNeedsAction = directorReceived
                    && activeAssignments.stream().anyMatch(a -> a.isActionableFor(department));
            boolean active = isAssigned(node, state, role, department) || directorNeedsAction;
            boolean returnedRoundRequest = secretaryProjection
                    && secretaryReturnedRequestNeedsAction(node, state, department, employeeId);
            boolean actionable = secretaryProjection ? returnedRoundRequest : active;
            // The Returned filter also includes authorized returned Round N
            // continuation requests, which remain routed correspondence.
            if ("returned".equals(view) && !returnedRoundRequest) continue;
            // Executive drafts are creator-private and belong only in their
            // owner's Drafts box. Director draft listing remains unchanged.
            if (state == CorrespondenceState.DRAFT) {
                if (role == CorrespondenceActorRole.DIRECTOR
                        || !role.name().equals(optional(node, PROP_CREATION_ROLE))
                        || employeeId == null
                        || !employeeId.equals(optional(node, PROP_CREATED_BY_ID))
                        || !"drafts".equals(box)) {
                    continue;
                }
                matches.accept(new MailboxSummary(
                        required(node, PROP_TECHNICAL_ID), required(node, PROP_SUBJECT),
                        optional(node, PROP_DOCUMENT_NUMBER), state.name(), origin,
                        required(node, PROP_SENDER), timestamp(node, ContentModel.PROP_CREATED),
                        timestamp(node, ContentModel.PROP_MODIFIED), false));
                continue;
            }
            List<CorrespondenceParticipation> actions = participationReader.read(node);
            boolean received = directorReceived || actions.stream().anyMatch(a -> a.receivedBy(role, department));
            boolean sent = actions.stream().anyMatch(a -> a.sentBy(role, employeeId));
            // Sent is based on actual authenticated employee participation for every
            // role. Institutional/departmental delivery remains shared in Inbox only.

            boolean included = switch (box) {
                case "inbox" -> active || received;
                case "sent" -> sent;
                // Saved President reviews and Office completion work are
                // creator-private even though their underlying case is shared.
                case "drafts" -> isDraft(node, role, department, state, actions, employeeId);
                default -> false;
            };
            if (!included) continue;
            if ("inbox".equals(box)) {
                boolean matchesView = switch (view) {
                    case "needs_action" -> actionable;
                    case "handled" -> !actionable && (!secretaryProjection || !active)
                            && state != CorrespondenceState.ARCHIVED;
                    case "archived" -> state == CorrespondenceState.ARCHIVED;
                    default -> true;
                };
                if (!matchesView) continue;
            }
            long incoming = 0;
            boolean unread = false;
            if ("inbox".equals(box) && readReceipts != null && employeeId != null) {
                incoming = readReceipts.latestIncoming(node, role, department, employeeId, directorReceived);
                unread = readReceipts.isUnread(node, employeeId, incoming);
            }
            if ("inbox".equals(box) && "unread".equals(view) && !unread) continue;
            matches.accept(new MailboxSummary(
                    required(node, PROP_TECHNICAL_ID), required(node, PROP_SUBJECT),
                    optional(node, PROP_DOCUMENT_NUMBER), state.name(), origin,
                    required(node, PROP_SENDER), timestamp(node, ContentModel.PROP_CREATED),
                    timestamp(node, ContentModel.PROP_MODIFIED), actionable, unread, incoming));
        }
        return deniedChild;
    }

    /** Read the persisted latest manager-return explanation only for the owner
     * whose returned draft has already passed the unit and identity checks. */
    private String latestSecretaryReturnReason(NodeRef draft) {
        long latest = -1;
        String reason = null;
        for (ChildAssociationRef assoc : nodes.getChildAssocs(draft)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(assoc.getTypeQName())) continue;
            NodeRef action = assoc.getChildRef();
            if (action == null || !TYPE_WORKFLOW_ACTION.equals(nodes.getType(action))
                    || !"RETURN_TO_SECRETARY".equals(optional(action, PROP_ACTION_TYPE))) continue;
            Serializable sequence = nodes.getProperty(action, PROP_ACTION_SEQUENCE);
            if (!(sequence instanceof Number number) || number.longValue() <= latest) continue;
            latest = number.longValue();
            reason = optional(action, PROP_EXPLANATION);
        }
        return reason;
    }

    /** Only a returned, current-cycle request owned by this secretary is a personal
     * follow-up action. Outstanding Director assignments remain visible in All/Unread.
     */
    private boolean secretaryReturnedRequestNeedsAction(NodeRef correspondence,
            CorrespondenceState state, DepartmentCode unit, String employeeId) {
        if (state != CorrespondenceState.WITH_DIRECTOR
                || !"DIRECTOR".equals(optional(correspondence, PROP_ASSIGNED_ROLE))
                || unit == null || employeeId == null
                || nodes.getProperty(correspondence, PROP_PENDING_REQUEST_ID) != null)
            return false;
        var current = departmentAssignments.readCurrent(correspondence);
        if (current.isEmpty() || current.stream().anyMatch(item ->
                item.recipientType() == DepartmentRecipientType.TO
                && item.status() != DepartmentAssignmentStatus.COMPLETED)
                || current.stream().noneMatch(item ->
                    item.recipientType() == DepartmentRecipientType.TO
                    && item.status() == DepartmentAssignmentStatus.COMPLETED
                    && unit.equals(item.departmentCode()))) return false;
        long cycle = new AlfrescoCorrespondenceCycleReader(nodes).current(correspondence);
        for (ChildAssociationRef association : nodes.getChildAssocs(correspondence)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef child = association.getChildRef();
            if (!TYPE_SECRETARY_ROUND_REQUEST.equals(nodes.getType(child))) continue;
            if (!employeeId.equals(optional(child, PROP_REQUEST_OWNER))
                    || !unit.value().equals(optional(child, PROP_REQUEST_UNIT))
                    || !"RETURNED".equals(optional(child, PROP_REQUEST_STATUS))) continue;
            if (AlfrescoCorrespondenceCycleReader.number(
                    nodes.getProperty(child, PROP_REQUEST_CYCLE), "roundTwoRequestCycle") == cycle)
                return true;
        }
        return false;
    }

    private boolean isDraft(NodeRef node, CorrespondenceActorRole role,
            DepartmentCode department, CorrespondenceState state,
            List<CorrespondenceParticipation> actions, String employeeId) {
        if (employeeId == null || role == CorrespondenceActorRole.DIRECTOR) return false;
        if (!isAssigned(node, state, role, department)) return false;
        if (role == CorrespondenceActorRole.PRESIDENT) {
            if (!employeeId.equals(optional(node, PROP_PRESIDENT_REVIEW_OWNER))
                    || revision(node, PROP_PRESIDENT_REVISION) < 1) return false;
            Instant saved = optionalDate(node, PROP_PRESIDENT_UPDATED);
            Instant entered = latestReceived(actions, CorrespondenceActorRole.PRESIDENT);
            return saved != null && entered != null && !saved.isBefore(entered);
        }
        if (revision(node, PROP_OFFICE_REVISION) < 1) return false;
        // Office draft starts after the latest presidential return, not after first receipt.
        Instant returned = null;
        for (CorrespondenceParticipation action : actions) {
            if (action.actorRole() == CorrespondenceActorRole.PRESIDENT
                    && action.targetRole() == CorrespondenceActorRole.PRESIDENT_OFFICE
                    && (returned == null || action.actedAt().isAfter(returned)))
                returned = action.actedAt();
        }
        // An Office draft must belong to the latest Office handoff, not a prior cycle.
        Instant lastOfficeReceipt = latestReceived(actions, CorrespondenceActorRole.PRESIDENT_OFFICE);
        if (returned == null || !returned.equals(lastOfficeReceipt)) return false;
        Instant saved = latestOfficeSnapshotAt(node, employeeId);
        return saved != null && !saved.isBefore(returned);
    }

    private Instant latestOfficeSnapshotAt(NodeRef correspondence, String employeeId) {
        long head = revision(correspondence, PROP_OFFICE_REVISION);
        Instant time = null;
        for (ChildAssociationRef assoc : nodes.getChildAssocs(correspondence)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(assoc.getTypeQName())) continue;
            NodeRef child = assoc.getChildRef();
            if (!TYPE_OFFICE_SNAPSHOT.equals(nodes.getType(child))) continue;
            if (revision(child, PROP_OFFICE_SNAPSHOT_REVISION) != head) continue;
            if (!employeeId.equals(optional(child, PROP_OFFICE_SNAPSHOT_OWNER))) return null;
            Instant updated = optionalDate(child, PROP_OFFICE_UPDATED);
            if (updated == null) throw new CorrespondenceRepositoryException("Office snapshot timestamp is missing");
            if (time != null) throw new CorrespondenceRepositoryException("Duplicate Office HEAD snapshot");
            time = updated;
        }
        return time;
    }

    private static Instant latestReceived(List<CorrespondenceParticipation> actions,
            CorrespondenceActorRole role) {
        Instant latest = null;
        for (CorrespondenceParticipation action : actions) {
            if (action.targetRole() == role && (latest == null || action.actedAt().isAfter(latest)))
                latest = action.actedAt();
        }
        return latest;
    }

    private boolean isAssigned(NodeRef node, CorrespondenceState state,
            CorrespondenceActorRole role, DepartmentCode department) {
        if (!role.name().equals(optional(node, PROP_ASSIGNED_ROLE))) return false;
        return switch (role) {
            case DIRECTOR -> state == CorrespondenceState.WITH_DIRECTOR
                    && department != null
                    && department.value().equals(optional(node, PROP_ASSIGNED_DEPARTMENT));
            case PRESIDENT_OFFICE -> state == CorrespondenceState.WITH_PRESIDENT_OFFICE;
            case PRESIDENT -> state == CorrespondenceState.WITH_PRESIDENT;
            case SECRETARY, TARASOL_ADMIN, TARASOL_DELEGATE -> false; // Personal draft inbox is separate.
        };
    }

    private long revision(NodeRef node, QName property) {
        Serializable raw = nodes.getProperty(node, property);
        if (raw == null) return 0L;
        if (!(raw instanceof Number number) || number.longValue() < 0)
            throw new CorrespondenceRepositoryException("Invalid correspondence revision");
        return number.longValue();
    }

    private Instant optionalDate(NodeRef node, QName property) {
        Serializable raw = nodes.getProperty(node, property);
        if (raw == null) return null;
        if (!(raw instanceof Date date))
            throw new CorrespondenceRepositoryException("Invalid correspondence date");
        return date.toInstant();
    }

    private CorrespondenceState readState(NodeRef node) {
        try { return CorrespondenceState.valueOf(required(node, PROP_STATE)); }
        catch (IllegalArgumentException exception) {
            throw new CorrespondenceRepositoryException("Unknown correspondence state", exception);
        }
    }
    private String required(NodeRef node, QName property) {
        String value = optional(node, property);
        if (value == null || value.isBlank()) throw new CorrespondenceRepositoryException(
                "Missing correspondence metadata: " + property.getLocalName());
        return value;
    }
    private String optional(NodeRef node, QName property) {
        Serializable value = nodes.getProperty(node, property);
        if (value == null) return null;
        if (!(value instanceof String text)) throw new CorrespondenceRepositoryException(
                "Invalid correspondence metadata: " + property.getLocalName());
        return text;
    }
    private Instant timestamp(NodeRef node, QName property) {
        Instant value = optionalDate(node, property);
        if (value == null) throw new CorrespondenceRepositoryException("Missing correspondence timestamp");
        return value;
    }
    private static CorrespondenceAuthorizationException forbidden() {
        return new CorrespondenceAuthorizationException("Authenticated user cannot access mailbox");
    }
    private static QName q(String name) { return QName.createQName(NS, name); }

    public record MailboxSummary(String technicalId, String subject, String documentNumber,
            String state, String originDepartmentCode, String senderName,
            Instant createdAt, Instant modifiedAt, boolean needsAction,
            boolean unread, long incomingSequence,
            String approvalStatus, String returnReason) {
        public MailboxSummary(String technicalId, String subject, String documentNumber,
                String state, String originDepartmentCode, String senderName,
                Instant createdAt, Instant modifiedAt, boolean needsAction,
                boolean unread, long incomingSequence) {
            this(technicalId, subject, documentNumber, state, originDepartmentCode,
                    senderName, createdAt, modifiedAt, needsAction, unread, incomingSequence,
                    null, null);
        }
        public MailboxSummary(String technicalId, String subject, String documentNumber,
                String state, String originDepartmentCode, String senderName,
                Instant createdAt, Instant modifiedAt, boolean needsAction) {
            this(technicalId, subject, documentNumber, state, originDepartmentCode,
                    senderName, createdAt, modifiedAt, needsAction, false, 0);
        }
        public MailboxSummary(String technicalId, String subject, String documentNumber,
                String state, String originDepartmentCode, String senderName,
                Instant createdAt, Instant modifiedAt) {
            this(technicalId, subject, documentNumber, state, originDepartmentCode,
                    senderName, createdAt, modifiedAt, false, false, 0);
        }
    }
    public record MailboxPage(List<MailboxSummary> items, int offset, int limit, int total) {
        public MailboxPage { items = List.copyOf(items); }
    }
}
