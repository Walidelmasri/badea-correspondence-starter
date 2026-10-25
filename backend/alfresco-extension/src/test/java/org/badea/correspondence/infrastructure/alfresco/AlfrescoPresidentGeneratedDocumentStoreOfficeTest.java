package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.directory.EmployeeId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlfrescoPresidentGeneratedDocumentStoreOfficeTest {
    private static final String ID = "97012c9b-50ee-46b7-bdc3-aa2d36eb2ed4";
    private final NodeService nodes = mock(NodeService.class);
    private final ContentService content = mock(ContentService.class);
    private final CorrespondenceNodeLocator locator = mock(CorrespondenceNodeLocator.class);
    private final NodeRef parent = new NodeRef("workspace://SpacesStore/00000000-0000-0000-0000-000000000101");
    private final NodeRef generated = new NodeRef("workspace://SpacesStore/00000000-0000-0000-0000-000000000102");
    private final AlfrescoPresidentGeneratedDocumentStore store =
            new AlfrescoPresidentGeneratedDocumentStore(nodes, content, locator,
                    Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZoneOffset.UTC));

    @Test
    void OfficeRevisionsCreateSeparateImmutableNodesForSamePresidentRevision() {
        when(locator.locate(ID)).thenReturn(parent);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of());
        ChildAssociationRef association = mock(ChildAssociationRef.class);
        when(association.getChildRef()).thenReturn(generated);
        when(nodes.createNode(eq(parent), eq(ContentModel.ASSOC_CONTAINS),
                any(QName.class), eq(AlfrescoPresidentGeneratedDocumentStore.TYPE), anyMap()))
                .thenReturn(association);
        when(content.getWriter(eq(generated), eq(ContentModel.PROP_CONTENT), eq(true)))
                .thenReturn(mock(ContentWriter.class));

        byte[] bytes = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        store.saveOffice(ID, 5, 2, "CORRESPONDENCE_PACKET", "packet.pdf", bytes, new EmployeeId("99"));
        store.saveOffice(ID, 5, 3, "CORRESPONDENCE_PACKET", "packet.pdf", bytes, new EmployeeId("99"));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Map<QName, Serializable>> properties = ArgumentCaptor.forClass((Class) Map.class);
        ArgumentCaptor<QName> names = ArgumentCaptor.forClass(QName.class);
        verify(nodes, times(2)).createNode(eq(parent), eq(ContentModel.ASSOC_CONTAINS),
                names.capture(), eq(AlfrescoPresidentGeneratedDocumentStore.TYPE), properties.capture());
        assertNotEquals(names.getAllValues().get(0), names.getAllValues().get(1));
        assertEquals(5L, properties.getAllValues().get(0).get(AlfrescoPresidentGeneratedDocumentStore.REVISION));
        assertEquals(2L, properties.getAllValues().get(0).get(AlfrescoPresidentGeneratedDocumentStore.OFFICE_REVISION));
        assertEquals(3L, properties.getAllValues().get(1).get(AlfrescoPresidentGeneratedDocumentStore.OFFICE_REVISION));
    }

    @Test
    void ExistingOfficeArtifactBlocksSameDistributionButNotLaterRevision() {
        when(locator.locate(ID)).thenReturn(parent);
        ChildAssociationRef old = mock(ChildAssociationRef.class);
        when(old.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(old.getChildRef()).thenReturn(generated);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(old));
        when(nodes.getType(generated)).thenReturn(AlfrescoPresidentGeneratedDocumentStore.TYPE);
        when(nodes.getProperty(generated, AlfrescoPresidentGeneratedDocumentStore.REVISION)).thenReturn(5L);
        when(nodes.getProperty(generated, AlfrescoPresidentGeneratedDocumentStore.OFFICE_REVISION)).thenReturn(2L);
        when(nodes.getProperty(generated, AlfrescoPresidentGeneratedDocumentStore.KIND))
                .thenReturn("CORRESPONDENCE_PACKET");

        // A later Office revision must not mistake the old packet for its own.
        assertTrue(store.findOffice(ID, 5, 3, "CORRESPONDENCE_PACKET").isEmpty());

        ContentData metadata = mock(ContentData.class);
        when(metadata.getMimetype()).thenReturn("application/pdf");
        when(metadata.getSize()).thenReturn(16L);
        when(nodes.getProperty(generated, ContentModel.PROP_CONTENT)).thenReturn(metadata);
        when(nodes.getProperty(generated, ContentModel.PROP_NAME)).thenReturn("packet.pdf");
        when(nodes.getProperty(generated, AlfrescoPresidentGeneratedDocumentStore.HASH))
                .thenReturn("0".repeat(64));
        when(nodes.getProperty(generated, AlfrescoPresidentGeneratedDocumentStore.CREATOR)).thenReturn("99");
        when(nodes.getProperty(generated, AlfrescoPresidentGeneratedDocumentStore.CREATED))
                .thenReturn(java.util.Date.from(Instant.parse("2026-10-26T08:00:00Z")));
        assertThrows(CorrespondenceRepositoryException.class,
                () -> store.saveOffice(ID, 5, 2, "CORRESPONDENCE_PACKET", "packet.pdf",
                        "%PDF-test".getBytes(), new EmployeeId("99")));
        verify(nodes, never()).createNode(any(), any(), any(), any(), anyMap());
    }

    @Test
    void DoesNotAllowInvalidOfficeArtifactOrCorruptPdf() {
        assertThrows(IllegalArgumentException.class,
                () -> store.saveOffice(ID, 5, 0, "CORRESPONDENCE_PACKET", "file.pdf",
                        "%PDF-test".getBytes(), new EmployeeId("99")));
        assertThrows(IllegalArgumentException.class,
                () -> store.saveOffice(ID, 5, 2, "ANNOTATED_PDF", "file.pdf",
                        "%PDF-test".getBytes(), new EmployeeId("99")));
        assertThrows(IllegalArgumentException.class,
                () -> store.saveOffice(ID, 5, 2, "CORRESPONDENCE_PACKET", "file.pdf",
                        "not-pdf".getBytes(), new EmployeeId("99")));
        verifyNoInteractions(nodes, content);
    }
}
