package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.badea.correspondence.application.OfficeCycleContinuationService;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoSecretaryRoundTwoRequestRepository;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceMailboxLister;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;

class TarasolSecretaryRoundContinuationApprovalServiceTest {
    private static final String CASE = "217a0e57-bc77-4475-a06f-c72aaa0949c4";
    private static final String REQUEST = "12345678-1234-1234-1234-123456789abc";
    private final TarasolOrganizationReader organization = mock(TarasolOrganizationReader.class);
    private final CorrespondenceActorRoleResolver roles = mock(CorrespondenceActorRoleResolver.class);
    private final AlfrescoSecretaryRoundTwoRequestRepository repository =
        mock(AlfrescoSecretaryRoundTwoRequestRepository.class);
    private final OfficeCycleContinuationService cycles = mock(OfficeCycleContinuationService.class);
    private final AlfrescoCorrespondenceMailboxLister mailbox = mock(AlfrescoCorrespondenceMailboxLister.class);
    private final TarasolSecretaryRoundContinuationApprovalService service =
        new TarasolSecretaryRoundContinuationApprovalService(organization, roles, repository, cycles, mailbox);
    private final DepartmentCode unit = new DepartmentCode("117");
    private final EmployeeDirectoryEntry manager = new EmployeeDirectoryEntry(
        new EmployeeId("200"), new DirectoryUsername("manager.test"),
        "Manager", "مدير", Optional.empty());

    private void authorize(CorrespondenceActorRole role, DepartmentCode target) {
        when(roles.resolveWorkspace(target.value(), manager)).thenReturn(
            new CorrespondenceActorRoleResolver.Workspace(role, target));
        when(organization.findCurrentManagerWorkspaces(manager.employeeId())).thenReturn(
            List.of(new TarasolOrganizationReader.ManagerWorkspace(117L,
                target, "Unit", null, "MANAGER")));
    }

    private void pending(String owner, long cycle) {
        when(repository.pending(CASE, unit)).thenReturn(
            new AlfrescoSecretaryRoundTwoRequestRepository.Request(REQUEST, CASE,
                unit.value(), owner, "Follow up", cycle, "PENDING",
                Instant.parse("2026-11-04T12:00:00Z")));
    }

    @Test void managerQueueFiltersBeforePaginationAndDoesNotAdvanceCycles() {
        authorize(CorrespondenceActorRole.DIRECTOR, unit);
        var first = new AlfrescoCorrespondenceMailboxLister.MailboxSummary(CASE,
            "Returned case", "117-A", "WITH_DIRECTOR", "117", "Originator",
            Instant.parse("2026-11-04T11:00:00Z"), Instant.parse("2026-11-04T12:00:00Z"));
        var other = new AlfrescoCorrespondenceMailboxLister.MailboxSummary(
            "117a0e57-bc77-4475-a06f-c72aaa0949c4", "No pending", null,
            "WITH_DIRECTOR", "117", "Originator",
            Instant.parse("2026-11-04T10:00:00Z"), Instant.parse("2026-11-04T11:00:00Z"));
        when(mailbox.list("inbox", CorrespondenceActorRole.DIRECTOR, unit,
            "200", "all", 0, 100)).thenReturn(
                new AlfrescoCorrespondenceMailboxLister.MailboxPage(
                    List.of(first, other), 0, 100, 2));
        pending("99999", 3L);
        var result = service.pendingQueue(manager, unit, 0, 10);
        assertEquals(1, result.total());
        assertEquals(CASE, result.items().get(0).technicalId());
        assertEquals(REQUEST, result.items().get(0).requestId());
        assertEquals("Returned case", result.items().get(0).subject());
        assertTrue(service.pendingQueue(manager, unit, 1, 10).items().isEmpty());
        verifyNoInteractions(cycles);
    }

