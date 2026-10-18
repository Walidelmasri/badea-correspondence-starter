package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.sql.Date;
import java.time.Clock;
import java.util.HashMap;
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

    private static final QName PROP_STATE =
            qname("state");

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

        return new CorrespondenceAccessContext(
                state,
                originDepartmentCode,
                assignedRole,
                assignedDepartmentCode);
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

        long currentSequence =
                readLastActionSequence(
                        correspondence);

        /*
         * The Office receives the correspondence both before and after the
         * President's review. The current state alone cannot distinguish
         * those visits. Fail closed unless the latest persisted action is
         * an actual Director -> Office handoff.
         */
        if (command.action() == CorrespondenceAction.SEND_TO_PRESIDENT
                && !lastActionWasDirectorHandoff(correspondence, currentSequence)) {
            throw new InvalidCorrespondenceTransitionException(
                    fromState, command.actorRole(), command.action());
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

    private boolean lastActionWasDirectorHandoff(
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
                    && CorrespondenceAction.SEND_TO_OFFICE.name().equals(
                        nodeService.getProperty(action, PROP_ACTION_TYPE))
                    && CorrespondenceActorRole.DIRECTOR.name().equals(
                        nodeService.getProperty(action, PROP_ACTOR_ROLE))
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