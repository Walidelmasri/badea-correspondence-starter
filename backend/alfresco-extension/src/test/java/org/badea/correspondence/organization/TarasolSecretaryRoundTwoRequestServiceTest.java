package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Set;
import java.util.function.Supplier;
import org.alfresco.service.cmr.security.AuthorityService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoSecretaryRoundTwoRequestRepository;
import org.badea.correspondence.security.AuthenticatedUserProvider;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TarasolSecretaryRoundTwoRequestServiceTest {
    private final TarasolEffectiveAccessReader access = mock(TarasolEffectiveAccessReader.class);
    private final AuthorityService groups = mock(AuthorityService.class);
    private final AuthenticatedUserProvider username = mock(AuthenticatedUserProvider.class);
    private final TarasolSecretarySharedInboxService inbox = mock(TarasolSecretarySharedInboxService.class);
    private final AlfrescoSecretaryRoundTwoRequestRepository repo = mock(AlfrescoSecretaryRoundTwoRequestRepository.class);
    private final TarasolSecretaryRoundTwoRequestService service = new TarasolSecretaryRoundTwoRequestService(
        access, groups, username, inbox, repo);
    private final EmployeeDirectoryEntry actor = mock(EmployeeDirectoryEntry.class);
    private final DepartmentCode unit = new DepartmentCode("117");
    private static final String ID = "217a0e57-bc77-4475-a06f-c72aaa0949c4";

    @BeforeEach void setup() {
        when(actor.employeeId()).thenReturn(new org.badea.correspondence.directory.EmployeeId("99999"));
        when(username.getUsername()).thenReturn("walid.salem");
        when(groups.getAuthoritiesForUser("walid.salem"))
            .thenReturn(Set.of(TarasolSecretaryGroupPolicy.AUTHORITY));
        when(access.hasActiveSecretaryAssignment(actor.employeeId(), unit)).thenReturn(true);
        when(inbox.readCase(eq(actor), eq(unit), eq(ID), any()))
            .thenAnswer(call -> ((Supplier<?>) call.getArgument(3)).get());
    }

    @Test void secretaryRequestIsPersistedWithoutCallingDirectorContinuation() {
        var expected = new AlfrescoSecretaryRoundTwoRequestRepository.Request("request-1", ID,
            "117", "99999", "Follow up", 2L, "PREPARING", Instant.parse("2026-11-04T12:00:00Z"));
        when(repo.submit(ID, unit, "99999", "Follow up")).thenReturn(expected);
        assertSame(expected, service.submit(actor, unit, ID, "  Follow up  "));
        verify(repo).submit(ID, unit, "99999", "Follow up");
        verify(inbox).readCase(eq(actor), eq(unit), eq(ID), any());
    }

    @Test void missingSecretaryGroupBlocksRequestEvenWithOracleAssignment() {
        when(groups.getAuthoritiesForUser("walid.salem")).thenReturn(Set.of());
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.submit(actor, unit, ID, "Reason"));
        verifyNoInteractions(repo);
    }

    @Test void expiredOrWrongUnitAssignmentBlocksRequest() {
        when(access.hasActiveSecretaryAssignment(actor.employeeId(), unit)).thenReturn(false);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.submit(actor, unit, ID, "Reason"));
        verifyNoInteractions(repo);
    }

    @Test void emptyReasonNeverCreatesRequest() {
        assertThrows(IllegalArgumentException.class,
            () -> service.submit(actor, unit, ID, "  "));
        verifyNoInteractions(repo);
    }

    @Test void explicitSubmissionRequiresActiveGroupAndOracleUnitAssignment() {
        String requestId = "12345678-1234-1234-1234-123456789abc";
        service.submitPrepared(actor, unit, ID, requestId);
        verify(repo).submitPrepared(ID, unit, "99999", requestId);
        when(access.hasActiveSecretaryAssignment(actor.employeeId(), unit)).thenReturn(false);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.submitPrepared(actor, unit, ID, requestId));
        verify(repo, times(1)).submitPrepared(ID, unit, "99999", requestId);
    }

    @Test void returnedRevisionAndResubmissionAlwaysCheckSecretaryAccessAndCaseVisibility() {
        String requestId = "12345678-1234-1234-1234-123456789abc";
        service.revise(actor, unit, ID, requestId, 2, "Revised explanation");
        service.resubmit(actor, unit, ID, requestId, 2);
        verify(repo).revise(ID, unit, "99999", requestId, 2, "Revised explanation");
        verify(repo).resubmit(ID, unit, "99999", requestId, 2);
        verify(inbox, times(2)).readCase(eq(actor), eq(unit), eq(ID), any());
    }

    @Test void revokedOracleAssignmentBlocksEditsAndResubmission() {
        when(access.hasActiveSecretaryAssignment(actor.employeeId(), unit)).thenReturn(false);
        assertThrows(CorrespondenceAuthorizationException.class, () ->
            service.revise(actor, unit, ID, "request-1", 1, "New explanation"));
        assertThrows(CorrespondenceAuthorizationException.class, () ->
            service.resubmit(actor, unit, ID, "request-1", 1));
        verifyNoInteractions(repo);
    }

    @Test void lostSecretaryGroupBlocksEditsEvenWithAnActiveAssignment() {
        when(groups.getAuthoritiesForUser("walid.salem")).thenReturn(Set.of());
        assertThrows(CorrespondenceAuthorizationException.class, () ->
            service.revise(actor, unit, ID, "request-1", 1, "New explanation"));
        verifyNoInteractions(repo);
    }
}
