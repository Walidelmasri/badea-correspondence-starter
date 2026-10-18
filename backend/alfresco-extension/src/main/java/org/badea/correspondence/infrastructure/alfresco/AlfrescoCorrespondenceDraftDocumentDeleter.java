package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;

import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

/**
 * Deletes original PDF attachments from Director drafts.
 *
 * This operation must execute inside an Alfresco write transaction.
 *
 * Generated documents, workflow records and documents belonging
 * to other correspondence cannot be deleted through this operation.
 */
public final class AlfrescoCorrespondenceDraftDocumentDeleter {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final QName TYPE_CORRESPONDENCE =
            qname("correspondence");

    private static final QName PROP_STATE =
            qname("state");

    private static final QName PROP_ORIGIN_DEPARTMENT =
            qname("originDepartmentCode");

    private static final String SOURCE_DOCUMENT_PREFIX =
            "source-document-";

    private final NodeService nodeService;
    private final PermissionService permissionService;
    private final CorrespondenceNodeLocator nodeLocator;

    public AlfrescoCorrespondenceDraftDocumentDeleter(
            NodeService nodeService,
            PermissionService permissionService,
            CorrespondenceNodeLocator nodeLocator) {

        this.nodeService =
                Objects.requireNonNull(nodeService);

        this.permissionService =
                Objects.requireNonNull(permissionService);

        this.nodeLocator =
                Objects.requireNonNull(nodeLocator);
    }

    public void deleteDocument(
            String technicalId,
            String documentNodeId,
            CorrespondenceActorRole actorRole,
            DepartmentCode actorDepartmentCode) {

        Objects.requireNonNull(
                technicalId,
                "technicalId");

        Objects.requireNonNull(
                actorRole,
                "actorRole");

        String validatedDocumentId =
                validateDocumentId(documentNodeId);

        if (actorRole != CorrespondenceActorRole.DIRECTOR
                || actorDepartmentCode == null) {

            throw forbidden();
        }

        NodeRef correspondence =
                nodeLocator.locate(technicalId);

        if (!TYPE_CORRESPONDENCE.equals(
                nodeService.getType(correspondence))) {

            throw new CorrespondenceRepositoryException(
                    "The resolved node is not correspondence.");
        }

        authorizeDraft(
                correspondence,
                actorDepartmentCode);

        NodeRef document =
                locateSourceDocument(
                        correspondence,
                        validatedDocumentId);

        if (!nodeService.exists(document)
                || !ContentModel.TYPE_CONTENT.equals(
                        nodeService.getType(document))) {

            throw new CorrespondenceDocumentNotFoundException();
        }

        if (permissionService.hasPermission(
                document,
                PermissionService.DELETE) != AccessStatus.ALLOWED) {

            throw forbidden();
        }

        /*
         * Alfresco handles the underlying deletion lifecycle.
         * This does not claim permanent physical erasure.
         */
        nodeService.deleteNode(document);
    }

    private void authorizeDraft(
            NodeRef correspondence,
            DepartmentCode actorDepartmentCode) {

        Serializable state =
                nodeService.getProperty(
                        correspondence,
                        PROP_STATE);

        if (!CorrespondenceState.DRAFT.name().equals(state)) {
            throw forbidden();
        }

        Serializable originDepartment =
                nodeService.getProperty(
                        correspondence,
                        PROP_ORIGIN_DEPARTMENT);

        if (!actorDepartmentCode.value().equals(originDepartment)) {
            throw forbidden();
        }

        if (permissionService.hasPermission(
                correspondence,
                PermissionService.WRITE) != AccessStatus.ALLOWED) {

            throw forbidden();
        }
    }

    private NodeRef locateSourceDocument(
            NodeRef correspondence,
            String documentNodeId) {

        for (ChildAssociationRef association :
                nodeService.getChildAssocs(correspondence)) {

            if (!ContentModel.ASSOC_CONTAINS.equals(
                    association.getTypeQName())) {
                continue;
            }

            QName associationName =
                    association.getQName();

            if (associationName == null
                    || !NAMESPACE_URI.equals(
                            associationName.getNamespaceURI())
                    || !associationName.getLocalName()
                            .startsWith(SOURCE_DOCUMENT_PREFIX)) {

                continue;
            }

            NodeRef child =
                    association.getChildRef();

            if (child != null
                    && documentNodeId.equals(child.getId())) {

                return child;
            }
        }

        /*
         * Never resolve an arbitrary node directly from
         * the request's node ID.
         *
         * It must be a source-document child of this
         * particular correspondence.
         */
        throw new CorrespondenceDocumentNotFoundException();
    }

    private static String validateDocumentId(
            String documentNodeId) {

        if (documentNodeId == null
                || documentNodeId.isBlank()) {

            throw new IllegalArgumentException(
                    "Document node ID is required.");
        }

        String value =
                documentNodeId.trim();

        try {
            UUID.fromString(value);

        } catch (IllegalArgumentException exception) {

            throw new IllegalArgumentException(
                    "Document node ID must be a valid UUID.",
                    exception);
        }

        return value;
    }

    private static CorrespondenceAuthorizationException forbidden() {
        return new CorrespondenceAuthorizationException(
                "The authenticated Director cannot delete this document.");
    }

    private static QName qname(String localName) {
        return QName.createQName(
                NAMESPACE_URI,
                localName);
    }
}