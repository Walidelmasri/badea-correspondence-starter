package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.directory.DepartmentCode;
import org.junit.jupiter.api.Test;

class AlfrescoCorrespondenceReferenceAllocatorTest {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private final NodeService nodes = mock(NodeService.class);
    private final CorrespondenceFolderLocator folders = mock(CorrespondenceFolderLocator.class);
    private final NodeRef department = new NodeRef("workspace://SpacesStore/00000000-0000-0000-0000-000000000111");
    private final ChildAssociationRef created = mock(ChildAssociationRef.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T22:15:00Z"), ZoneOffset.UTC);

    private AlfrescoCorrespondenceReferenceAllocator allocator() {
        when(folders.locateDepartmentFolder(new DepartmentCode("117"))).thenReturn(department);
        when(created.getChildRef()).thenReturn(new NodeRef("workspace://SpacesStore/00000000-0000-0000-0000-000000000119"));
        when(nodes.createNode(eq(department), eq(ContentModel.ASSOC_CONTAINS), any(QName.class),
                eq(ContentModel.TYPE_FOLDER), any())).thenReturn(created);
        return new AlfrescoCorrespondenceReferenceAllocator(nodes, folders, clock);
    }

    @Test void startsAtOneAndUsesRiyadhSubmissionDate() {
        var allocator = allocator();
        when(nodes.getChildAssocs(department)).thenReturn(List.of());
        assertEquals("117/20261025/1", allocator.reserve(new DepartmentCode("117")));
        verify(nodes).createNode(eq(department), eq(ContentModel.ASSOC_CONTAINS),
                eq(QName.createQName(NS, "ref-sequence-1")), eq(ContentModel.TYPE_FOLDER), any());
    }

    @Test void continuesFromPersistedReservations() {
        var allocator = allocator();
        var previous = mock(ChildAssociationRef.class);
        when(previous.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(previous.getQName()).thenReturn(QName.createQName(NS,"ref-sequence-4"));
        when(nodes.getChildAssocs(department)).thenReturn(List.of(previous));
        assertEquals("117/20261025/5", allocator.reserve(new DepartmentCode("117")));
    }

    @Test void neverFallsBackToDuplicateWhenReservationFails() {
        var allocator = allocator();
        when(nodes.getChildAssocs(department)).thenReturn(List.of());
        when(nodes.createNode(eq(department), eq(ContentModel.ASSOC_CONTAINS), any(QName.class),
                eq(ContentModel.TYPE_FOLDER), any()))
                .thenThrow(new IllegalStateException("Concurrent unique child association conflict"));
        assertThrows(IllegalStateException.class, () -> allocator.reserve(new DepartmentCode("117")));
    }
}
