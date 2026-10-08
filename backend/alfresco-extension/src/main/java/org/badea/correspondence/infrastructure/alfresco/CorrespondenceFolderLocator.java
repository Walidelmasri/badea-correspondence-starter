package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.site.SiteService;
import org.badea.correspondence.directory.DepartmentCode;

/**
 * Resolves the existing Alfresco storage folders used by the
 * Presidency Correspondence application.
 *
 * <p>This component is deliberately read-only. It does not create,
 * rename or repair repository folders.</p>
 */
public final class CorrespondenceFolderLocator {

    private final SiteService siteService;
    private final NodeService nodeService;
    private final String siteShortName;
    private final String correspondenceRootFolderName;

    public CorrespondenceFolderLocator(
            SiteService siteService,
            NodeService nodeService,
            String siteShortName,
            String correspondenceRootFolderName) {

        this.siteService = Objects.requireNonNull(
                siteService,
                "siteService must not be null");

        this.nodeService = Objects.requireNonNull(
                nodeService,
                "nodeService must not be null");

        this.siteShortName = requireText(
                siteShortName,
                "siteShortName");

        this.correspondenceRootFolderName = requireText(
                correspondenceRootFolderName,
                "correspondenceRootFolderName");
    }

    public NodeRef locateCorrespondenceRoot() {

        if (siteService.getSite(siteShortName) == null) {
            throw new CorrespondenceRepositoryException(
                    "Alfresco site does not exist: " + siteShortName);
        }

        NodeRef documentLibrary = siteService.getContainer(
                siteShortName,
                SiteService.DOCUMENT_LIBRARY);

        if (documentLibrary == null) {
            throw new CorrespondenceRepositoryException(
                    "Document Library does not exist for Alfresco site: "
                            + siteShortName);
        }

        NodeRef correspondenceRoot = nodeService.getChildByName(
                documentLibrary,
                ContentModel.ASSOC_CONTAINS,
                correspondenceRootFolderName);

        if (correspondenceRoot == null) {
            throw new CorrespondenceRepositoryException(
                    "Correspondence root folder does not exist: "
                            + correspondenceRootFolderName);
        }

        if (!ContentModel.TYPE_FOLDER.equals(
                nodeService.getType(correspondenceRoot))) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence root is not a folder: "
                            + correspondenceRootFolderName);
        }

        return correspondenceRoot;
    }

    public NodeRef locateDepartmentFolder(DepartmentCode departmentCode) {

        Objects.requireNonNull(
                departmentCode,
                "departmentCode must not be null");

        NodeRef correspondenceRoot = locateCorrespondenceRoot();

        String expectedPrefix = departmentCode.value() + " - ";

        List<NodeRef> matches = nodeService
                .getChildAssocs(correspondenceRoot)
                .stream()
                .map(ChildAssociationRef::getChildRef)
                .filter(child ->
                        ContentModel.TYPE_FOLDER.equals(
                                nodeService.getType(child)))
                .filter(child -> {
                    Serializable name = nodeService.getProperty(
                            child,
                            ContentModel.PROP_NAME);

                    return name instanceof String folderName
                            && folderName.startsWith(expectedPrefix);
                })
                .toList();

        if (matches.isEmpty()) {
            throw new CorrespondenceRepositoryException(
                    "No correspondence folder exists for department code: "
                            + departmentCode.value());
        }

        if (matches.size() > 1) {
            throw new CorrespondenceRepositoryException(
                    "Multiple correspondence folders exist for department code: "
                            + departmentCode.value());
        }

        return matches.getFirst();
    }

    private static String requireText(
            String value,
            String fieldName) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank");
        }

        return value.trim();
    }
}