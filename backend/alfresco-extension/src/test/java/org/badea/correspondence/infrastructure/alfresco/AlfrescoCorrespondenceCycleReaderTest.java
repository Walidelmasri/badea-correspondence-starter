package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.junit.jupiter.api.Test;

class AlfrescoCorrespondenceCycleReaderTest {
    @Test void legacyAssignmentsRemainInCycleOne() {
        NodeService nodes = mock(NodeService.class);
        NodeRef correspondence = mock(NodeRef.class);
        NodeRef assignment = mock(NodeRef.class);
        var reader = new AlfrescoCorrespondenceCycleReader(nodes);
        assertEquals(1L, reader.current(correspondence));
        assertEquals(1L, reader.assignment(assignment));
        assertTrue(reader.isCurrent(correspondence, assignment));
    }

    @Test void earlierAssignmentsAreNotActionableInNextRound() {
        NodeService nodes = mock(NodeService.class);
        NodeRef correspondence = mock(NodeRef.class);
        NodeRef old = mock(NodeRef.class);
        NodeRef next = mock(NodeRef.class);
        when(nodes.getProperty(correspondence, AlfrescoCorrespondenceCycleReader.CURRENT))
                .thenReturn(2L);
        when(nodes.getProperty(next, AlfrescoCorrespondenceCycleReader.ASSIGNMENT))
                .thenReturn(2L);
        var reader = new AlfrescoCorrespondenceCycleReader(nodes);
        assertFalse(reader.isCurrent(correspondence, old));
        assertTrue(reader.isCurrent(correspondence, next));
    }

    @Test void corruptCycleMetadataFailsClosed() {
        NodeService nodes = mock(NodeService.class);
        NodeRef correspondence = mock(NodeRef.class);
        when(nodes.getProperty(correspondence, AlfrescoCorrespondenceCycleReader.CURRENT))
                .thenReturn(0L);
        var reader = new AlfrescoCorrespondenceCycleReader(nodes);
        assertThrows(CorrespondenceRepositoryException.class,
                () -> reader.current(correspondence));
    }
}
