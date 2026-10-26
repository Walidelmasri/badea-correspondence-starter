package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;

class AlfrescoCorrespondenceReadReceiptsTest {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";

    @Test void incomingEventsExcludeTheActorAndWrongRole() {
        NodeService nodes = mock(NodeService.class);
        NodeRef parent = new NodeRef("workspace://SpacesStore/11111111-1111-1111-1111-111111111111");
        NodeRef other = new NodeRef("workspace://SpacesStore/22222222-2222-2222-2222-222222222222");
        NodeRef own = new NodeRef("workspace://SpacesStore/33333333-3333-3333-3333-333333333333");
        NodeRef wrongRole = new NodeRef("workspace://SpacesStore/44444444-4444-4444-4444-444444444444");
        ChildAssociationRef otherAssoc = assoc(other);
        ChildAssociationRef ownAssoc = assoc(own);
        ChildAssociationRef wrongRoleAssoc = assoc(wrongRole);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(otherAssoc, ownAssoc, wrongRoleAssoc));
        for (NodeRef node : List.of(other, own, wrongRole)) when(nodes.getType(node)).thenReturn(q("workflowAction"));
        when(nodes.getProperty(other, q("targetRole"))).thenReturn("PRESIDENT_OFFICE");
        when(nodes.getProperty(other, q("actorEmployeeId"))).thenReturn("A");
        when(nodes.getProperty(other, q("actionSequence"))).thenReturn(7L);
        when(nodes.getProperty(own, q("targetRole"))).thenReturn("PRESIDENT_OFFICE");
        when(nodes.getProperty(own, q("actorEmployeeId"))).thenReturn("B");
        when(nodes.getProperty(wrongRole, q("targetRole"))).thenReturn("PRESIDENT");
        var repo = new AlfrescoCorrespondenceReadReceipts(nodes, mock(CorrespondenceNodeLocator.class));
        assertEquals(7L, repo.latestIncoming(parent, CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "B", false));
    }

    @Test void departmentalDeliveryIsScopedToItsRecipient() {
        NodeService nodes = mock(NodeService.class);
        NodeRef parent = new NodeRef("workspace://SpacesStore/11111111-1111-1111-1111-111111111111");
        NodeRef action = new NodeRef("workspace://SpacesStore/22222222-2222-2222-2222-222222222222");
        ChildAssociationRef actionAssoc = assoc(action);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(actionAssoc));
        when(nodes.getType(action)).thenReturn(q("workflowAction"));
        when(nodes.getProperty(action, q("targetRole"))).thenReturn("DIRECTOR");
        when(nodes.getProperty(action, q("targetDepartmentCode"))).thenReturn("117");
        when(nodes.getProperty(action, q("actorEmployeeId"))).thenReturn("X");
        when(nodes.getProperty(action, q("actionSequence"))).thenReturn(8L);
        var repo = new AlfrescoCorrespondenceReadReceipts(nodes, mock(CorrespondenceNodeLocator.class));
        assertEquals(8L, repo.latestIncoming(parent, CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("117"), "B", true));
        assertEquals(0L, repo.latestIncoming(parent, CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("120"), "B", true));
        assertEquals(0L, repo.latestIncoming(parent, CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("117"), "B", false));
    }

    private static ChildAssociationRef assoc(NodeRef node) {
        ChildAssociationRef assoc = mock(ChildAssociationRef.class);
        when(assoc.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(assoc.getChildRef()).thenReturn(node);
        return assoc;
    }
    private static QName q(String local) { return QName.createQName(NS, local); }
}
