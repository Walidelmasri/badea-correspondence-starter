package org.badea.correspondence.infrastructure.alfresco;

import java.sql.Date;

import java.io.Serializable;
import java.time.Clock;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CorrespondenceAccessContext;
import org.badea.correspondence.application.CorrespondenceTransitionRepository;
import org.badea.correspondence.application.CreateCorrespondenceCommand;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.InvalidCorrespondenceTransitionException;
import org.badea.correspondence.workflow.CorrespondenceState;

public final class AlfrescoCorrespondenceRepository
        implements CorrespondenceTransitionRepository {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final QName TYPE_CORRESPONDENCE =
            qname("correspondence");

    private static final QName TYPE_WORKFLOW_ACTION =
            qname("workflowAction");

    private static final QName PROP_TECHNICAL_ID =
            qname("technicalId");

    private static final QName PROP_SUBJECT =
            qname("subject");

    private static final QName PROP_BODY_TEXT =
            qname("bodyText");

    private static final QName PROP_DOCUMENT_NUMBER =
            qname("documentNumber");

    private static final QName PROP_DOCUMENT_DATE =
            qname("documentDate");

    private static final QName PROP_SOURCE_TEXT =
            qname("sourceText");

    private static final QName PROP_ORIGIN_DEPARTMENT_CODE =
            qname("originDepartmentCode");

    private static final QName PROP_CREATION_ROLE =
            qname("createdByCorrespondenceRole");

    private static final QName PROP_EXECUTIVE_COMPOSE =
            qname("executiveComposeJson");

    private static final QName PROP_STATE =
            qname("state");

    private static final QName PROP_SECRETARY_STATUS = qname("secretaryApprovalStatus");
    private static final QName PROP_SECRETARY_OWNER = qname("secretaryOwnerEmployeeId");

    private static final QName PROP_ASSIGNED_ROLE =
            qname("assignedRole");

    private static final QName PROP_ASSIGNED_DEPARTMENT_CODE =
            qname("assignedDepartmentCode");

    private static final QName PROP_CREATED_BY_EMPLOYEE_ID =
            qname("createdByEmployeeId");

    private static final QName PROP_CREATED_BY_NAME_ENGLISH =
            qname("createdByNameEnglish");

    private static final QName PROP_CREATED_BY_NAME_ARABIC =
            qname("createdByNameArabic");

    private static final QName PROP_LAST_ACTION_SEQUENCE =
            qname("lastActionSequence");

    private static final QName PROP_ACTION_SEQUENCE =
            qname("actionSequence");

    private static final QName PROP_ACTION_TYPE =
            qname("actionType");

    private static final QName PROP_FROM_STATE =
            qname("fromState");

    private static final QName PROP_TO_STATE =
            qname("toState");

    private static final QName PROP_TARGET_ROLE =
            qname("targetRole");

    private static final QName PROP_TARGET_DEPARTMENT_CODE =
            qname("targetDepartmentCode");
    private static final QName PROP_TARGET_EMPLOYEE_ID = qname("targetEmployeeId");

    private static final QName PROP_ACTOR_EMPLOYEE_ID =
            qname("actorEmployeeId");

    private static final QName PROP_ACTOR_NAME_ENGLISH =
            qname("actorNameEnglish");

    private static final QName PROP_ACTOR_NAME_ARABIC =
            qname("actorNameArabic");

    private static final QName PROP_ACTOR_ROLE =
            qname("actorRole");

    private static final QName PROP_ACTED_AT =
            qname("actedAt");

    private static final QName PROP_EXPLANATION =
            qname("explanation");

    private static final QName PROP_CLOSURE_REASON =
            qname("closureReason");

    private final NodeService nodeService;
    private final CorrespondenceFolderLocator folderLocator;
    private final CorrespondenceNodeLocator nodeLocator;
    private final Clock clock;

    public AlfrescoCorrespondenceRepository(
            NodeService nodeService,
            CorrespondenceFolderLocator folderLocator,
            CorrespondenceNodeLocator nodeLocator,
            Clock clock) {

        this.nodeService =
                Objects.requireNonNull(
                        nodeService,
                        "nodeService must not be null");

        this.folderLocator =
                Objects.requireNonNull(
                        folderLocator,
                        "folderLocator must not be null");

        this.nodeLocator =
                Objects.requireNonNull(
                        nodeLocator,
                        "nodeLocator must not be null");

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock must not be null");
    }

    public String createDraft(
            CreateCorrespondenceCommand command) {

        Objects.requireNonNull(
                command,
                "command must not be null");

        NodeRef departmentFolder =
                folderLocator.locateDepartmentFolder(
                        command.originDepartmentCode());

        String technicalId =
                UUID.randomUUID().toString();

        String folderName =
                buildFolderName(
                        command.originDepartmentCode().value(),
                        technicalId,
                        command.subject());

        Map<QName, Serializable> properties =
                new HashMap<>();

        properties.put(
                ContentModel.PROP_NAME,
                folderName);

        properties.put(
                PROP_TECHNICAL_ID,
                technicalId);

        properties.put(
                PROP_SUBJECT,
                command.subject());

        putIfPresent(
                properties,
                PROP_BODY_TEXT,
                command.bodyText());

        putIfPresent(
                properties,
                PROP_DOCUMENT_NUMBER,
                command.documentNumber());

        if (command.documentDate() != null) {
            properties.put(
                    PROP_DOCUMENT_DATE,
                    Date.valueOf(
                            command.documentDate()));
        }

        putIfPresent(
                properties,
                PROP_SOURCE_TEXT,
                command.sourceText());

        properties.put(
                PROP_ORIGIN_DEPARTMENT_CODE,
                command.originDepartmentCode().value());

        properties.put(
                PROP_STATE,
                CorrespondenceState.DRAFT.name());

        if (command.createdByRole() != CorrespondenceActorRole.DIRECTOR) {
            properties.put(PROP_CREATION_ROLE, command.createdByRole().name());
        }
        if (command.executiveComposeJson() != null) {
            properties.put(PROP_EXECUTIVE_COMPOSE, command.executiveComposeJson());
        }

        properties.put(
                PROP_CREATED_BY_EMPLOYEE_ID,
                command.createdByEmployeeId().value());

        properties.put(
                PROP_CREATED_BY_NAME_ENGLISH,
                command.createdByNameEnglish());

        putIfPresent(
                properties,
                PROP_CREATED_BY_NAME_ARABIC,
                command.createdByNameArabic());

        properties.put(
                PROP_LAST_ACTION_SEQUENCE,
                0L);

        properties.put(AlfrescoCorrespondenceCycleReader.CURRENT, 1L);

        QName associationQName =
                qname(
                        "correspondence-" + technicalId);

        ChildAssociationRef created =
                nodeService.createNode(
                        departmentFolder,
                        ContentModel.ASSOC_CONTAINS,
                        associationQName,
                        TYPE_CORRESPONDENCE,
                        properties);

        if (created == null
                || created.getChildRef() == null) {

            throw new CorrespondenceRepositoryException(
                    "Alfresco did not return the created correspondence node.");
        }

        return technicalId;
    }

    @Override
    public CorrespondenceAccessContext getAccessContext(
            String technicalId) {

        NodeRef correspondence =
                nodeLocator.locate(
                        technicalId);

        CorrespondenceState state =
                readState(
                        correspondence);

        DepartmentCode originDepartmentCode =
                readRequiredDepartmentCode(
                        correspondence,
                        PROP_ORIGIN_DEPARTMENT_CODE,
                        "originDepartmentCode");

        CorrespondenceActorRole assignedRole =
                readOptionalActorRole(
                        correspondence,
                        PROP_ASSIGNED_ROLE,
                        "assignedRole");

        DepartmentCode assignedDepartmentCode =
                readOptionalDepartmentCode(
                        correspondence,
                        PROP_ASSIGNED_DEPARTMENT_CODE,
                        "assignedDepartmentCode");

        CorrespondenceActorRole creatorRole = readOptionalActorRole(
                correspondence, PROP_CREATION_ROLE, "createdByCorrespondenceRole");
        // The creator ID is also required to protect direct Director submission.
        String creatorId = (creatorRole != null || state == CorrespondenceState.DRAFT)
                ? readOptionalCreatorEmployeeId(correspondence) : null;

        return new CorrespondenceAccessContext(
                state,
                originDepartmentCode,
                assignedRole,
                assignedDepartmentCode,
                new AlfrescoCorrespondenceParticipationReader(nodeService).read(correspondence),
                new AlfrescoDepartmentAssignmentReader(nodeService).readCurrent(correspondence),
                creatorRole,
                creatorId);
    }

    @Override
    public void applyTransition(
            TransitionCorrespondenceCommand command,
            CorrespondenceState fromState,
            CorrespondenceState toState,
            CorrespondenceActorRole targetRole) {

        Objects.requireNonNull(
                command,
                "command must not be null");

        Objects.requireNonNull(
                fromState,
                "fromState must not be null");

        Objects.requireNonNull(
                toState,
                "toState must not be null");

        NodeRef correspondence =
                nodeLocator.locate(
                        command.technicalId());

        CorrespondenceState persistedState =
                readState(
                        correspondence);

        if (persistedState != fromState) {
            throw new CorrespondenceRepositoryException(
                    "Correspondence state changed before the transition could be persisted. "
                            + "Expected "
                            + fromState
                            + " but found "
                            + persistedState
                            + ".");
        }

        // Re-read persisted assignments inside the same REQUIRED transaction.
        // A browser's earlier view is not authority to archive pending work.
        if (command.action() == CorrespondenceAction.ADMINISTRATIVE_CLOSE
                && fromState == CorrespondenceState.WITH_DIRECTOR) {
            var currentAssignments = new AlfrescoDepartmentAssignmentReader(nodeService)
                    .read(correspondence);
            if (command.actorRole() != CorrespondenceActorRole.PRESIDENT_OFFICE
                    || toState != CorrespondenceState.ARCHIVED
                    || currentAssignments.isEmpty()
                    || currentAssignments.stream().anyMatch(item ->
                            item.recipientType() == org.badea.correspondence.application.DepartmentRecipientType.TO
                            && item.status() != org.badea.correspondence.application.DepartmentAssignmentStatus.COMPLETED)) {
                throw new InvalidCorrespondenceTransitionException(
                        fromState, command.actorRole(), command.action());
            }
        }

        long currentSequence =
                readLastActionSequence(
                        correspondence);

        /*
         * The Office receives the correspondence both before and after the
         * President's review. The current state alone cannot distinguish
         * those visits. Fail closed unless the latest persisted action is
         * an actual Director -> Office handoff.
         */
        boolean directInitialSubmission = fromState == CorrespondenceState.DRAFT
                && toState == CorrespondenceState.WITH_PRESIDENT
                && command.action() == CorrespondenceAction.SEND_TO_PRESIDENT
                && (command.actorRole() == CorrespondenceActorRole.DIRECTOR
                    || (command.actorRole() == CorrespondenceActorRole.PRESIDENT_OFFICE
                        && "101".equals(nodeService.getProperty(correspondence, PROP_ORIGIN_DEPARTMENT_CODE))
                        && (CorrespondenceActorRole.PRESIDENT_OFFICE.name().equals(
                                nodeService.getProperty(correspondence, PROP_CREATION_ROLE))
                            || "PENDING".equals(nodeService.getProperty(correspondence, PROP_SECRETARY_STATUS)))));
        if (command.action() == CorrespondenceAction.SEND_TO_PRESIDENT
                && !directInitialSubmission
                && !lastActionWasForwardableOfficeArrival(correspondence, currentSequence)) {
            throw new InvalidCorrespondenceTransitionException(
                    fromState, command.actorRole(), command.action());
        }

        // Allocate exactly once, at first official submission. Existing correspondence,
        // revisions, and historical document numbers are not changed.
        if (fromState == CorrespondenceState.DRAFT
                && ((toState == CorrespondenceState.WITH_PRESIDENT_OFFICE
                        && command.action() == CorrespondenceAction.SEND_TO_OFFICE)
                    || directInitialSubmission)) {
            DepartmentCode originatingDepartment = readRequiredDepartmentCode(
                    correspondence, PROP_ORIGIN_DEPARTMENT_CODE, "originDepartmentCode");
            String reference = new AlfrescoCorrespondenceReferenceAllocator(
                    nodeService, folderLocator, clock).reserve(originatingDepartment);
            nodeService.setProperty(correspondence, PROP_DOCUMENT_NUMBER, reference);
        }

        long nextSequence =
                currentSequence + 1L;

        createWorkflowAction(
                correspondence,
                nextSequence,
                command,
                fromState,
                toState,
                targetRole);

        nodeService.setProperty(
                correspondence,
                PROP_STATE,
                toState.name());

        updateAssignment(
                correspondence,
                targetRole,
                command);

        nodeService.setProperty(
                correspondence,
                PROP_LAST_ACTION_SEQUENCE,
                nextSequence);
    }

    /**
     * Secretary drafts retain the existing DRAFT workflow state. The separate
     * creator-bound marker keeps them out of the normal departmental Drafts
     * list and blocks the ordinary Director/executive submission endpoint.
     * Called only after active Oracle secretary authorization.
     */
    public String createSecretaryDraft(CreateCorrespondenceCommand command) {
        if (command.createdByRole() != CorrespondenceActorRole.DIRECTOR) {
            throw new IllegalArgumentException("Secretary drafts use a departmental origin");
        }
        String technicalId = createDraft(command);
        NodeRef node = nodeLocator.locate(technicalId);
        nodeService.setProperty(node, PROP_CREATION_ROLE, CorrespondenceActorRole.DIRECTOR.name());
        nodeService.setProperty(node, PROP_SECRETARY_OWNER, command.createdByEmployeeId().value());
        nodeService.setProperty(node, PROP_SECRETARY_STATUS, "DRAFT");
        return technicalId;
    }

    public record SecretaryDraft(String technicalId, String unitCode, String subject,
            String bodyText, String ownerEmployeeId, String approvalStatus, String returnReason,
            String documentNumber, String createdAt, String modifiedAt,
            String state, String senderName) {
        public SecretaryDraft(String technicalId, String unitCode, String subject,
                String bodyText, String ownerEmployeeId, String approvalStatus, String returnReason,
                String documentNumber, String createdAt, String modifiedAt) {
            this(technicalId, unitCode, subject, bodyText, ownerEmployeeId, approvalStatus,
                returnReason, documentNumber, createdAt, modifiedAt, null, null);
        }

        public SecretaryDraft(String technicalId, String unitCode, String subject,
                String bodyText, String ownerEmployeeId, String approvalStatus, String returnReason) {
            this(technicalId, unitCode, subject, bodyText, ownerEmployeeId, approvalStatus,
                returnReason, null, null, null);
        }

        public SecretaryDraft(String technicalId, String unitCode, String subject,
                String bodyText, String ownerEmployeeId, String approvalStatus) {
            this(technicalId, unitCode, subject, bodyText, ownerEmployeeId, approvalStatus, null);
        }
    }

    public record SecretaryPage(List<SecretaryDraft> items, int total) {
        public SecretaryPage {
            items = List.copyOf(items);
        }
    }

    /** Owner-scoped reader supports issued correspondence; transition methods still require DRAFT. */
    public SecretaryDraft readSecretaryCase(String technicalId) {
        NodeRef node = nodeLocator.locate(technicalId);
        if (!TYPE_CORRESPONDENCE.equals(nodeService.getType(node))) {
            throw new CorrespondenceRepositoryException("Not secretary correspondence");
        }
        String status = (String) nodeService.getProperty(node, PROP_SECRETARY_STATUS);
        String owner = (String) nodeService.getProperty(node, PROP_SECRETARY_OWNER);
        Object state = nodeService.getProperty(node, PROP_STATE);
        boolean draft = CorrespondenceState.DRAFT.name().equals(state);
        if (owner == null || owner.isBlank() || (!draft && !"APPROVED".equals(status))
                || (draft && !java.util.Set.of("DRAFT", "PENDING", "RETURNED").contains(
                    status == null ? "" : status))) {
            throw new CorrespondenceRepositoryException("Not secretary correspondence");
        }
        return secretaryCase(node, status, owner);
    }

    /** Optional full Director metadata for an authenticated secretary's personal draft. */
    public record SecretaryDraftMetadata(String documentNumber, java.time.LocalDate documentDate,
            String sourceText) { }

    public SecretaryDraftMetadata readSecretaryDraftMetadata(String technicalId) {
        readSecretaryDraft(technicalId);
        NodeRef node = nodeLocator.locate(technicalId);
        Object rawDate = nodeService.getProperty(node, PROP_DOCUMENT_DATE);
        java.time.LocalDate documentDate = null;
        if (rawDate instanceof java.sql.Date sqlDate) {
            documentDate = sqlDate.toLocalDate();
        } else if (rawDate instanceof java.util.Date date) {
            documentDate = java.time.Instant.ofEpochMilli(date.getTime())
                .atZone(java.time.ZoneOffset.UTC).toLocalDate();
        }
        return new SecretaryDraftMetadata(
            (String) nodeService.getProperty(node, PROP_DOCUMENT_NUMBER), documentDate,
            (String) nodeService.getProperty(node, PROP_SOURCE_TEXT));
    }

    /** Transition-only reader must never allow editing an approved or routed case. */
    public SecretaryDraft readSecretaryDraft(String technicalId) {
        SecretaryDraft draft = readSecretaryCase(technicalId);
        NodeRef node = nodeLocator.locate(technicalId);
        if (!CorrespondenceState.DRAFT.name().equals(nodeService.getProperty(node, PROP_STATE))) {
            throw new CorrespondenceRepositoryException("Not an active secretary draft");
        }
        return draft;
    }

    private SecretaryDraft secretaryCase(NodeRef node, String status, String owner) {
        return new SecretaryDraft(
            requiredSecretaryString(node, PROP_TECHNICAL_ID),
            requiredSecretaryString(node, PROP_ORIGIN_DEPARTMENT_CODE),
            requiredSecretaryString(node, PROP_SUBJECT),
            (String) nodeService.getProperty(node, PROP_BODY_TEXT), owner, status,
            "RETURNED".equals(status) ? latestSecretaryReturnReason(node) : null,
            (String) nodeService.getProperty(node, PROP_DOCUMENT_NUMBER),
            secretaryTimestamp(node, ContentModel.PROP_CREATED),
            secretaryTimestamp(node, ContentModel.PROP_MODIFIED),
            (String) nodeService.getProperty(node, PROP_STATE),
            (String) nodeService.getProperty(node, PROP_CREATED_BY_NAME_ENGLISH));
    }

    private String secretaryTimestamp(NodeRef node, QName property) {
        Object value = nodeService.getProperty(node, property);
        if (!(value instanceof java.util.Date date)) {
            throw new CorrespondenceRepositoryException("Missing secretary correspondence timestamp");
        }
        return date.toInstant().toString();
    }

    private String latestSecretaryReturnReason(NodeRef node) {
        long latest = -1;
        String reason = null;
        for (ChildAssociationRef link : nodeService.getChildAssocs(node)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(link.getTypeQName())) continue;
            NodeRef action = link.getChildRef();
            if (action == null || !TYPE_WORKFLOW_ACTION.equals(nodeService.getType(action))
                    || !CorrespondenceAction.RETURN_TO_SECRETARY.name().equals(
                        nodeService.getProperty(action, PROP_ACTION_TYPE))) continue;
            Object sequence = nodeService.getProperty(action, PROP_ACTION_SEQUENCE);
            if (!(sequence instanceof Number number) || number.longValue() <= latest) continue;
            latest = number.longValue();
            Object explanation = nodeService.getProperty(action, PROP_EXPLANATION);
            reason = explanation instanceof String text ? text : null;
        }
        return reason;
    }

    /** Old internal caller contract remains draft-only; do not broaden manager visibility. */
    public List<SecretaryDraft> listSecretaryDrafts(
            DepartmentCode unit, String ownerEmployeeId, boolean pendingOnly) {
        NodeRef folder = folderLocator.locateDepartmentFolder(unit);
        List<SecretaryDraft> drafts = new ArrayList<>();
        for (ChildAssociationRef association : nodeService.getChildAssocs(folder)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef child = association.getChildRef();
            if (!TYPE_CORRESPONDENCE.equals(nodeService.getType(child))
                    || !CorrespondenceState.DRAFT.name().equals(nodeService.getProperty(child, PROP_STATE))) continue;
            String owner = (String) nodeService.getProperty(child, PROP_SECRETARY_OWNER);
            String status = (String) nodeService.getProperty(child, PROP_SECRETARY_STATUS);
            if (owner == null || owner.isBlank()
                    || !unit.value().equals(nodeService.getProperty(child, PROP_ORIGIN_DEPARTMENT_CODE))
                    || (pendingOnly && !"PENDING".equals(status))
                    || (!pendingOnly && !owner.equals(ownerEmployeeId))) continue;
            drafts.add(secretaryCase(child, status, owner));
        }
        drafts.sort(Comparator.comparing(SecretaryDraft::technicalId));
        return List.copyOf(drafts.subList(0, Math.min(drafts.size(), 100)));
    }

    private record SecretaryDatedCase(SecretaryDraft item, long modifiedAt) { }

    /** Bounded, newest-first personal and manager queues. Only personal view includes issued cases. */
    public SecretaryPage pageSecretaryCases(DepartmentCode unit, String ownerEmployeeId,
            boolean pendingOnly, int offset, int pageSize) {
        return pageSecretaryCases(unit, ownerEmployeeId, pendingOnly, offset, pageSize, null);
    }

    public SecretaryPage pageSecretaryCases(DepartmentCode unit, String ownerEmployeeId,
            boolean pendingOnly, int offset, int pageSize, String personalBox) {
        if (offset < 0 || pageSize < 1 || pageSize > 50 || (pendingOnly == (ownerEmployeeId != null))
                || (personalBox != null && (pendingOnly
                    || !java.util.Set.of("drafts", "sent").contains(personalBox)))) {
            throw new IllegalArgumentException("Invalid secretary listing page");
        }
        NodeRef folder = folderLocator.locateDepartmentFolder(unit);
        List<SecretaryDatedCase> cases = new ArrayList<>();
        for (ChildAssociationRef link : nodeService.getChildAssocs(folder)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(link.getTypeQName())) continue;
            var child = link.getChildRef();
            if (!TYPE_CORRESPONDENCE.equals(nodeService.getType(child))) continue;
            String owner = (String) nodeService.getProperty(child, PROP_SECRETARY_OWNER);
            String status = (String) nodeService.getProperty(child, PROP_SECRETARY_STATUS);
            Object state = nodeService.getProperty(child, PROP_STATE);
            boolean draftState = CorrespondenceState.DRAFT.name().equals(state);
            boolean editableDraft = draftState && java.util.Set.of("DRAFT", "RETURNED")
                .contains(status == null ? "" : status);
            boolean personalCase = (draftState && java.util.Set.of("DRAFT", "PENDING", "RETURNED")
                .contains(status == null ? "" : status))
                || (!draftState && "APPROVED".equals(status));
            if (owner == null || owner.isBlank()
                    || !unit.value().equals(nodeService.getProperty(child, PROP_ORIGIN_DEPARTMENT_CODE))
                    || (pendingOnly && (!"PENDING".equals(status) || !draftState))
                    || (!pendingOnly && (!owner.equals(ownerEmployeeId) || !personalCase))
                    || ("drafts".equals(personalBox) && !editableDraft)
                    || ("sent".equals(personalBox) && !(draftState && "PENDING".equals(status)
                        || !draftState && "APPROVED".equals(status)))) continue;
            Object modified = nodeService.getProperty(child, ContentModel.PROP_MODIFIED);
            long stamp = modified instanceof java.util.Date date ? date.getTime() : 0L;
            cases.add(new SecretaryDatedCase(secretaryCase(child, status, owner), stamp));
        }
        cases.sort(Comparator.comparingLong(SecretaryDatedCase::modifiedAt).reversed()
            .thenComparing(row -> row.item().technicalId()));
        int total = cases.size();
        if (offset >= total) return new SecretaryPage(List.of(), total);
        int end = (int) Math.min((long) total, (long) offset + pageSize);
        return new SecretaryPage(cases.subList(offset, end).stream()
            .map(SecretaryDatedCase::item).toList(), total);
    }

    public void reviseSecretaryDraft(TransitionCorrespondenceCommand command,
            String subject, String bodyText) {
        reviseSecretaryDraftInternal(command, subject, bodyText, null, null, null, false);
    }

    public void reviseSecretaryDraft(TransitionCorrespondenceCommand command,
            String subject, String bodyText, String documentNumber,
            java.time.LocalDate documentDate, String sourceText) {
        reviseSecretaryDraftInternal(command, subject, bodyText,
            documentNumber, documentDate, sourceText, true);
    }

    private void reviseSecretaryDraftInternal(TransitionCorrespondenceCommand command,
            String subject, String bodyText, String documentNumber,
            java.time.LocalDate documentDate, String sourceText, boolean updateMetadata) {
        NodeRef node = nodeLocator.locate(command.technicalId());
        if (!CorrespondenceState.DRAFT.name().equals(nodeService.getProperty(node, PROP_STATE))
                || !java.util.Set.of("DRAFT", "RETURNED").contains(
                    nodeService.getProperty(node, PROP_SECRETARY_STATUS))
                || !command.actorEmployeeId().value().equals(nodeService.getProperty(node, PROP_SECRETARY_OWNER))) {
            throw new CorrespondenceRepositoryException("Secretary draft is not editable");
        }
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("Subject required");
        nodeService.setProperty(node, PROP_SUBJECT, subject.trim());
        if (bodyText == null || bodyText.isBlank()) nodeService.removeProperty(node, PROP_BODY_TEXT);
        else nodeService.setProperty(node, PROP_BODY_TEXT, bodyText.trim());
        if (updateMetadata) {
            if (documentNumber == null || documentNumber.isBlank())
                nodeService.removeProperty(node, PROP_DOCUMENT_NUMBER);
            else nodeService.setProperty(node, PROP_DOCUMENT_NUMBER, documentNumber.trim());
            if (documentDate == null)
                nodeService.removeProperty(node, PROP_DOCUMENT_DATE);
            else nodeService.setProperty(node, PROP_DOCUMENT_DATE, Date.valueOf(documentDate));
            if (sourceText == null || sourceText.isBlank())
                nodeService.removeProperty(node, PROP_SOURCE_TEXT);
            else nodeService.setProperty(node, PROP_SOURCE_TEXT, sourceText.trim());
        }
        String previousStatus = (String) nodeService.getProperty(node, PROP_SECRETARY_STATUS);
        changeSecretaryStatus(command, previousStatus, "DRAFT", CorrespondenceActorRole.DIRECTOR);
    }

    /** Same REQUIRED Alfresco transaction for state and append-only history. */
    public void changeSecretaryStatus(TransitionCorrespondenceCommand command,
            String expectedStatus, String nextStatus, CorrespondenceActorRole targetRole) {
        NodeRef node = nodeLocator.locate(command.technicalId());
        if (!CorrespondenceState.DRAFT.name().equals(nodeService.getProperty(node, PROP_STATE))
                || !expectedStatus.equals(nodeService.getProperty(node, PROP_SECRETARY_STATUS))) {
            throw new CorrespondenceRepositoryException("Secretary approval was already changed");
        }
        long sequence = readLastActionSequence(node) + 1L;
        createWorkflowAction(node, sequence, command, CorrespondenceState.DRAFT,
                CorrespondenceState.DRAFT, targetRole);
        if (command.action() == CorrespondenceAction.RETURN_TO_SECRETARY) {
            String owner = requiredSecretaryString(node, PROP_SECRETARY_OWNER);
            String actionName = String.format("action-%06d", sequence);
            for (ChildAssociationRef association : nodeService.getChildAssocs(node)) {
                if (qname(actionName).equals(association.getQName())) {
                    nodeService.setProperty(association.getChildRef(), PROP_TARGET_EMPLOYEE_ID, owner);
                    break;
                }
            }
        }
        nodeService.setProperty(node, PROP_SECRETARY_STATUS, nextStatus);
        nodeService.setProperty(node, PROP_LAST_ACTION_SEQUENCE, sequence);
    }

    public void approveSecretaryDraft(TransitionCorrespondenceCommand command,
            CorrespondenceState nextState, CorrespondenceActorRole targetRole) {
        NodeRef node = nodeLocator.locate(command.technicalId());
        if (!CorrespondenceState.DRAFT.name().equals(nodeService.getProperty(node, PROP_STATE))
                || !"PENDING".equals(nodeService.getProperty(node, PROP_SECRETARY_STATUS))
                || nodeService.getProperty(node, PROP_SECRETARY_OWNER) == null) {
            throw new CorrespondenceRepositoryException("Secretary approval no longer pending");
        }
        // applyTransition reserves the reference, writes audited history, and
        // changes the assignment atomically with this status in REQUIRED txn.
        applyTransition(command, CorrespondenceState.DRAFT, nextState, targetRole);
        nodeService.setProperty(node, PROP_SECRETARY_STATUS, "APPROVED");
    }

    private String requiredSecretaryString(NodeRef node, QName key) {
        Object raw = nodeService.getProperty(node, key);
        if (!(raw instanceof String text) || text.isBlank()) {
            throw new CorrespondenceRepositoryException("Incomplete secretary draft metadata");
        }
        return text;
    }

    /** Append-only completion event; does not change parent assignment or state. */
    public void recordDepartmentCompletion(TransitionCorrespondenceCommand command) {
        NodeRef parent = nodeLocator.locate(command.technicalId());
        CorrespondenceState state = readState(parent);
        if (state != CorrespondenceState.WITH_DIRECTOR
                || command.action() != CorrespondenceAction.COMPLETE_DEPARTMENT_ASSIGNMENT) {
            throw new CorrespondenceRepositoryException("Invalid departmental completion state");
        }
        long sequence = readLastActionSequence(parent) + 1L;
        createWorkflowAction(parent, sequence, command, state, state,
                CorrespondenceActorRole.PRESIDENT_OFFICE);
        nodeService.setProperty(parent, PROP_LAST_ACTION_SEQUENCE, sequence);
    }

    private boolean lastActionWasForwardableOfficeArrival(
            NodeRef correspondence, long lastSequence) {

        if (lastSequence < 1L) {
            return false;
        }

        String expectedName = String.format("action-%06d", lastSequence);

        for (ChildAssociationRef association :
                nodeService.getChildAssocs(correspondence)) {

            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())
                    || !qname(expectedName).equals(association.getQName())) {
                continue;
            }

            NodeRef action = association.getChildRef();
            if (!TYPE_WORKFLOW_ACTION.equals(nodeService.getType(action))) {
                return false;
            }

            Serializable sequence = nodeService.getProperty(action, PROP_ACTION_SEQUENCE);
            return sequence instanceof Number number
                    && number.longValue() == lastSequence
                    && ((CorrespondenceAction.SEND_TO_OFFICE.name().equals(
                        nodeService.getProperty(action, PROP_ACTION_TYPE))
                            && CorrespondenceActorRole.DIRECTOR.name().equals(
                                nodeService.getProperty(action, PROP_ACTOR_ROLE)))
                        || (CorrespondenceAction.CONTINUE_CORRESPONDENCE.name().equals(
                            nodeService.getProperty(action, PROP_ACTION_TYPE))
                            && CorrespondenceActorRole.PRESIDENT_OFFICE.name().equals(
                                nodeService.getProperty(action, PROP_ACTOR_ROLE)))
                        || (CorrespondenceAction.DIRECTOR_CONTINUE_CORRESPONDENCE.name().equals(
                            nodeService.getProperty(action, PROP_ACTION_TYPE))
                            && CorrespondenceActorRole.DIRECTOR.name().equals(
                                nodeService.getProperty(action, PROP_ACTOR_ROLE)))
                        // Newly originated executive correspondence is an eligible
                        // first arrival at Office, unlike a returned President review.
                        || (CorrespondenceAction.SEND_TO_OFFICE.name().equals(
                            nodeService.getProperty(action, PROP_ACTION_TYPE))
                            && CorrespondenceState.DRAFT.name().equals(
                                nodeService.getProperty(action, PROP_FROM_STATE))
                            && (CorrespondenceActorRole.PRESIDENT.name().equals(
                                nodeService.getProperty(action, PROP_ACTOR_ROLE))
                                || CorrespondenceActorRole.PRESIDENT_OFFICE.name().equals(
                                    nodeService.getProperty(action, PROP_ACTOR_ROLE)))
                            && Objects.equals(nodeService.getProperty(correspondence, PROP_CREATION_ROLE),
                                    nodeService.getProperty(action, PROP_ACTOR_ROLE))))
                    && CorrespondenceState.WITH_PRESIDENT_OFFICE.name().equals(
                        nodeService.getProperty(action, PROP_TO_STATE));
        }
        return false;
    }

    private DepartmentCode readRequiredDepartmentCode(
            NodeRef correspondence,
            QName property,
            String propertyName) {

        Serializable value =
                nodeService.getProperty(
                        correspondence,
                        property);

        if (!(value instanceof String text)
                || text.isBlank()) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence has no valid "
                            + propertyName
                            + ".");
        }

        try {
            return new DepartmentCode(
                    text);
        } catch (IllegalArgumentException exception) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence has invalid "
                            + propertyName
                            + ": "
                            + text,
                    exception);
        }
    }

    private DepartmentCode readOptionalDepartmentCode(
            NodeRef correspondence,
            QName property,
            String propertyName) {

        Serializable value =
                nodeService.getProperty(
                        correspondence,
                        property);

        if (value == null) {
            return null;
        }

        if (!(value instanceof String text)
                || text.isBlank()) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence has invalid "
                            + propertyName
                            + ".");
        }

        try {
            return new DepartmentCode(
                    text);
        } catch (IllegalArgumentException exception) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence has invalid "
                            + propertyName
                            + ": "
                            + text,
                    exception);
        }
    }

    private String readOptionalCreatorEmployeeId(NodeRef correspondence) {
        Serializable raw = nodeService.getProperty(correspondence, PROP_CREATED_BY_EMPLOYEE_ID);
        if (raw == null) return null;
        if (!(raw instanceof String text) || text.isBlank()) {
            throw new CorrespondenceRepositoryException("Invalid correspondence creator employee ID");
        }
        return text;
    }

    private CorrespondenceActorRole readOptionalActorRole(
            NodeRef correspondence,
            QName property,
            String propertyName) {

        Serializable value =
                nodeService.getProperty(
                        correspondence,
                        property);

        if (value == null) {
            return null;
        }

        if (!(value instanceof String text)
                || text.isBlank()) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence has invalid "
                            + propertyName
                            + ".");
        }

        try {
            return CorrespondenceActorRole.valueOf(
                    text);
        } catch (IllegalArgumentException exception) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence has unknown "
                            + propertyName
                            + ": "
                            + text,
                    exception);
        }
    }

    private CorrespondenceState readState(
            NodeRef correspondence) {

        Serializable value =
                nodeService.getProperty(
                        correspondence,
                        PROP_STATE);

        if (!(value instanceof String stateValue)
                || stateValue.isBlank()) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence has no valid persisted workflow state.");
        }

        try {
            return CorrespondenceState.valueOf(
                    stateValue);
        } catch (IllegalArgumentException exception) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence has unknown workflow state: "
                            + stateValue,
                    exception);
        }
    }

    private long readLastActionSequence(
            NodeRef correspondence) {

        Serializable value =
                nodeService.getProperty(
                        correspondence,
                        PROP_LAST_ACTION_SEQUENCE);

        if (value == null) {
            return 0L;
        }

        if (!(value instanceof Number number)) {
            throw new CorrespondenceRepositoryException(
                    "Correspondence has an invalid last action sequence.");
        }

        long sequence =
                number.longValue();

        if (sequence < 0L) {
            throw new CorrespondenceRepositoryException(
                    "Correspondence has a negative last action sequence.");
        }

        return sequence;
    }

    private void createWorkflowAction(
            NodeRef correspondence,
            long sequence,
            TransitionCorrespondenceCommand command,
            CorrespondenceState fromState,
            CorrespondenceState toState,
            CorrespondenceActorRole targetRole) {

        String actionName =
                String.format(
                        "action-%06d",
                        sequence);

        Map<QName, Serializable> properties =
                new HashMap<>();

        properties.put(
                ContentModel.PROP_NAME,
                actionName);

        properties.put(
                PROP_ACTION_SEQUENCE,
                sequence);

        properties.put(
                PROP_ACTION_TYPE,
                command.action().name());

        properties.put(
                PROP_FROM_STATE,
                fromState.name());

        properties.put(
                PROP_TO_STATE,
                toState.name());

        if (targetRole != null) {
            properties.put(
                    PROP_TARGET_ROLE,
                    targetRole.name());
        }

        if (command.targetDepartmentCode() != null) {
            properties.put(
                    PROP_TARGET_DEPARTMENT_CODE,
                    command.targetDepartmentCode().value());
        }

        properties.put(
                PROP_ACTOR_EMPLOYEE_ID,
                command.actorEmployeeId().value());

        properties.put(
                PROP_ACTOR_NAME_ENGLISH,
                command.actorNameEnglish());

        putIfPresent(
                properties,
                PROP_ACTOR_NAME_ARABIC,
                command.actorNameArabic());

        properties.put(
                PROP_ACTOR_ROLE,
                command.actorRole().name());

        properties.put(
                PROP_ACTED_AT,
                java.util.Date.from(
                        clock.instant()));

        putIfPresent(
                properties,
                PROP_EXPLANATION,
                command.explanation());

        putIfPresent(
                properties,
                PROP_CLOSURE_REASON,
                command.closureReason());

        ChildAssociationRef created =
                nodeService.createNode(
                        correspondence,
                        ContentModel.ASSOC_CONTAINS,
                        qname(actionName),
                        TYPE_WORKFLOW_ACTION,
                        properties);

        if (created == null
                || created.getChildRef() == null) {

            throw new CorrespondenceRepositoryException(
                    "Alfresco did not return the created workflow action node.");
        }
    }

    private void updateAssignment(
            NodeRef correspondence,
            CorrespondenceActorRole targetRole,
            TransitionCorrespondenceCommand command) {

        if (targetRole == null) {
            nodeService.removeProperty(
                    correspondence,
                    PROP_ASSIGNED_ROLE);

            nodeService.removeProperty(
                    correspondence,
                    PROP_ASSIGNED_DEPARTMENT_CODE);

            return;
        }

        nodeService.setProperty(
                correspondence,
                PROP_ASSIGNED_ROLE,
                targetRole.name());

        if (targetRole
                == CorrespondenceActorRole.DIRECTOR) {

            if (command.action() == CorrespondenceAction.DISTRIBUTE_TO_DEPARTMENTS) {
                nodeService.removeProperty(correspondence, PROP_ASSIGNED_DEPARTMENT_CODE);
                return;
            }
            if (command.targetDepartmentCode() == null) {
                throw new CorrespondenceRepositoryException(
                        "Director assignment requires a target department.");
            }

            nodeService.setProperty(
                    correspondence,
                    PROP_ASSIGNED_DEPARTMENT_CODE,
                    command.targetDepartmentCode().value());

            return;
        }

        nodeService.removeProperty(
                correspondence,
                PROP_ASSIGNED_DEPARTMENT_CODE);
    }

    private static String buildFolderName(
            String originDepartmentCode,
            String technicalId,
            String subject) {

        String safeSubject =
                sanitizeFolderNamePart(
                        subject);

        return originDepartmentCode
                + " - "
                + technicalId
                + " - "
                + safeSubject;
    }

    private static String sanitizeFolderNamePart(
            String value) {

        String sanitized =
                value
                        .replace("/", "-")
                        .replace("\\", "-")
                        .replace(":", "-")
                        .replace("*", "-")
                        .replace("?", "")
                        .replace("\"", "'")
                        .replace("<", "")
                        .replace(">", "")
                        .replace("|", "-")
                        .trim();

        if (sanitized.length() > 120) {
            return sanitized
                    .substring(0, 120)
                    .trim();
        }

        return sanitized;
    }

    private static void putIfPresent(
            Map<QName, Serializable> properties,
            QName property,
            String value) {

        if (value != null
                && !value.isBlank()) {

            properties.put(
                    property,
                    value);
        }
    }

    private static QName qname(
            String localName) {

        return QName.createQName(
                NAMESPACE_URI,
                localName);
    }
}