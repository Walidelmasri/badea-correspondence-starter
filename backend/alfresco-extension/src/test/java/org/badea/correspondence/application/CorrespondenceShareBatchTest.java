package org.badea.correspondence.application;

import java.util.List;
import java.util.Optional;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.mail.SmtpShareMailSender;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

final class CorrespondenceShareBatchTest {
    private static final String ID = "7fc41b5b-aceb-48fc-9f36-367e4a36e207";
    private static final String FIRST = "f1bc7ab2-b55b-48ab-ae18-676e35615d74";
    private static final String SECOND = "aa4cce12-12ab-4ac0-af58-c154ba4f71e9";
    private final CorrespondenceDetailsService details = mock(CorrespondenceDetailsService.class);
    private final CorrespondenceSharedLinkRepository links = mock(CorrespondenceSharedLinkRepository.class);
    private final EmployeeDirectory directory = mock(EmployeeDirectory.class);
    private final SmtpShareMailSender smtp = mock(SmtpShareMailSender.class);
    private final CorrespondenceShareAuditRepository audit = mock(CorrespondenceShareAuditRepository.class);
    private final CorrespondenceShareService service = new CorrespondenceShareService(details, links, directory, smtp, audit);

    private void allow() {
        when(details.getDetails(eq(ID), eq(CorrespondenceActorRole.DIRECTOR), isNull(), eq("100")))
            .thenReturn(new CorrespondenceDetails(ID, CorrespondenceState.WITH_DIRECTOR,
                    "Test", "", "117/20261025/1", null, null, "117", "Sender", null,
                    List.of(new CorrespondenceDocument(FIRST, "A.pdf", "application/pdf", 300),
                            new CorrespondenceDocument(SECOND, "B.pdf", "application/pdf", 500))));
    }

    private void recipient() {
        when(directory.findActiveByUsername(new DirectoryUsername("staff.member")))
            .thenReturn(Optional.of(new EmployeeDirectoryEntry(new EmployeeId("200"),
                    new DirectoryUsername("staff.member"), "Staff Member", null, Optional.empty())));
    }

    @Test void sendsOneEmailForTwoAuthorizedPdfsWithNativeLinks() {
        allow(); recipient();
        when(links.getOrCreate(FIRST)).thenReturn("link_first_123");
        when(links.getOrCreate(SECOND)).thenReturn("link_second_456");
        var result = service.sendBatch(ID, List.of(FIRST, SECOND), CorrespondenceActorRole.DIRECTOR,
                null, "100", "Sender", "staff.member", "Review <both>");
        assertEquals(2, result.links().size());
        assertEquals("staff.member@badea.org", result.recipient());
        verify(smtp, times(1)).send(eq("staff.member@badea.org"), anyString(),
                contains("B.pdf"), argThat(html -> html.contains("A.pdf") && html.contains("B.pdf")
                        && html.contains("Review &lt;both&gt;")));
        verify(audit, times(2)).append(argThat(e -> e.event().equals("EMAIL_ACCEPTED_BY_SMTP")));
    }

    @Test void unknownNodeCannotCauseEvenOnePublicLink() {
        allow(); recipient();
        assertThrows(CorrespondenceDocumentNotFoundException.class,
            () -> service.sendBatch(ID,
                    List.of(FIRST, "bb4cce12-12ab-4ac0-af58-c154ba4f71e9"),
                    CorrespondenceActorRole.DIRECTOR, null, "100", "Sender", "staff.member", ""));
        verifyNoInteractions(links, smtp);
    }

    @Test void inactiveEmployeeDoesNotGeneratePublicLinks() {
        allow();
        assertThrows(IllegalArgumentException.class,
            () -> service.sendBatch(ID, List.of(FIRST), CorrespondenceActorRole.DIRECTOR,
                null, "100", "Sender", "inactive", ""));
        verifyNoInteractions(links, smtp);
    }

    @Test void duplicateNodesAreRejectedAndNoEmailIsSent() {
        assertThrows(IllegalArgumentException.class,
            () -> service.sendBatch(ID, List.of(FIRST, FIRST), CorrespondenceActorRole.DIRECTOR,
                null, "100", "Sender", "staff.member", ""));
        verifyNoInteractions(details, links, smtp);
    }

    @Test void missingLinkIsANormalEmptyResult() {
        allow();
        when(links.findExisting(FIRST)).thenReturn(Optional.empty());
        assertTrue(service.findExistingLink(ID, FIRST, CorrespondenceActorRole.DIRECTOR,
                null, "100").isEmpty());
        verify(links, never()).getOrCreate(anyString());
    }
}
