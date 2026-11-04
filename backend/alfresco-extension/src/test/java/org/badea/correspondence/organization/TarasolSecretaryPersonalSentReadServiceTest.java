package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.badea.correspondence.application.CorrespondenceDetails;
import org.badea.correspondence.application.CorrespondenceDetailsService;
import org.badea.correspondence.application.CorrespondenceDocumentContentService;
import org.badea.correspondence.application.CorrespondenceDocumentStream;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceHistoryReader;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;

class TarasolSecretaryPersonalSentReadServiceTest {
    private static final String ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String DOC = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private final TarasolSecretaryWorkflowService workflow = mock(TarasolSecretaryWorkflowService.class);
    private final CorrespondenceDetailsService details = mock(CorrespondenceDetailsService.class);
    private final CorrespondenceDocumentContentService content = mock(CorrespondenceDocumentContentService.class);
    private final AlfrescoCorrespondenceHistoryReader history = mock(AlfrescoCorrespondenceHistoryReader.class);
    private final EmployeeDirectoryEntry actor = new EmployeeDirectoryEntry(
        new EmployeeId("99999"), new DirectoryUsername("walid.salem"),
        "Test", null, Optional.empty());
    private final DepartmentCode unit = new DepartmentCode("117");
    private final TarasolSecretaryPersonalSentReadService service =
        new TarasolSecretaryPersonalSentReadService(workflow, details, content, history);

    @Test
    void ownerScopedReadsReuseExistingDirectorReadProjections() {
        when(workflow.readPersonalSent(eq(actor), eq(unit), eq(ID), any()))
            .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get());
        var expectedDetails = mock(CorrespondenceDetails.class);
        when(details.getDetails(ID, CorrespondenceActorRole.DIRECTOR, unit, "99999"))
            .thenReturn(expectedDetails);
        assertSame(expectedDetails, service.details(actor, unit, ID));
        when(history.list(ID)).thenReturn(List.of());
        assertTrue(service.history(actor, unit, ID).isEmpty());
        var document = new CorrespondenceDocumentStream("file.pdf", "application/pdf",
                new ByteArrayInputStream(new byte[] {1}));
        when(content.open(ID, DOC, CorrespondenceActorRole.DIRECTOR, unit, "99999"))
            .thenReturn(document);
        assertSame(document, service.document(actor, unit, ID, DOC));
        verify(workflow, times(3)).readPersonalSent(eq(actor), eq(unit), eq(ID), any());
    }

    @Test
    void deniedPersonalSentCaseCannotReachHistoryDetailsOrBinaryContent() {
        when(workflow.readPersonalSent(eq(actor), eq(unit), eq(ID), any()))
            .thenThrow(new CorrespondenceAuthorizationException("Not secretary owner"));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.details(actor, unit, ID));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.history(actor, unit, ID));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.document(actor, unit, ID, DOC));
        verifyNoInteractions(details, history, content);
    }
}
