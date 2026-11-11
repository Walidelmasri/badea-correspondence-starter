package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.junit.jupiter.api.Test;

class AlfrescoIncomingNotificationEventsTest {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static QName q(String name) { return QName.createQName(NS, name); }
    private static NodeRef node(int id) {
        return new NodeRef("workspace://SpacesStore/00000000-0000-0000-0000-0000000000" + String.format("%02d", id));
    }
    private static ChildAssociationRef child(NodeRef ref) {
        ChildAssociationRef assoc = mock(ChildAssociationRef.class);
        when(assoc.getChildRef()).thenReturn(ref);
        return assoc;
    }

    @Test void discoversSubmittedSecretaryApprovalAndOfficeReturnWithoutChangingHistory() {
        NodeService nodes = mock(NodeService.class);
        CorrespondenceFolderLocator folders = mock(CorrespondenceFolderLocator.class);
        NodeRef root = node(1), folder = node(2), correspondence = node(3);
        NodeRef submit = node(4), officeReturn = node(5), secretaryReturn = node(6);
        List<ChildAssociationRef> rootChildren = List.of(child(folder));
        List<ChildAssociationRef> folderChildren = List.of(child(correspondence));
        List<ChildAssociationRef> correspondenceChildren = List.of(
            child(submit), child(officeReturn), child(secretaryReturn));
        when(folders.locateCorrespondenceRoot()).thenReturn(root);
        when(nodes.getChildAssocs(root)).thenReturn(rootChildren);
        when(nodes.getType(folder)).thenReturn(ContentModel.TYPE_FOLDER);
        when(nodes.getChildAssocs(folder)).thenReturn(folderChildren);
        when(nodes.getType(correspondence)).thenReturn(q("correspondence"));
        when(nodes.getChildAssocs(correspondence)).thenReturn(correspondenceChildren);
        for (NodeRef action : List.of(submit, officeReturn, secretaryReturn)) {
            when(nodes.getType(action)).thenReturn(q("workflowAction"));
            when(nodes.getProperty(action, q("actedAt")))
                .thenReturn(Date.from(Instant.parse("2026-11-11T12:01:00Z")));
        }
        when(nodes.getProperty(correspondence, q("subject"))).thenReturn("Secretary test");
        when(nodes.getProperty(submit, q("actionType")))
            .thenReturn("SUBMIT_FOR_MANAGER_APPROVAL");
        when(nodes.getProperty(submit, q("targetRole"))).thenReturn("DIRECTOR");
        when(nodes.getProperty(submit, q("targetDepartmentCode"))).thenReturn("117");
        when(nodes.getProperty(submit, q("actorEmployeeId"))).thenReturn("100");
        when(nodes.getProperty(officeReturn, q("actionType"))).thenReturn("SEND_TO_DIRECTOR");
        when(nodes.getProperty(officeReturn, q("targetRole"))).thenReturn("DIRECTOR");
        when(nodes.getProperty(officeReturn, q("targetDepartmentCode"))).thenReturn("117");
        when(nodes.getProperty(officeReturn, q("actorRole"))).thenReturn("PRESIDENT_OFFICE");
        when(nodes.getProperty(officeReturn, q("explanation"))).thenReturn("Review and respond");
        when(nodes.getProperty(secretaryReturn, q("actionType"))).thenReturn("RETURN_TO_SECRETARY");
        when(nodes.getProperty(secretaryReturn, q("targetRole"))).thenReturn("SECRETARY");
        when(nodes.getProperty(secretaryReturn, q("targetEmployeeId"))).thenReturn("100");
        when(nodes.getProperty(secretaryReturn, q("explanation"))).thenReturn("Revise the attachments");
        var worker = new AlfrescoIncomingNotificationWorker(nodes, folders,
            mock(TransactionService.class), "true", "2026-11-11T12:00:00Z", null,
            "ALLOWLIST", "walid.salem@badea.org,beilahi@badea.org");
        var events = worker.findCommittedEvents();
        assertEquals(4, events.size());
        assertTrue(events.stream().anyMatch(e ->
            e.kind() == AlfrescoIncomingNotificationWorker.Kind.MANAGER_REQUEST
                && e.department().value().equals("117")));
        assertTrue(events.stream().anyMatch(e ->
            e.kind() == AlfrescoIncomingNotificationWorker.Kind.DEPARTMENT_MANAGER_RETURN
                && "Review and respond".equals(e.explanation())));
        assertTrue(events.stream().anyMatch(e ->
            e.kind() == AlfrescoIncomingNotificationWorker.Kind.DEPARTMENT_SECRETARIES
                && "Review and respond".equals(e.explanation())));
        assertTrue(events.stream().anyMatch(e ->
            e.kind() == AlfrescoIncomingNotificationWorker.Kind.SECRETARY_RETURN
                && "Revise the attachments".equals(e.explanation())));
        verify(nodes, never()).setProperty(any(), any(), any());
    }

