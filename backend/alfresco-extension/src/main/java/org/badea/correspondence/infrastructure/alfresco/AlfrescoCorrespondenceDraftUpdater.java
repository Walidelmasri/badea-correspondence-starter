package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.sql.Date;
import java.util.Objects;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;

import org.badea.correspondence.application.UpdateCorrespondenceDraftCommand;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

public final class AlfrescoCorrespondenceDraftUpdater {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final QName PROP_STATE = qname("state");

    private static final QName PROP_ORIGIN_DEPARTMENT =
            qname("originDepartmentCode");

    private static final QName PROP_CREATED_BY_EMPLOYEE =
            qname("createdByEmployeeId");
    private static final QName PROP_CREATION_ROLE =
            qname("createdByCorrespondenceRole");
    private static final QName PROP_EXECUTIVE_COMPOSE =
            qname("executiveComposeJson");

    private static final QName PROP_SUBJECT = qname("subject");
    private static final QName PROP_BODY = qname("bodyText");
    private static final QName PROP_DOCUMENT_NUMBER = qname("documentNumber");
    private static final QName PROP_DOCUMENT_DATE = qname("documentDate");
    private static final QName PROP_SOURCE = qname("sourceText");

    private final NodeService nodeService;
    private final PermissionService permissionService;
    private final CorrespondenceNodeLocator nodeLocator;

    public AlfrescoCorrespondenceDraftUpdater(
            NodeService nodeService,
            PermissionService permissionService,
            CorrespondenceNodeLocator nodeLocator) {

        this.nodeService = Objects.requireNonNull(nodeService);
        this.permissionService = Objects.requireNonNull(permissionService);
        this.nodeLocator = Objects.requireNonNull(nodeLocator);
    }

    /**
     * Updates only editable metadata.
     *
     * Must execute inside an Alfresco write transaction.
     */
    public void updateDraft(UpdateCorrespondenceDraftCommand command) {
        Objects.requireNonNull(command, "command");

        if (command.actorRole() != CorrespondenceActorRole.DIRECTOR
                && command.actorRole() != CorrespondenceActorRole.PRESIDENT
                && command.actorRole() != CorrespondenceActorRole.PRESIDENT_OFFICE) {
            throw forbidden();
        }
        if (command.actorRole() != CorrespondenceActorRole.DIRECTOR
                && (command.executiveComposeJson() == null
                        || command.executiveComposeJson().isBlank())) {
            throw forbidden();
        }
        if (command.actorRole() == CorrespondenceActorRole.DIRECTOR
                && command.executiveComposeJson() != null) {
            throw forbidden();
        }

        NodeRef correspondence = nodeLocator.locate(command.technicalId());

        // No writes occur before all authorization checks succeed.
        requireOwnedDraft(correspondence, command);

        nodeService.setProperty(
                correspondence,
                PROP_SUBJECT,
                command.subject());

        nodeService.setProperty(
                correspondence,
                PROP_BODY,
                command.bodyText());

        nodeService.setProperty(
                correspondence,
                PROP_DOCUMENT_NUMBER,
                command.documentNumber());

        nodeService.setProperty(
                correspondence,
                PROP_DOCUMENT_DATE,
                command.documentDate() == null
                        ? null
                        : Date.valueOf(command.documentDate()));

        nodeService.setProperty(
                correspondence,
                PROP_SOURCE,
                command.sourceText());

        if (command.actorRole() != CorrespondenceActorRole.DIRECTOR) {
            nodeService.setProperty(
                    correspondence,
                    PROP_EXECUTIVE_COMPOSE,
                    command.executiveComposeJson());
        }
    }

    private void requireOwnedDraft(
            NodeRef correspondence,
            UpdateCorrespondenceDraftCommand command) {

        Serializable state = nodeService.getProperty(
                correspondence,
                PROP_STATE);

        if (!CorrespondenceState.DRAFT.name().equals(state)) {
            throw forbidden();
        }

        Serializable originDepartment = nodeService.getProperty(
                correspondence,
                PROP_ORIGIN_DEPARTMENT);

        if (!command.actorDepartmentCode()
                .value()
                .equals(originDepartment)) {

            throw forbidden();
        }

        // Retain the existing Director department-scoped editing policy.
        // Executive drafts are private to their authenticated creator and role.
        if (command.actorRole() != CorrespondenceActorRole.DIRECTOR) {
            Serializable creationRole = nodeService.getProperty(
                    correspondence, PROP_CREATION_ROLE);
            Serializable creator = nodeService.getProperty(
                    correspondence, PROP_CREATED_BY_EMPLOYEE);
            if (!command.actorRole().name().equals(creationRole)
                    || !command.actorEmployeeId().value().equals(creator)) {
                throw forbidden();
            }
        }

        if (permissionService.hasPermission(
                correspondence,
                PermissionService.WRITE) != AccessStatus.ALLOWED) {

            throw forbidden();
        }
    }

    private static CorrespondenceAuthorizationException forbidden() {
        return new CorrespondenceAuthorizationException(
                "The authenticated employee cannot modify this draft.");
    }

    private static QName qname(String localName) {
        return QName.createQName(
                NAMESPACE_URI,
                localName);
    }
}