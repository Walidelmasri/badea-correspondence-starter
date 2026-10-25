package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.junit.jupiter.api.Test;

class AlfrescoOfficeDistributionRepositoryTest {
    private final NodeService nodes = mock(NodeService.class);
    private final ContentService content = mock(ContentService.class);
    private final CorrespondenceNodeLocator locator = mock(CorrespondenceNodeLocator.class);
    private final AlfrescoCorrespondenceRepository transitions = mock(AlfrescoCorrespondenceRepository.class);
    private final AlfrescoPresidentGeneratedDocumentStore documents = mock(
            AlfrescoPresidentGeneratedDocumentStore.class);
    private final NodeRef parent = new NodeRef("workspace://SpacesStore/00000000-0000-0000-0000-000000000101");
    private final NodeRef original = new NodeRef("workspace://SpacesStore/00000000-0000-0000-0000-000000000102");
    private final NodeRef annotated = new NodeRef("workspace://SpacesStore/00000000-0000-0000-0000-000000000103");
    private final NodeRef oldAnnotation = new NodeRef("workspace://SpacesStore/00000000-0000-0000-0000-000000000104");
    private final AlfrescoOfficeDistributionRepository repository = new AlfrescoOfficeDistributionRepository(nodes,
            content, locator, transitions, documents);

    private ChildAssociationRef assoc(NodeRef child) {
        ChildAssociationRef association = mock(ChildAssociationRef.class);
        when(association.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(association.getChildRef()).thenReturn(child);
        return association;
    }

    @Test
    void usesCurrentAnnotatedRenditionWithoutDuplicatingItsOriginal() {
        ChildAssociationRef originalAssoc = assoc(original);
        ChildAssociationRef oldAnnotationAssoc = assoc(oldAnnotation);
        ChildAssociationRef annotatedAssoc = assoc(annotated);

        when(nodes.getChildAssocs(parent)).thenReturn(
                List.of(originalAssoc, oldAnnotationAssoc, annotatedAssoc));
        when(nodes.getType(original)).thenReturn(ContentModel.TYPE_CONTENT);
        when(nodes.getProperty(original, ContentModel.PROP_NAME)).thenReturn("base.pdf");
        when(nodes.getType(annotated)).thenReturn(AlfrescoPresidentGeneratedDocumentStore.TYPE);
        when(nodes.getType(oldAnnotation)).thenReturn(AlfrescoPresidentGeneratedDocumentStore.TYPE);
        for (NodeRef node : List.of(annotated, oldAnnotation)) {
            when(nodes.getProperty(node, AlfrescoPresidentGeneratedDocumentStore.KIND)).thenReturn("ANNOTATED_PDF");
            when(nodes.getProperty(node, AlfrescoPresidentGeneratedDocumentStore.SOURCE)).thenReturn(original.getId());
        }
        when(nodes.getProperty(annotated, AlfrescoPresidentGeneratedDocumentStore.REVISION)).thenReturn(7L);
        when(nodes.getProperty(oldAnnotation, AlfrescoPresidentGeneratedDocumentStore.REVISION)).thenReturn(6L);
        ContentReader reader = mock(ContentReader.class);
        byte[] inkPdf = "%PDF-current-annotation".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(content.getReader(annotated, ContentModel.PROP_CONTENT)).thenReturn(reader);
        when(reader.exists()).thenReturn(true);
        when(reader.getContentInputStream()).thenReturn(new ByteArrayInputStream(inkPdf));
        var result = repository.distributionDocuments(parent, 8L);
        assertEquals(1, result.size());
        assertArrayEquals(inkPdf, result.getFirst());
        verify(content, never()).getReader(original, ContentModel.PROP_CONTENT);
    }
}
