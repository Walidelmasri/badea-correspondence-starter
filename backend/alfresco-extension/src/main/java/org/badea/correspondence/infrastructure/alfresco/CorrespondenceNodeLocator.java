package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;

public final class CorrespondenceNodeLocator {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final QName TYPE_CORRESPONDENCE =
            QName.createQName(
                    NAMESPACE_URI,
                    "correspondence");

    private static final QName PROP_TECHNICAL_ID =
            QName.createQName(
                    NAMESPACE_URI,
                    "technicalId");

    private final NodeService nodeService;
    private final CorrespondenceFolderLocator folderLocator;

    public CorrespondenceNodeLocator(
            NodeService nodeService,
            CorrespondenceFolderLocator folderLocator) {

        this.nodeService = Objects.requireNonNull(
                nodeService,
                "nodeService must not be null");

        this.folderLocator = Objects.requireNonNull(
                folderLocator,
                "folderLocator must not be null");
    }

    public NodeRef locate(String technicalId) {

        String validatedTechnicalId =
                validateTechnicalId(technicalId);

        NodeRef correspondenceRoot =
                folderLocator.locateCorrespondenceRoot();

        QName correspondenceAssociationQName =
                QName.createQName(
                        NAMESPACE_URI,
                        "correspondence-" + validatedTechnicalId);

        List<NodeRef> matches =
                new ArrayList<>();

        for (ChildAssociationRef departmentAssociation :
                nodeService.getChildAssocs(correspondenceRoot)) {

            NodeRef departmentFolder =
                    departmentAssociation.getChildRef();

            if (!ContentModel.TYPE_FOLDER.equals(
                    nodeService.getType(departmentFolder))) {
                continue;
            }

            List<ChildAssociationRef> correspondenceAssociations =
                    nodeService.getChildAssocs(
                            departmentFolder,
                            ContentModel.ASSOC_CONTAINS,
                            correspondenceAssociationQName);

            for (ChildAssociationRef correspondenceAssociation :
                    correspondenceAssociations) {

                NodeRef correspondenceNode =
                        correspondenceAssociation.getChildRef();

                if (!TYPE_CORRESPONDENCE.equals(
                        nodeService.getType(correspondenceNode))) {
                    continue;
                }

                Serializable storedTechnicalId =
                        nodeService.getProperty(
                                correspondenceNode,
                                PROP_TECHNICAL_ID);

                if (validatedTechnicalId.equals(
                        storedTechnicalId)) {

                    matches.add(
                            correspondenceNode);
                }
            }
        }

        if (matches.isEmpty()) {
            throw new CorrespondenceRepositoryException(
                    "Correspondence does not exist: "
                            + validatedTechnicalId);
        }

        if (matches.size() > 1) {
            throw new CorrespondenceRepositoryException(
                    "Multiple correspondence nodes exist for technical ID: "
                            + validatedTechnicalId);
        }

        return matches.getFirst();
    }

    private static String validateTechnicalId(
            String technicalId) {

        if (technicalId == null
                || technicalId.isBlank()) {

            throw new IllegalArgumentException(
                    "technicalId must not be blank");
        }

        String value =
                technicalId.trim();

        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "technicalId must be a valid UUID",
                    exception);
        }

        return value;
    }
}