package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorrespondenceNodeLocatorTest {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private NodeService nodeService;
    private CorrespondenceFolderLocator folderLocator;

    private CorrespondenceNodeLocator locator;

    @BeforeEach
    void setUp() {

        nodeService =
                mock(NodeService.class);

        folderLocator =
                mock(CorrespondenceFolderLocator.class);

        locator =
                new CorrespondenceNodeLocator(
                        nodeService,
                        folderLocator);
    }

    @Test
    void locatesCorrespondenceByTechnicalId()
            throws Exception {

        NodeRef root =
                nodeRef(
                        "00000000-0000-0000-0000-000000000001");

        NodeRef strategyFolder =
                nodeRef(
                        "00000000-0000-0000-0000-000000000111");

        NodeRef digitalTransformationFolder =
                nodeRef(
                        "00000000-0000-0000-0000-000000000117");

        NodeRef correspondence =
                nodeRef(
                        "00000000-0000-0000-0000-000000000999");

        ChildAssociationRef strategyAssociation =
                childAssociation(strategyFolder);

        ChildAssociationRef digitalTransformationAssociation =
                childAssociation(digitalTransformationFolder);

        ChildAssociationRef correspondenceAssociation =
                childAssociation(correspondence);

        when(folderLocator.locateCorrespondenceRoot())
                .thenReturn(root);

        when(nodeService.getChildAssocs(root))
                .thenReturn(
                        List.of(
                                strategyAssociation,
                                digitalTransformationAssociation));

        when(nodeService.getType(strategyFolder))
                .thenReturn(
                        ContentModel.TYPE_FOLDER);

        when(nodeService.getType(
                digitalTransformationFolder))
                .thenReturn(
                        ContentModel.TYPE_FOLDER);

        QName associationQName =
                qname(
                        "correspondence-" + TECHNICAL_ID);

        when(nodeService.getChildAssocs(
                strategyFolder,
                ContentModel.ASSOC_CONTAINS,
                associationQName))
                .thenReturn(
                        List.of());

        when(nodeService.getChildAssocs(
                digitalTransformationFolder,
                ContentModel.ASSOC_CONTAINS,
                associationQName))
                .thenReturn(
                        List.of(
                                correspondenceAssociation));

        when(nodeService.getType(correspondence))
                .thenReturn(
                        qname("correspondence"));

        when(nodeService.getProperty(
                correspondence,
                qname("technicalId")))
                .thenReturn(
                        TECHNICAL_ID);

        NodeRef result =
                locator.locate(
                        TECHNICAL_ID);

        assertEquals(
                correspondence,
                result);
    }

    @Test
    void rejectsMalformedTechnicalId() {

        assertThrows(
                IllegalArgumentException.class,
                () -> locator.locate(
                        "not-a-uuid"));

        verify(
                folderLocator,
                never())
                .locateCorrespondenceRoot();
    }

    @Test
    void rejectsMissingCorrespondence() {

        NodeRef root =
                nodeRef(
                        "00000000-0000-0000-0000-000000000001");

        when(folderLocator.locateCorrespondenceRoot())
                .thenReturn(root);

        when(nodeService.getChildAssocs(root))
                .thenReturn(
                        List.of());

        assertThrows(
                CorrespondenceRepositoryException.class,
                () -> locator.locate(
                        TECHNICAL_ID));
    }

@Test
void rejectsDuplicateTechnicalId() {

    NodeRef root =
            nodeRef(
                    "00000000-0000-0000-0000-000000000001");

    NodeRef firstDepartment =
            nodeRef(
                    "00000000-0000-0000-0000-000000000111");

    NodeRef secondDepartment =
            nodeRef(
                    "00000000-0000-0000-0000-000000000117");

    NodeRef firstCorrespondence =
            nodeRef(
                    "00000000-0000-0000-0000-000000000901");

    NodeRef secondCorrespondence =
            nodeRef(
                    "00000000-0000-0000-0000-000000000902");

    ChildAssociationRef firstDepartmentAssociation =
            childAssociation(firstDepartment);

    ChildAssociationRef secondDepartmentAssociation =
            childAssociation(secondDepartment);

    ChildAssociationRef firstCorrespondenceAssociation =
            childAssociation(firstCorrespondence);

    ChildAssociationRef secondCorrespondenceAssociation =
            childAssociation(secondCorrespondence);

    when(folderLocator.locateCorrespondenceRoot())
            .thenReturn(root);

    when(nodeService.getChildAssocs(root))
            .thenReturn(
                    List.of(
                            firstDepartmentAssociation,
                            secondDepartmentAssociation));

    when(nodeService.getType(firstDepartment))
            .thenReturn(
                    ContentModel.TYPE_FOLDER);

    when(nodeService.getType(secondDepartment))
            .thenReturn(
                    ContentModel.TYPE_FOLDER);

    QName associationQName =
            qname(
                    "correspondence-" + TECHNICAL_ID);

    when(nodeService.getChildAssocs(
            firstDepartment,
            ContentModel.ASSOC_CONTAINS,
            associationQName))
            .thenReturn(
                    List.of(
                            firstCorrespondenceAssociation));

    when(nodeService.getChildAssocs(
            secondDepartment,
            ContentModel.ASSOC_CONTAINS,
            associationQName))
            .thenReturn(
                    List.of(
                            secondCorrespondenceAssociation));

    when(nodeService.getType(firstCorrespondence))
            .thenReturn(
                    qname("correspondence"));

    when(nodeService.getType(secondCorrespondence))
            .thenReturn(
                    qname("correspondence"));

    when(nodeService.getProperty(
            firstCorrespondence,
            qname("technicalId")))
            .thenReturn(
                    TECHNICAL_ID);

    when(nodeService.getProperty(
            secondCorrespondence,
            qname("technicalId")))
            .thenReturn(
                    TECHNICAL_ID);

    assertThrows(
            CorrespondenceRepositoryException.class,
            () -> locator.locate(
                    TECHNICAL_ID));
}

    private static ChildAssociationRef childAssociation(
            NodeRef child) {

        ChildAssociationRef association =
                mock(ChildAssociationRef.class);

        when(association.getChildRef())
                .thenReturn(child);

        return association;
    }

    private static QName qname(
            String localName) {

        return QName.createQName(
                NAMESPACE_URI,
                localName);
    }

    private static NodeRef nodeRef(
            String id) {

        return new NodeRef(
                "workspace://SpacesStore/" + id);
    }
}