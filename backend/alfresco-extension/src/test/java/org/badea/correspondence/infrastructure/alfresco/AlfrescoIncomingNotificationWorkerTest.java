package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.alfresco.service.cmr.repository.NodeService;
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
}
