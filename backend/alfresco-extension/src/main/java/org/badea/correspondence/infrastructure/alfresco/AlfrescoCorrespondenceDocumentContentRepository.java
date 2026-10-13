package org.badea.correspondence.infrastructure.alfresco;

import java.io.InputStream;
import java.util.Objects;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;

import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;

import org.badea.correspondence.application.CorrespondenceDocumentContentRepository;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

public final class AlfrescoCorrespondenceDocumentContentRepository
        implements CorrespondenceDocumentContentRepository {

    private final NodeService nodeService;
    private final ContentService contentService;
    private final PermissionService permissionService;

    public AlfrescoCorrespondenceDocumentContentRepository(
            NodeService nodeService,
            ContentService contentService,
            PermissionService permissionService) {

        this.nodeService =
                Objects.requireNonNull(
                        nodeService,
                        "nodeService must not be null");

        this.contentService =
                Objects.requireNonNull(
                        contentService,
                        "contentService must not be null");

        this.permissionService =
                Objects.requireNonNull(
                        permissionService,
                        "permissionService must not be null");
    }

    @Override
    public InputStream openByNodeId(
            String nodeId) {

        Objects.requireNonNull(
                nodeId,
                "nodeId must not be null");

        NodeRef document =
                new NodeRef(
                        StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,
                        nodeId);

        if (!nodeService.exists(document)) {
            throw new CorrespondenceDocumentNotFoundException();
        }

        /*
         * Workflow history uses its own subtype.
         * Only original cm:content nodes are streamed here.
         */
        if (!ContentModel.TYPE_CONTENT.equals(
                nodeService.getType(document))) {

            throw new CorrespondenceDocumentNotFoundException();
        }

        /*
         * Correspondence-role authorization does not replace
         * Alfresco's native document permissions.
         */
        AccessStatus access =
                permissionService.hasPermission(
                        document,
                        PermissionService.READ);

        if (access != AccessStatus.ALLOWED) {

            throw new CorrespondenceAuthorizationException(
                    "The authenticated user does not have READ permission "
                            + "for the requested document.");
        }

        ContentReader reader =
                contentService.getReader(
                        document,
                        ContentModel.PROP_CONTENT);

        if (reader == null || !reader.exists()) {

            throw new CorrespondenceRepositoryException(
                    "Document content is missing from Alfresco.");
        }

        InputStream stream =
                reader.getContentInputStream();

        if (stream == null) {

            throw new CorrespondenceRepositoryException(
                    "Alfresco did not provide a document content stream.");
        }

        return stream;
    }
}