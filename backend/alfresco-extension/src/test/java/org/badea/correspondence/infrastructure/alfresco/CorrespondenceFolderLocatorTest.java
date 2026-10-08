package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.badea.correspondence.directory.DepartmentCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class CorrespondenceFolderLocatorTest {

    private static final String SITE_SHORT_NAME =
            "badea-instituional-documents";

    private static final String ROOT_FOLDER =
            "Correspondence";

    private SiteService siteService;

    private NodeService nodeService;

    private CorrespondenceFolderLocator locator;

@BeforeEach
void setUp() {
    siteService = mock(SiteService.class);
    nodeService = mock(NodeService.class);

    locator = new CorrespondenceFolderLocator(
            siteService,
            nodeService,
            SITE_SHORT_NAME,
            ROOT_FOLDER);
}

    @Test
    void rejectsMissingSite() {

        when(siteService.getSite(SITE_SHORT_NAME))
                .thenReturn(null);

        CorrespondenceRepositoryException exception =
                assertThrows(
                        CorrespondenceRepositoryException.class,
                        locator::locateCorrespondenceRoot);

        assertEquals(
                "Alfresco site does not exist: "
                        + SITE_SHORT_NAME,
                exception.getMessage());
    }

    @Test
    void rejectsMissingDocumentLibrary() {

        when(siteService.getSite(SITE_SHORT_NAME))
                .thenReturn(mock(SiteInfo.class));

        when(siteService.getContainer(
                SITE_SHORT_NAME,
                SiteService.DOCUMENT_LIBRARY))
                .thenReturn(null);

        assertThrows(
                CorrespondenceRepositoryException.class,
                locator::locateCorrespondenceRoot);
    }

    @Test
    void rejectsMissingCorrespondenceRoot() {

        NodeRef documentLibrary =
                new NodeRef(
                        "workspace://SpacesStore/00000000-0000-0000-0000-000000000001");

        when(siteService.getSite(SITE_SHORT_NAME))
                .thenReturn(mock(SiteInfo.class));

        when(siteService.getContainer(
                SITE_SHORT_NAME,
                SiteService.DOCUMENT_LIBRARY))
                .thenReturn(documentLibrary);

        when(nodeService.getChildByName(
                documentLibrary,
                ContentModel.ASSOC_CONTAINS,
                ROOT_FOLDER))
                .thenReturn(null);

        assertThrows(
                CorrespondenceRepositoryException.class,
                locator::locateCorrespondenceRoot);
    }

    @Test
    void resolvesDepartmentFolderByNumericCodePrefix() {

        NodeRef root = stubRoot();

        NodeRef strategyFolder =
                nodeRef("00000000-0000-0000-0000-000000000111");

        NodeRef riskFolder =
                nodeRef("00000000-0000-0000-0000-000000000104");

        ChildAssociationRef strategyAssociation =
                mock(ChildAssociationRef.class);

        ChildAssociationRef riskAssociation =
                mock(ChildAssociationRef.class);

        when(strategyAssociation.getChildRef())
                .thenReturn(strategyFolder);

        when(riskAssociation.getChildRef())
                .thenReturn(riskFolder);

        when(nodeService.getChildAssocs(root))
                .thenReturn(List.of(
                        riskAssociation,
                        strategyAssociation));

        when(nodeService.getType(strategyFolder))
                .thenReturn(ContentModel.TYPE_FOLDER);

        when(nodeService.getType(riskFolder))
                .thenReturn(ContentModel.TYPE_FOLDER);

        when(nodeService.getProperty(
                strategyFolder,
                ContentModel.PROP_NAME))
                .thenReturn("111 - Strategy Department");

        when(nodeService.getProperty(
                riskFolder,
                ContentModel.PROP_NAME))
                .thenReturn("104 - Risk Department");

        NodeRef result =
                locator.locateDepartmentFolder(
                        new DepartmentCode("111"));

        assertEquals(strategyFolder, result);
    }

    @Test
    void rejectsMissingDepartmentFolder() {

        NodeRef root = stubRoot();

        when(nodeService.getChildAssocs(root))
                .thenReturn(List.of());

        assertThrows(
                CorrespondenceRepositoryException.class,
                () -> locator.locateDepartmentFolder(
                        new DepartmentCode("111")));
    }

    @Test
    void rejectsDuplicateDepartmentCodeFolders() {

        NodeRef root = stubRoot();

        NodeRef first =
                nodeRef("00000000-0000-0000-0000-000000000111");

        NodeRef second =
                nodeRef("00000000-0000-0000-0000-000000000211");

        ChildAssociationRef firstAssociation =
                mock(ChildAssociationRef.class);

        ChildAssociationRef secondAssociation =
                mock(ChildAssociationRef.class);

        when(firstAssociation.getChildRef())
                .thenReturn(first);

        when(secondAssociation.getChildRef())
                .thenReturn(second);

        when(nodeService.getChildAssocs(root))
                .thenReturn(List.of(
                        firstAssociation,
                        secondAssociation));

        when(nodeService.getType(first))
                .thenReturn(ContentModel.TYPE_FOLDER);

        when(nodeService.getType(second))
                .thenReturn(ContentModel.TYPE_FOLDER);

        when(nodeService.getProperty(
                first,
                ContentModel.PROP_NAME))
                .thenReturn("111 - Strategy Department");

        when(nodeService.getProperty(
                second,
                ContentModel.PROP_NAME))
                .thenReturn("111 - Old Strategy Department");

        assertThrows(
                CorrespondenceRepositoryException.class,
                () -> locator.locateDepartmentFolder(
                        new DepartmentCode("111")));
    }

    private NodeRef stubRoot() {

        NodeRef documentLibrary =
                nodeRef("00000000-0000-0000-0000-000000000001");

        NodeRef root =
                nodeRef("00000000-0000-0000-0000-000000000002");

        when(siteService.getSite(SITE_SHORT_NAME))
                .thenReturn(mock(SiteInfo.class));

        when(siteService.getContainer(
                SITE_SHORT_NAME,
                SiteService.DOCUMENT_LIBRARY))
                .thenReturn(documentLibrary);

        when(nodeService.getChildByName(
                documentLibrary,
                ContentModel.ASSOC_CONTAINS,
                ROOT_FOLDER))
                .thenReturn(root);

        when(nodeService.getType(root))
                .thenReturn(ContentModel.TYPE_FOLDER);

        return root;
    }

    private static NodeRef nodeRef(String id) {
        return new NodeRef(
                "workspace://SpacesStore/" + id);
    }
}