    @Test void createsRoundNNotificationsOnlyFromPersistedDecisionEvents() {
        NodeService nodes = mock(NodeService.class);
        CorrespondenceFolderLocator folders = mock(CorrespondenceFolderLocator.class);
        NodeRef root = node(1), folder = node(2), correspondence = node(3);
        NodeRef request = node(7), submit = node(8), returned = node(9), preparing = node(10);
        List<ChildAssociationRef> rootChildren = List.of(child(folder));
        List<ChildAssociationRef> folderChildren = List.of(child(correspondence));
        List<ChildAssociationRef> correspondenceChildren = List.of(
            child(request), child(preparing), child(submit), child(returned));
        when(folders.locateCorrespondenceRoot()).thenReturn(root);
        when(nodes.getChildAssocs(root)).thenReturn(rootChildren);
        when(nodes.getType(folder)).thenReturn(ContentModel.TYPE_FOLDER);
        when(nodes.getChildAssocs(folder)).thenReturn(folderChildren);
        when(nodes.getType(correspondence)).thenReturn(q("correspondence"));
        when(nodes.getChildAssocs(correspondence)).thenReturn(correspondenceChildren);
        when(nodes.getType(request)).thenReturn(q("secretaryRoundTwoRequest"));
        when(nodes.getProperty(request, q("roundTwoRequestId"))).thenReturn("request-1");
        when(nodes.getProperty(request, q("roundTwoRequestUnit"))).thenReturn("117");
        when(nodes.getProperty(request, q("roundTwoRequestOwner"))).thenReturn("100");
        when(nodes.getType(preparing)).thenReturn(q("secretaryRoundTwoDecisionEvent"));
        when(nodes.getType(submit)).thenReturn(q("secretaryRoundTwoDecisionEvent"));
        when(nodes.getType(returned)).thenReturn(q("secretaryRoundTwoDecisionEvent"));
        for (NodeRef item : List.of(preparing, submit, returned)) {
            when(nodes.getProperty(item, q("roundTwoDecisionRequestId"))).thenReturn("request-1");
            when(nodes.getProperty(item, q("roundTwoDecisionAt")))
                .thenReturn(Date.from(Instant.parse("2026-11-11T12:05:00Z")));
        }
        when(nodes.getProperty(preparing, q("roundTwoDecisionAction"))).thenReturn("REVISED");
        when(nodes.getProperty(submit, q("roundTwoDecisionAction"))).thenReturn("SUBMITTED");
        when(nodes.getProperty(returned, q("roundTwoDecisionAction"))).thenReturn("RETURNED");
        when(nodes.getProperty(returned, q("roundTwoDecisionReason"))).thenReturn("Update report");
        var worker = new AlfrescoIncomingNotificationWorker(nodes, folders,
            mock(TransactionService.class), "true", "2026-11-11T12:00:00Z", null,
            "ALLOWLIST", "walid.salem@badea.org,beilahi@badea.org");
        var events = worker.findCommittedEvents();
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(e -> e.kind() ==
            AlfrescoIncomingNotificationWorker.Kind.ROUND_REQUEST && "117".equals(e.department().value())));
        assertTrue(events.stream().anyMatch(e -> e.kind() ==
            AlfrescoIncomingNotificationWorker.Kind.ROUND_RETURN
            && "100".equals(e.targetEmployeeId()) && "Update report".equals(e.explanation())));
        verify(nodes, never()).setProperty(any(), any(), any());
    }
}
