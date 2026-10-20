package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.time.Instant;
import java.util.List;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.junit.jupiter.api.Test;

class AlfrescoDepartmentAssignmentReaderTest {
    @Test void currentProjectionKeepsHistoricalAssignmentsOutOfActions() {
        NodeService nodes = mock(NodeService.class);
        NodeRef parent = mock(NodeRef.class);
        NodeRef previous = mock(NodeRef.class);
        NodeRef active = mock(NodeRef.class);
        ChildAssociationRef oldAssoc = association(previous);
        ChildAssociationRef newAssoc = association(active);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(oldAssoc, newAssoc));
        when(nodes.getType(previous)).thenReturn(AlfrescoDepartmentAssignmentReader.TYPE);
        when(nodes.getType(active)).thenReturn(AlfrescoDepartmentAssignmentReader.TYPE);
        when(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("technicalId")))
                .thenReturn("3bdd931c-a3de-4f90-bff6-7cfd1949f2c9");
        when(nodes.getProperty(parent, AlfrescoCorrespondenceCycleReader.CURRENT))
                .thenReturn(2L);
        when(nodes.getProperty(active, AlfrescoCorrespondenceCycleReader.ASSIGNMENT))
                .thenReturn(2L);
        // Old assignment deliberately lacks required fields: it must be filtered
        // before the current-round projection attempts to parse it.
        when(nodes.getProperty(active, AlfrescoDepartmentAssignmentReader.q("assignmentId")))
                .thenReturn("be10a159-94aa-4b14-8872-5d123aa97b9a");
        when(nodes.getProperty(active, AlfrescoDepartmentAssignmentReader.q("assignmentDepartmentCode")))
                .thenReturn("117");
        when(nodes.getProperty(active, AlfrescoDepartmentAssignmentReader.q("assignmentRecipientType")))
                .thenReturn("TO");
        when(nodes.getProperty(active, AlfrescoDepartmentAssignmentReader.q("assignmentStatus")))
                .thenReturn("PENDING");
        when(nodes.getProperty(active, AlfrescoDepartmentAssignmentReader.q("assignmentByEmployeeId")))
                .thenReturn("99999");
        when(nodes.getProperty(active, AlfrescoDepartmentAssignmentReader.q("assignmentAt")))
                .thenReturn(Date.from(Instant.parse("2026-10-20T13:00:00Z")));

        var current = new AlfrescoDepartmentAssignmentReader(nodes).readCurrent(parent);
        assertEquals(1, current.size());
        assertEquals("117", current.get(0).departmentCode().value());
        assertEquals("be10a159-94aa-4b14-8872-5d123aa97b9a", current.get(0).assignmentId());
    }

    private static ChildAssociationRef association(NodeRef target) {
        ChildAssociationRef association = mock(ChildAssociationRef.class);
        when(association.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(association.getChildRef()).thenReturn(target);
        return association;
    }
}