    @Test void managerQueueScansLaterPagesAndSkipsAnotherUnitsRequest() {
        authorize(CorrespondenceActorRole.DIRECTOR, unit);
        var first = new AlfrescoCorrespondenceMailboxLister.MailboxSummary(
            "117a0e57-bc77-4475-a06f-c72aaa0949c4", "Other unit request", null,
            "WITH_DIRECTOR", "117", "Sender",
            Instant.parse("2026-11-04T10:00:00Z"), Instant.parse("2026-11-04T11:00:00Z"));
        var next = new AlfrescoCorrespondenceMailboxLister.MailboxSummary(CASE,
            "My unit request", null, "WITH_DIRECTOR", "117", "Sender",
            Instant.parse("2026-11-04T11:00:00Z"), Instant.parse("2026-11-04T12:00:00Z"));
        when(mailbox.list("inbox", CorrespondenceActorRole.DIRECTOR, unit,
            "200", "all", 0, 100)).thenReturn(
                new AlfrescoCorrespondenceMailboxLister.MailboxPage(List.of(first), 0, 100, 2));
        when(mailbox.list("inbox", CorrespondenceActorRole.DIRECTOR, unit,
            "200", "all", 1, 100)).thenReturn(
                new AlfrescoCorrespondenceMailboxLister.MailboxPage(List.of(next), 1, 100, 2));
        when(repository.pending(first.technicalId(), unit)).thenThrow(
            new org.badea.correspondence.application.OfficeCycleConflictException(
                "Request belongs to another unit"));
        pending("99999", 4L);
        var result = service.pendingQueue(manager, unit, 0, 10);
        assertEquals(1, result.total());
        assertEquals(CASE, result.items().get(0).technicalId());
    }

    @Test void unauthorizedManagerCannotDiscoverAnyPendingContinuation() {
        when(roles.resolveWorkspace(unit.value(), manager))
            .thenThrow(new CorrespondenceAuthorizationException("No active Tarasol appointment for workspace"));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.pendingQueue(manager, unit, 0, 10));
        verifyNoInteractions(mailbox, repository, cycles);
    }

    @Test void invalidPaginationRejectedBeforeReadingMailbox() {
        authorize(CorrespondenceActorRole.DIRECTOR, unit);
        assertThrows(IllegalArgumentException.class,
            () -> service.pendingQueue(manager, unit, 0, 101));
        verifyNoInteractions(mailbox, repository, cycles);
    }

    @Test void authorizedDirectorApprovesUsingExistingCycleService() {
        authorize(CorrespondenceActorRole.DIRECTOR, unit);
        pending("99999", 3L);
        when(cycles.continueCycle(any())).thenReturn(4L);
        when(repository.approve(eq(CASE), eq(unit), eq(REQUEST), eq("200"), any()))
            .thenAnswer(inv -> {
                LongSupplier action = inv.getArgument(4);
                assertEquals(4L, action.getAsLong());
                return new AlfrescoSecretaryRoundTwoRequestRepository.Decision(REQUEST,
                    CASE, "APPROVED", 4L);
            });
        assertEquals(4L, service.approve(manager, unit, CASE, REQUEST).workflowCycle());
        verify(cycles).continueCycle(argThat((TransitionCorrespondenceCommand c) ->
            c.action() == CorrespondenceAction.DIRECTOR_CONTINUE_CORRESPONDENCE
            && c.actorRole() == CorrespondenceActorRole.DIRECTOR
            && unit.equals(c.actorDepartmentCode())
            && manager.employeeId().equals(c.actorEmployeeId())
            && "Follow up".equals(c.explanation())));
    }

    @Test void wrongUnitOrNoLiveAppointmentCannotReviewRequest() {
        when(roles.resolveWorkspace(unit.value(), manager)).thenReturn(
            new CorrespondenceActorRoleResolver.Workspace(CorrespondenceActorRole.DIRECTOR, unit));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.pending(manager, unit, CASE));
        verifyNoInteractions(repository, cycles);
    }

    @Test void managerCannotApproveOwnSecretaryRequest() {
        authorize(CorrespondenceActorRole.DIRECTOR, unit);
        pending("200", 3L);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.approve(manager, unit, CASE, REQUEST));
        verify(repository, never()).approve(any(), any(), any(), any(), any());
        verifyNoInteractions(cycles);
    }

    @Test void officeAuthorityCannotBeUsedForAnotherUnit() {
        authorize(CorrespondenceActorRole.PRESIDENT_OFFICE, unit);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.pending(manager, unit, CASE));
    }

    @Test void managerCanReturnWithoutInvokingCycleTransition() {
        authorize(CorrespondenceActorRole.DIRECTOR, unit);
        when(repository.returnForRevision(CASE, unit, REQUEST, "200", "Fix file"))
            .thenReturn(new AlfrescoSecretaryRoundTwoRequestRepository.Decision(
                REQUEST, CASE, "RETURNED", 3L));
        assertEquals("RETURNED", service.returnForRevision(manager, unit, CASE,
            REQUEST, "Fix file").status());
        verifyNoInteractions(cycles);
    }
}
