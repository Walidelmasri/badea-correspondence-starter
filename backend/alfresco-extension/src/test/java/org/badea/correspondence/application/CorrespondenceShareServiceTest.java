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

final class CorrespondenceShareServiceTest {
    private static final String TECH_ID = "7fc41b5b-aceb-48fc-9f36-367e4a36e207";
    private static final String NODE_ID = "f1bc7ab2-b55b-48ab-ae18-676e35615d74";
    private final CorrespondenceDetailsService details = mock(CorrespondenceDetailsService.class);
    private final CorrespondenceSharedLinkRepository links = mock(CorrespondenceSharedLinkRepository.class);
    private final EmployeeDirectory directory = mock(EmployeeDirectory.class);
    private final SmtpShareMailSender smtp = mock(SmtpShareMailSender.class);
    private final CorrespondenceShareAuditRepository audit = mock(CorrespondenceShareAuditRepository.class);
    private final CorrespondenceShareService service = new CorrespondenceShareService(details, links, directory, smtp, audit);

    private void allowPdf() {
        when(details.getDetails(eq(TECH_ID), eq(CorrespondenceActorRole.DIRECTOR), isNull(), eq("100")))
                .thenReturn(new CorrespondenceDetails(TECH_ID, CorrespondenceState.WITH_DIRECTOR,
                        "Test", "", "117/20261025/1", null, null, "117", "Sender", null,
                        List.of(new CorrespondenceDocument(NODE_ID, "Letter.pdf", "application/pdf", 400))));
    }

    @Test void deniesUnknownNodeBeforeAnyLinkOrSmtpAction() {
        allowPdf();
        assertThrows(CorrespondenceDocumentNotFoundException.class,
                () -> service.createLink(TECH_ID, "df4a109e-dc44-4992-9f16-e5816266c123",
                        CorrespondenceActorRole.DIRECTOR, null, "100"));
        verifyNoInteractions(links, smtp, directory);
    }

    @Test void sendsOnlyToActiveDirectoryEmployeeUsingExistingLink() {
        allowPdf();
        when(links.requireExisting(NODE_ID)).thenReturn("ABCdefghi_123");
        when(directory.findActiveByUsername(new DirectoryUsername("user.one")))
                .thenReturn(Optional.of(new EmployeeDirectoryEntry(new EmployeeId("200"),
                        new DirectoryUsername("user.one"), "Recipient", null, Optional.empty())));
        var result = service.send(TECH_ID, NODE_ID, CorrespondenceActorRole.DIRECTOR, null,
                "100", "Sender", "user.one", "Please review");
        assertEquals("user.one@badea.org", result.recipient());
        assertEquals("https://alfresco.internal.badea.org/#/preview/s/ABCdefghi_123", result.link().url());
        verify(links, never()).getOrCreate(anyString());
        verify(smtp).send(eq("user.one@badea.org"), anyString(),
                contains("117/20261025/1"), contains("Please review"));
    }

    @Test void reusesExistingNativeShareAndDoesNotSendEmailDuringLinkCreation() {
        allowPdf();
        when(links.getOrCreate(NODE_ID)).thenReturn("existing_12345");
        var result = service.createLink(TECH_ID, NODE_ID,
                CorrespondenceActorRole.DIRECTOR, null, "100");
        assertEquals("existing_12345", result.sharedId());
        assertEquals("Letter.pdf", result.filename());
        verifyNoInteractions(smtp, directory);
    }

    @Test void refusesNewSharingOfArchivedCorrespondence() {
        when(details.getDetails(eq(TECH_ID), eq(CorrespondenceActorRole.DIRECTOR), isNull(), eq("100")))
                .thenReturn(new CorrespondenceDetails(TECH_ID, CorrespondenceState.ARCHIVED,
                        "Test", "", "117/20261025/1", null, null, "117", "Sender", null,
                        List.of(new CorrespondenceDocument(NODE_ID, "Letter.pdf", "application/pdf", 400))));
        assertThrows(org.badea.correspondence.security.CorrespondenceAuthorizationException.class,
                () -> service.createLink(TECH_ID, NODE_ID,
                        CorrespondenceActorRole.DIRECTOR, null, "100"));
        verifyNoInteractions(links, smtp);
    }

