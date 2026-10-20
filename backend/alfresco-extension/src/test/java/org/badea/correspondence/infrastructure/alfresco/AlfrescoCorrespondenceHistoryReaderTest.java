package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Date;
import java.util.List;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.junit.jupiter.api.Test;

class AlfrescoCorrespondenceHistoryReaderTest {
    private static final String NS="http://www.badea.org/model/correspondence/1.0";
    private static QName q(String local) {return QName.createQName(NS,local);}
    private static NodeRef node(String id) {return new NodeRef("workspace://SpacesStore/"+id);}

    @Test
    void returnsSortedWorkflowActionsAndIgnoresOriginalPdf() {
        NodeService nodes=mock(NodeService.class);
        CorrespondenceNodeLocator locator=mock(CorrespondenceNodeLocator.class);
        NodeRef parent=node("11111111-1111-1111-1111-111111111111");
        NodeRef first=node("22222222-2222-2222-2222-222222222222");
        NodeRef second=node("33333333-3333-3333-3333-333333333333");
        NodeRef pdf=node("44444444-4444-4444-4444-444444444444");
        when(locator.locate("draft-id")).thenReturn(parent);
        ChildAssociationRef a1=assoc(first), a2=assoc(second), a3=assoc(pdf);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(a2,a3,a1));
        when(nodes.getType(first)).thenReturn(q("workflowAction"));
        when(nodes.getType(second)).thenReturn(q("workflowAction"));
        when(nodes.getType(pdf)).thenReturn(ContentModel.TYPE_CONTENT);
        setup(nodes,first,1L,"SEND_TO_OFFICE","DIRECTOR");
        setup(nodes,second,2L,"SEND_TO_PRESIDENT","PRESIDENT_OFFICE");
        var result=new AlfrescoCorrespondenceHistoryReader(nodes,locator).list("draft-id");
        assertEquals(2,result.size());
        assertEquals("SEND_TO_OFFICE",result.get(0).actionType());
        assertEquals("SEND_TO_PRESIDENT",result.get(1).actionType());
        assertEquals(2,result.get(1).sequence());
    }

    @Test
    void exposesClosureReasonInReadOnlyHistory() {
        NodeService nodes = mock(NodeService.class);
        CorrespondenceNodeLocator locator = mock(CorrespondenceNodeLocator.class);
        NodeRef parent = node("11111111-1111-1111-1111-111111111111");
        NodeRef action = node("22222222-2222-2222-2222-222222222222");
        when(locator.locate("closed-id")).thenReturn(parent);
        ChildAssociationRef association = assoc(action);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(association));
        when(nodes.getType(action)).thenReturn(q("workflowAction"));
        setup(nodes, action, 4L, "ADMINISTRATIVE_CLOSE", "PRESIDENT_OFFICE");
        when(nodes.getProperty(action, q("closureReason")))
                .thenReturn("All required departments completed");
        var history = new AlfrescoCorrespondenceHistoryReader(nodes, locator).list("closed-id");
        assertEquals("All required departments completed", history.get(0).explanation());
    }

    @Test
    void rejectsMalformedHistoryRatherThanMisclassifyingStage() {
        NodeService nodes=mock(NodeService.class);
        CorrespondenceNodeLocator locator=mock(CorrespondenceNodeLocator.class);
        NodeRef parent=node("11111111-1111-1111-1111-111111111111");
        NodeRef action=node("22222222-2222-2222-2222-222222222222");
        when(locator.locate("draft-id")).thenReturn(parent);
        ChildAssociationRef assoc=assoc(action);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(assoc));
        when(nodes.getType(action)).thenReturn(q("workflowAction"));
        when(nodes.getProperty(action,q("actionSequence"))).thenReturn("bad");
        assertThrows(CorrespondenceRepositoryException.class,
            () -> new AlfrescoCorrespondenceHistoryReader(nodes,locator).list("draft-id"));
    }

    private static ChildAssociationRef assoc(NodeRef child) {
        ChildAssociationRef a=mock(ChildAssociationRef.class);
        when(a.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(a.getChildRef()).thenReturn(child);
        return a;
    }

    private static void setup(NodeService nodes,NodeRef item,long sequence,
            String action,String role) {
        when(nodes.getProperty(item,q("actionSequence"))).thenReturn(sequence);
        when(nodes.getProperty(item,q("actedAt"))).thenReturn(new Date(1000L));
        when(nodes.getProperty(item,q("actionType"))).thenReturn(action);
        when(nodes.getProperty(item,q("actorRole"))).thenReturn(role);
        when(nodes.getProperty(item,q("actorNameEnglish"))).thenReturn("Test User");
        when(nodes.getProperty(item,q("fromState"))).thenReturn("DRAFT");
        when(nodes.getProperty(item,q("toState"))).thenReturn("WITH_PRESIDENT_OFFICE");
    }
}
