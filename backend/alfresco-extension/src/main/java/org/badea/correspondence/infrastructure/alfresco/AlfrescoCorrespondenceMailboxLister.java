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
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

/**
 * Read-only Inbox and initial Director Sent listing.
 *
 * Inbox = current workflow assignment, not physical folder location.
 * Sent = submitted correspondence originated by the Director's department.
 * The underlying nodes never move, and existing documents are not copied.
 *
 * Note: scanning is acceptable for this release's small repository; replace
 * with indexed search before high-volume rollout.
 */
public final class AlfrescoCorrespondenceMailboxLister {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final QName TYPE_CORRESPONDENCE = qname("correspondence");
    private static final QName PROP_TECHNICAL_ID = qname("technicalId");
    private static final QName PROP_STATE = qname("state");
    private static final QName PROP_ORIGIN = qname("originDepartmentCode");
    private static final QName PROP_ASSIGNED_ROLE = qname("assignedRole");
    private static final QName PROP_ASSIGNED_DEPARTMENT = qname("assignedDepartmentCode");
    private static final QName PROP_SUBJECT = qname("subject");
    private static final QName PROP_DOCUMENT_NUMBER = qname("documentNumber");
    private static final QName PROP_SENDER = qname("createdByNameEnglish");

    private final NodeService nodeService;
    private final PermissionService permissionService;
    private final CorrespondenceFolderLocator folderLocator;

    public AlfrescoCorrespondenceMailboxLister(
            NodeService nodeService,
            PermissionService permissionService,
            CorrespondenceFolderLocator folderLocator) {

        this.nodeService = Objects.requireNonNull(nodeService);
        this.permissionService = Objects.requireNonNull(permissionService);
        this.folderLocator = Objects.requireNonNull(folderLocator);
    }

    public MailboxPage list(
            String box,
            CorrespondenceActorRole actorRole,
            DepartmentCode actorDepartment,
            int offset,
            int limit) {

        Objects.requireNonNull(actorRole, "actorRole must not be null");

        if (!"inbox".equals(box) && !"sent".equals(box)) {
            throw new IllegalArgumentException("box must be inbox or sent");
        }
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid mailbox pagination");
        }
        if (actorRole == CorrespondenceActorRole.DIRECTOR
                && actorDepartment == null) {
            throw forbidden();
        }
        // Office and President sent history will use action-level provenance;
        // do not return unrelated institution-wide records as their 'Sent'.
        if ("sent".equals(box)
                && actorRole != CorrespondenceActorRole.DIRECTOR) {
            throw forbidden();
        }

        List<MailboxSummary> matches = new ArrayList<>();

        if ("sent".equals(box)) {
            NodeRef department = folderLocator.locateDepartmentFolder(actorDepartment);
            collect(department, box, actorRole, actorDepartment, matches);
        } else {
            NodeRef root = folderLocator.locateCorrespondenceRoot();
            for (ChildAssociationRef folderAssociation : nodeService.getChildAssocs(root)) {
                if (!ContentModel.ASSOC_CONTAINS.equals(folderAssociation.getTypeQName())) {
                    continue;
                }
                NodeRef folder = folderAssociation.getChildRef();
                if (permissionService.hasPermission(
                        folder, PermissionService.READ) != AccessStatus.ALLOWED) {
                    continue;
                }
                if (!ContentModel.TYPE_FOLDER.equals(nodeService.getType(folder))) {
                    continue;
                }
                collect(folder, box, actorRole, actorDepartment, matches);
            }
        }

        matches.sort(Comparator.comparing(MailboxSummary::modifiedAt)
                .reversed().thenComparing(MailboxSummary::technicalId));

        int total = matches.size();
        int from = Math.min(offset, total);
        int to = (int) Math.min((long) from + limit, total);
        return new MailboxPage(matches.subList(from, to), offset, limit, total);
    }

    private void collect(
            NodeRef departmentFolder,
            String box,
            CorrespondenceActorRole role,
            DepartmentCode actorDepartment,
            List<MailboxSummary> matches) {

        for (ChildAssociationRef assoc : nodeService.getChildAssocs(departmentFolder)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(assoc.getTypeQName())) {
                continue;
            }
            NodeRef node = assoc.getChildRef();
            if (permissionService.hasPermission(
                    node, PermissionService.READ) != AccessStatus.ALLOWED) {
                continue;
            }
            if (!TYPE_CORRESPONDENCE.equals(nodeService.getType(node))) {
                continue;
            }

            CorrespondenceState state = readState(node);
            String origin = required(node, PROP_ORIGIN);
            if ("sent".equals(box)) {
                if (state == CorrespondenceState.DRAFT
                        || !actorDepartment.value().equals(origin)) {
                    continue;
                }
            } else if (!isAssignedToActor(node, state, role, actorDepartment)) {
                continue;
            }

            matches.add(new MailboxSummary(
                    required(node, PROP_TECHNICAL_ID),
                    required(node, PROP_SUBJECT),
                    optional(node, PROP_DOCUMENT_NUMBER),
                    state.name(),
                    origin,
                    required(node, PROP_SENDER),
                    timestamp(node, ContentModel.PROP_CREATED),
                    timestamp(node, ContentModel.PROP_MODIFIED)));
        }
    }

    private boolean isAssignedToActor(
            NodeRef node,
            CorrespondenceState state,
            CorrespondenceActorRole role,
            DepartmentCode department) {

        String assignedRole = optional(node, PROP_ASSIGNED_ROLE);
        if (!role.name().equals(assignedRole)) {
            return false;
        }
        return switch (role) {
            case DIRECTOR -> state == CorrespondenceState.WITH_DIRECTOR
                    && department != null
                    && department.value().equals(optional(node, PROP_ASSIGNED_DEPARTMENT));
            case PRESIDENT_OFFICE -> state == CorrespondenceState.WITH_PRESIDENT_OFFICE;
            case PRESIDENT -> state == CorrespondenceState.WITH_PRESIDENT;
        };
    }

    private CorrespondenceState readState(NodeRef node) {
        String raw = required(node, PROP_STATE);
        try {
            return CorrespondenceState.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            throw new CorrespondenceRepositoryException(
                    "Unknown correspondence state", exception);
        }
    }

    private String required(NodeRef node, QName property) {
        String value = optional(node, property);
        if (value == null || value.isBlank()) {
            throw new CorrespondenceRepositoryException(
                    "Missing correspondence metadata: " + property.getLocalName());
        }
        return value;
    }

    private String optional(NodeRef node, QName property) {
        Serializable value = nodeService.getProperty(node, property);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new CorrespondenceRepositoryException(
                    "Invalid correspondence metadata: " + property.getLocalName());
        }
        return text;
    }

    private Instant timestamp(NodeRef node, QName property) {
        Serializable value = nodeService.getProperty(node, property);
        if (!(value instanceof Date date)) {
            throw new CorrespondenceRepositoryException("Invalid correspondence timestamp");
        }
        return date.toInstant();
    }

    private static CorrespondenceAuthorizationException forbidden() {
        return new CorrespondenceAuthorizationException(
                "Authenticated user cannot access this mailbox");
    }

    private static QName qname(String localName) {
        return QName.createQName(NAMESPACE_URI, localName);
    }

    public record MailboxSummary(
            String technicalId,
            String subject,
            String documentNumber,
            String state,
            String originDepartmentCode,
            String senderName,
            Instant createdAt,
            Instant modifiedAt) {}

    public record MailboxPage(
            List<MailboxSummary> items,
            int offset,
            int limit,
            int total) {
        public MailboxPage {
            items = List.copyOf(items);
        }
    }
}
