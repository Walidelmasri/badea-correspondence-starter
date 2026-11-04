package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.alfresco.service.cmr.security.AuthorityService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceMailboxLister;
import org.badea.correspondence.security.AuthenticatedUserProvider;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;

class TarasolSecretarySharedInboxServiceTest {
    private final DepartmentCode unit = new DepartmentCode("117");
    private final DepartmentCode other = new DepartmentCode("118");
    private final EmployeeDirectoryEntry secretary = new EmployeeDirectoryEntry(
        new EmployeeId("99999"), new DirectoryUsername("walid.salem"),
        "Test Secretary", null, Optional.empty());
    private final TarasolEffectiveAccessReader access = mock(TarasolEffectiveAccessReader.class);
    private final AlfrescoCorrespondenceMailboxLister mailbox = mock(AlfrescoCorrespondenceMailboxLister.class);
    private final AuthorityService authorities = mock(AuthorityService.class);
    private final AuthenticatedUserProvider authenticatedUser = mock(AuthenticatedUserProvider.class);
    private final TarasolSecretarySharedInboxService service =
        new TarasolSecretarySharedInboxService(access, mailbox, authorities, authenticatedUser);

    private void permitSecretaryGroup() {
        when(authenticatedUser.getUsername()).thenReturn("walid.salem");
        when(authorities.getAuthoritiesForUser("walid.salem"))
            .thenReturn(Set.of(TarasolSecretaryGroupPolicy.AUTHORITY,
                "GROUP_BADEA_TARASOL_ADMINS"));
    }

    @Test
    void groupAndOracleAssignmentReadOnlyProjectExistingUnitInbox() {
        permitSecretaryGroup();
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), unit)).thenReturn(true);
        var expected = new AlfrescoCorrespondenceMailboxLister.MailboxPage(List.of(), 0, 10, 0);
        when(mailbox.list("inbox", CorrespondenceActorRole.DIRECTOR, unit,
            "99999", "needs_action", 0, 10)).thenReturn(expected);

        assertSame(expected, service.list(secretary, unit, "needs_action", 0, 10));
        verify(mailbox).list("inbox", CorrespondenceActorRole.DIRECTOR, unit,
            "99999", "needs_action", 0, 10);
        verifyNoMoreInteractions(mailbox);
    }

    @Test
    void caseReadRequiresBothGrantsAndActualInboxMembership() {
        String id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        permitSecretaryGroup();
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), unit)).thenReturn(true);
        when(mailbox.containsDirectorInboxCase(id, unit)).thenReturn(true);
        assertEquals("approved", service.readCase(secretary, unit, id, () -> "approved"));
        verify(mailbox).containsDirectorInboxCase(id, unit);
        when(mailbox.containsDirectorInboxCase(id, unit)).thenReturn(false);
        assertThrows(CorrespondenceAuthorizationException.class,
                () -> service.readCase(secretary, unit, id, () -> "private"));
    }

    @Test
    void caseReadNeverRunsForWrongUnitOrNonSecretary() {
        String id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        permitSecretaryGroup();
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), unit)).thenReturn(true);
        assertThrows(CorrespondenceAuthorizationException.class,
                () -> service.readCase(secretary, other, id, () -> "private"));
        verifyNoInteractions(mailbox);
    }

    @Test
    void oracleAssignmentWithoutSecretaryGroupIsDenied() {
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), unit)).thenReturn(true);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.list(secretary, unit, "all", 0, 10));
        verifyNoInteractions(mailbox);
    }

    @Test
    void secretaryGroupWithoutOracleAssignmentIsDenied() {
        permitSecretaryGroup();
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.list(secretary, unit, "all", 0, 10));
        verifyNoInteractions(mailbox);
    }

    @Test
    void secretaryAssignedToOneUnitCannotReadAnotherUnit() {
        permitSecretaryGroup();
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), unit)).thenReturn(true);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.list(secretary, other, "all", 0, 10));
        verify(mailbox, never()).list(anyString(), any(), any(), any(), anyString(), anyInt(), anyInt());
    }

    @Test
    void rejectsInvalidViewsAndPaginationWithoutReadingRepository() {
        permitSecretaryGroup();
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), unit)).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
            () -> service.list(secretary, unit, "drafts", 0, 10));
        assertThrows(IllegalArgumentException.class,
            () -> service.list(secretary, unit, "sent", 0, 10));
        assertThrows(IllegalArgumentException.class,
            () -> service.list(secretary, unit, "all", -1, 10));
        assertThrows(IllegalArgumentException.class,
            () -> service.list(secretary, unit, "all", 0, 101));
        verifyNoInteractions(mailbox);
    }
}
