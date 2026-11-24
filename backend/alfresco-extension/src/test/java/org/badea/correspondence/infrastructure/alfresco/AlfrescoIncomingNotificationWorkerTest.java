package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.alfresco.service.transaction.TransactionService;
import org.junit.jupiter.api.Test;

class AlfrescoIncomingNotificationWorkerTest {
    private final NodeService nodes = mock(NodeService.class);
    private final CorrespondenceFolderLocator folders = mock(CorrespondenceFolderLocator.class);
    private final TransactionService tx = mock(TransactionService.class);

    @Test void notificationsAreDisabledWithoutAnActivationInstant() {
        var worker = new AlfrescoIncomingNotificationWorker(nodes, folders, tx,
                null, null, null);
        assertDoesNotThrow(worker::start);
        assertDoesNotThrow(worker::sweep);
        assertDoesNotThrow(worker::stop);
        verifyNoInteractions(nodes, folders, tx);
    }

    @Test void enabledWorkerMustHaveAnExplicitCutoffToProtectHistoricalRecords() {
        assertThrows(IllegalArgumentException.class, () ->
                new AlfrescoIncomingNotificationWorker(nodes, folders, tx,
                        "true", null, null));
        assertThrows(java.time.format.DateTimeParseException.class, () ->
                new AlfrescoIncomingNotificationWorker(nodes, folders, tx,
                        "true", "not-a-date", null));
    }

    @Test void invalidOrMissingDeliveryScopeLeavesNotificationsDisabledWithoutTouchingRepository() {
        for (String scope : new String[] {"", "ALL", "DISABLED"}) {
            var worker = new AlfrescoIncomingNotificationWorker(nodes, folders, tx, "true",
                "2026-11-11T12:00:00Z", "https://tarasol.internal.badea.org/inbox",
                scope, "walid.salem@badea.org,beilahi@badea.org");
            assertDoesNotThrow(worker::start);
            assertDoesNotThrow(worker::sweep);
            worker.stop();
        }
        verifyNoInteractions(nodes, folders, tx);
    }

    @Test void frozenAudienceNeverExpandsAndPendingDeliveryCanBeClaimedOnlyOnce() {
        var worker = new AlfrescoIncomingNotificationWorker(nodes, folders, tx, null, null, null);
        NodeRef correspondence = new NodeRef("workspace://SpacesStore/00000000-0000-0000-0000-000000000001");
        var event = new AlfrescoIncomingNotificationWorker.Event(correspondence, "ACTION-1",
            AlfrescoIncomingNotificationWorker.Kind.INCOMING, CorrespondenceActorRole.PRESIDENT_OFFICE,
            null, "1", null, "ref", "subject", null);
        var first = new AlfrescoNotificationRecipientResolver.Recipient("2", "first", "first@badea.org", "First", null);
        var later = new AlfrescoNotificationRecipientResolver.Recipient("3", "later", "later@badea.org", "Later", null);
        Map<String, NodeRef> byName = new HashMap<>();
        Map<NodeRef, Map<QName, Serializable>> properties = new HashMap<>();
        QName receiptType = QName.createQName("http://www.badea.org/model/correspondence/1.0", "mailDeliveryReceipt");
        when(nodes.getChildAssocs(correspondence)).thenReturn(List.of());
        when(nodes.getChildByName(eq(correspondence), eq(ContentModel.ASSOC_CONTAINS), anyString()))
            .thenAnswer(invocation -> byName.get(invocation.getArgument(2)));
        when(nodes.createNode(eq(correspondence), eq(ContentModel.ASSOC_CONTAINS), any(QName.class),
                eq(receiptType), anyMap())).thenAnswer(invocation -> {
            Map<QName, Serializable> props = invocation.getArgument(4);
            String filename = (String) props.get(ContentModel.PROP_NAME);
            NodeRef node = new NodeRef("workspace://SpacesStore/" + filename);
            byName.put(filename, node);
            properties.put(node, new HashMap<>(props));
            var association = mock(ChildAssociationRef.class);
            when(association.getChildRef()).thenReturn(node);
            return association;
        });
        when(nodes.getType(any(NodeRef.class))).thenReturn(receiptType);
        when(nodes.getProperty(any(NodeRef.class), any(QName.class)))
            .thenAnswer(invocation -> properties.get(invocation.getArgument(0)).get(invocation.getArgument(1)));
        doAnswer(invocation -> {
            properties.get(invocation.getArgument(0)).put(invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(nodes).setProperty(any(NodeRef.class), any(QName.class), any());

        worker.snapshot(event, List.of(first));
        worker.snapshot(event, List.of(first, later));
        assertEquals(2, byName.size()); // one pending recipient plus the frozen-audience marker
        assertFalse(worker.claim(event, later.employeeId()));
        assertTrue(worker.claim(event, first.employeeId()));
        assertFalse(worker.claim(event, first.employeeId()));
    }

}