    @Test void rejectsUntrustedUsernameAndMissingEmployeeWithoutMailing() {
        allowPdf();
        assertThrows(IllegalArgumentException.class, () -> service.send(TECH_ID, NODE_ID,
                CorrespondenceActorRole.DIRECTOR, null, "100", "Sender",
                "someone@external.test", ""));
        assertThrows(IllegalArgumentException.class, () -> service.send(TECH_ID, NODE_ID,
                CorrespondenceActorRole.DIRECTOR, null, "100", "Sender", "absent", ""));
        verifyNoInteractions(smtp);
    }
    @Test void archivedRecordsCanReadExistingLinkAndRevokeWithoutCreatingANewOne() {
        when(details.getDetails(eq(TECH_ID), eq(CorrespondenceActorRole.DIRECTOR), isNull(), eq("100")))
                .thenReturn(new CorrespondenceDetails(TECH_ID, CorrespondenceState.ARCHIVED,
                        "Test", "", "117/20261025/1", null, null, "117", "Sender", null,
                        List.of(new CorrespondenceDocument(NODE_ID, "Letter.pdf", "application/pdf", 400))));
        when(links.requireExisting(NODE_ID)).thenReturn("existing_12345");
        assertEquals("existing_12345", service.existingLink(TECH_ID, NODE_ID,
                CorrespondenceActorRole.DIRECTOR, null, "100").sharedId());
        service.revoke(TECH_ID, NODE_ID, CorrespondenceActorRole.DIRECTOR, null, "100");
        verify(links).revoke(NODE_ID);
        verify(links, never()).getOrCreate(anyString());
        verify(audit).append(argThat(event -> event.event().equals("LINK_REVOKED")
                && event.sharedId().equals("existing_12345")));
    }

    @Test void recordsAttemptAndSmtpAcceptanceWithOptionalMessage() {
        allowPdf();
        when(links.requireExisting(NODE_ID)).thenReturn("existing_12345");
        when(directory.findActiveByUsername(new DirectoryUsername("employee")))
                .thenReturn(Optional.of(new EmployeeDirectoryEntry(new EmployeeId("301"),
                        new DirectoryUsername("employee"), "Employee", null, Optional.empty())));
        service.send(TECH_ID, NODE_ID, CorrespondenceActorRole.DIRECTOR, null,
                "100", "Sender", "employee", "A note");
        verify(audit).append(argThat(e -> e.event().equals("EMAIL_ATTEMPTED")
                && "A note".equals(e.comment())));
        verify(audit).append(argThat(e -> e.event().equals("EMAIL_ACCEPTED_BY_SMTP")
                && "employee@badea.org".equals(e.recipient())));
    }

    @Test void recordsUncertainMailFailureAndDoesNotRetry() {
        allowPdf();
        when(links.requireExisting(NODE_ID)).thenReturn("existing_12345");
        when(directory.findActiveByUsername(new DirectoryUsername("employee")))
                .thenReturn(Optional.of(new EmployeeDirectoryEntry(new EmployeeId("301"),
                        new DirectoryUsername("employee"), "Employee", null, Optional.empty())));
        doThrow(new SmtpShareMailSender.MailSubmissionException("Uncertain"))
                .when(smtp).send(anyString(), anyString(), anyString(), anyString());
        assertThrows(SmtpShareMailSender.MailSubmissionException.class,
                () -> service.send(TECH_ID, NODE_ID, CorrespondenceActorRole.DIRECTOR,
                        null, "100", "Sender", "employee", null));
        verify(smtp, times(1)).send(anyString(), anyString(), anyString(), anyString());
        verify(audit).append(argThat(e -> e.event().equals("EMAIL_FAILED_OR_UNCERTAIN")));
    }
}
