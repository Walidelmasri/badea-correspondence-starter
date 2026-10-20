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
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CorrespondenceParticipation;
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
    private static final QName PROP_PRESIDENT_REVISION = q("presidentReviewRevision");
    private static final QName PROP_PRESIDENT_UPDATED = q("presidentReviewUpdatedAt");
    private static final QName PROP_OFFICE_REVISION = q("officeCompletionCurrentRevision");
    private static final QName PROP_OFFICE_SNAPSHOT_REVISION = q("officeSnapshotRevision");
    private static final QName PROP_OFFICE_UPDATED = q("officeActedAt");

    private final NodeService nodes;
    private final PermissionService permissions;
    private final CorrespondenceFolderLocator folders;
    private final AlfrescoCorrespondenceParticipationReader participationReader;
    private final AlfrescoDepartmentAssignmentReader departmentAssignments;

    public AlfrescoCorrespondenceMailboxLister(NodeService nodes,
            PermissionService permissions, CorrespondenceFolderLocator folders) {
        this.nodes = Objects.requireNonNull(nodes);
        this.permissions = Objects.requireNonNull(permissions);
        this.folders = Objects.requireNonNull(folders);
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
        Objects.requireNonNull(role, "actorRole must not be null");
        if (!"inbox".equals(box) && !"sent".equals(box) && !"drafts".equals(box))
            throw new IllegalArgumentException("Unsupported mailbox box");
        if (offset < 0 || limit < 1 || limit > 100)
            throw new IllegalArgumentException("Invalid mailbox pagination");
        if (role == CorrespondenceActorRole.DIRECTOR && department == null) throw forbidden();
        if (!"all".equals(view) && !"needs_action".equals(view)
                && !"handled".equals(view) && !"archived".equals(view))
            throw new IllegalArgumentException("Invalid inbox view");

        List<MailboxSummary> matches = new ArrayList<>();
        NodeRef root = folders.locateCorrespondenceRoot();
        for (ChildAssociationRef folderAssociation : nodes.getChildAssocs(root)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(folderAssociation.getTypeQName())) continue;
            NodeRef folder = folderAssociation.getChildRef();
            if (permissions.hasPermission(folder, PermissionService.READ) != AccessStatus.ALLOWED
                    || !ContentModel.TYPE_FOLDER.equals(nodes.getType(folder))) continue;
            collect(folder, box, role, department, employeeId, view, matches);
        }

        matches.sort(Comparator.comparing(MailboxSummary::modifiedAt)
                .reversed().thenComparing(MailboxSummary::technicalId));
        int total = matches.size();
        int from = Math.min(offset, total);
        int to = (int) Math.min((long) from + limit, total);
        return new MailboxPage(matches.subList(from, to), offset, limit, total);
    }

    private void collect(NodeRef folder, String box, CorrespondenceActorRole role,
            DepartmentCode department, String employeeId, String view,
            List<MailboxSummary> matches) {
        for (ChildAssociationRef association : nodes.getChildAssocs(folder)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef node = association.getChildRef();
            if (permissions.hasPermission(node, PermissionService.READ) != AccessStatus.ALLOWED
                    || !TYPE_CORRESPONDENCE.equals(nodes.getType(node))) continue;

            CorrespondenceState state = readState(node);
            String origin = required(node, PROP_ORIGIN);
            var distributed = departmentAssignments.read(node);
            boolean directorReceived = role == CorrespondenceActorRole.DIRECTOR && department != null
                    && distributed.stream().anyMatch(a -> a.isVisibleTo(department));
            var activeAssignments = departmentAssignments.readCurrent(node);
            boolean directorNeedsAction = directorReceived
                    && activeAssignments.stream().anyMatch(a -> a.isActionableFor(department));
            boolean active = isAssigned(node, state, role, department) || directorNeedsAction;
            // Never expose an unsubmitted Director draft to other institutional roles.
            if (state == CorrespondenceState.DRAFT && role != CorrespondenceActorRole.DIRECTOR)
                continue;
            List<CorrespondenceParticipation> actions = participationReader.read(node);
            boolean received = directorReceived || actions.stream().anyMatch(a -> a.receivedBy(role, department));
            boolean sent = actions.stream().anyMatch(a -> a.sentBy(role, employeeId));
            // Director Sent has historically been departmental; retain that scope.
            if (role == CorrespondenceActorRole.DIRECTOR && department != null
                    && department.value().equals(origin) && state != CorrespondenceState.DRAFT)
                sent = true;

            boolean included = switch (box) {
                case "inbox" -> active || received;
                case "sent" -> sent;
                case "drafts" -> isDraft(node, role, department, state, actions);
                default -> false;
            };
            if (!included) continue;
            if ("inbox".equals(box)) {
                boolean matchesView = switch (view) {
                    case "needs_action" -> active;
                    case "handled" -> !active && state != CorrespondenceState.ARCHIVED;
                    case "archived" -> state == CorrespondenceState.ARCHIVED;
                    default -> true;
                };
                if (!matchesView) continue;
            }
            matches.add(new MailboxSummary(
                    required(node, PROP_TECHNICAL_ID), required(node, PROP_SUBJECT),
                    optional(node, PROP_DOCUMENT_NUMBER), state.name(), origin,
                    required(node, PROP_SENDER), timestamp(node, ContentModel.PROP_CREATED),
                    timestamp(node, ContentModel.PROP_MODIFIED), active));
        }
    }

    private boolean isDraft(NodeRef node, CorrespondenceActorRole role,
            DepartmentCode department, CorrespondenceState state,
            List<CorrespondenceParticipation> actions) {
        if (role == CorrespondenceActorRole.DIRECTOR)
            return state == CorrespondenceState.DRAFT && department != null
                    && department.value().equals(required(node, PROP_ORIGIN));
        if (!isAssigned(node, state, role, department)) return false;
        if (role == CorrespondenceActorRole.PRESIDENT) {
            if (revision(node, PROP_PRESIDENT_REVISION) < 1) return false;
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
        Instant saved = latestOfficeSnapshotAt(node);
        return saved != null && !saved.isBefore(returned);
    }

    private Instant latestOfficeSnapshotAt(NodeRef correspondence) {
        long head = revision(correspondence, PROP_OFFICE_REVISION);
        Instant time = null;
        for (ChildAssociationRef assoc : nodes.getChildAssocs(correspondence)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(assoc.getTypeQName())) continue;
            NodeRef child = assoc.getChildRef();
            if (!TYPE_OFFICE_SNAPSHOT.equals(nodes.getType(child))) continue;
            if (revision(child, PROP_OFFICE_SNAPSHOT_REVISION) != head) continue;
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
            Instant createdAt, Instant modifiedAt, boolean needsAction) {
        public MailboxSummary(String technicalId, String subject, String documentNumber,
                String state, String originDepartmentCode, String senderName,
                Instant createdAt, Instant modifiedAt) {
            this(technicalId, subject, documentNumber, state, originDepartmentCode,
                    senderName, createdAt, modifiedAt, false);
        }
    }
    public record MailboxPage(List<MailboxSummary> items, int offset, int limit, int total) {
        public MailboxPage { items = List.copyOf(items); }
    }
}
