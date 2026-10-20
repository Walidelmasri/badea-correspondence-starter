package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.badea.correspondence.workflow.CorrespondenceWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorrespondenceTransitionServiceTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private CorrespondenceTransitionRepository repository;
    private CorrespondenceDepartmentService departmentService;

    private CorrespondenceTransitionService service;
    private PresidentDocumentFinalizer finalizer;

    @BeforeEach
    void setUp() {

        repository =
                mock(
                        CorrespondenceTransitionRepository.class);

        departmentService =
                mock(
                        CorrespondenceDepartmentService.class);

        finalizer = mock(PresidentDocumentFinalizer.class);

        service =
                new CorrespondenceTransitionService(
                        new CorrespondenceWorkflowService(),
                        new CorrespondenceAccessAuthorizer(),
                        departmentService,
                        repository,
                        finalizer);
    }

    @Test
    void directorSendsOwnDepartmentDraftToPresidentOffice() {

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.DIRECTOR,
                        CorrespondenceAction.SEND_TO_OFFICE,
                        new DepartmentCode("117"),
                        null,
                        null);

        CorrespondenceAccessContext context =
                new CorrespondenceAccessContext(
                        CorrespondenceState.DRAFT,
                        new DepartmentCode("117"),
                        null,
                        null);

        when(repository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        context);

        CorrespondenceState result =
                service.transition(
                        command);

        assertEquals(
                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                result);

        verify(repository)
                .applyTransition(
                        command,
                        CorrespondenceState.DRAFT,
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        CorrespondenceActorRole.PRESIDENT_OFFICE);

        verify(
                departmentService,
                never())
                .requireDepartment(
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDirectorActingOnAnotherDepartmentsDraft() {

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.DIRECTOR,
                        CorrespondenceAction.SEND_TO_OFFICE,
                        new DepartmentCode("117"),
                        null,
                        null);

        when(repository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        new CorrespondenceAccessContext(
                                CorrespondenceState.DRAFT,
                                new DepartmentCode("118"),
                                null,
                                null));

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> service.transition(
                        command));

        verify(repository, never())
                .applyTransition(
                        command,
                        CorrespondenceState.DRAFT,
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        CorrespondenceActorRole.PRESIDENT_OFFICE);

        verify(
                departmentService,
                never())
                .requireDepartment(
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void presidentOfficeSendsToExistingSelectedDirectorDepartment() {

        DepartmentCode targetDepartment =
                new DepartmentCode("117");

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.SEND_TO_DIRECTOR,
                        new DepartmentCode("101"),
                        targetDepartment,
                        null);

        when(repository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        new CorrespondenceAccessContext(
                                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                                new DepartmentCode("118"),
                                CorrespondenceActorRole.PRESIDENT_OFFICE,
                                null));

        CorrespondenceState result =
                service.transition(
                        command);

        assertEquals(
                CorrespondenceState.WITH_DIRECTOR,
                result);

        verify(departmentService)
                .requireDepartment(
                        targetDepartment);

        verify(repository)
                .applyTransition(
                        command,
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        CorrespondenceState.WITH_DIRECTOR,
                        CorrespondenceActorRole.DIRECTOR);
    }

    @Test
    void rejectsUnknownDirectorTargetDepartment() {

        DepartmentCode targetDepartment =
                new DepartmentCode("999");

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.SEND_TO_DIRECTOR,
                        new DepartmentCode("101"),
                        targetDepartment,
                        null);

        when(repository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        officeContext());

        when(departmentService.requireDepartment(
                targetDepartment))
                .thenThrow(
                        new IllegalArgumentException(
                                "Unknown correspondence department: 999"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.transition(
                        command));

        verify(departmentService)
                .requireDepartment(
                        targetDepartment);

        verify(
                repository,
                never())
                .applyTransition(
                        command,
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        CorrespondenceState.WITH_DIRECTOR,
                        CorrespondenceActorRole.DIRECTOR);
    }

    @Test
    void sendingToDirectorRequiresTargetDepartment() {

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.SEND_TO_DIRECTOR,
                        new DepartmentCode("101"),
                        null,
                        null);

        when(repository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        officeContext());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.transition(
                        command));

        verify(
                departmentService,
                never())
                .requireDepartment(
                        org.mockito.ArgumentMatchers.any());

        verify(
                repository,
                never())
                .applyTransition(
                        command,
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        CorrespondenceState.WITH_DIRECTOR,
                        CorrespondenceActorRole.DIRECTOR);
    }

    @Test
    void rejectsTargetDepartmentForNonDirectorRoute() {

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.SEND_TO_PRESIDENT,
                        new DepartmentCode("101"),
                        new DepartmentCode("117"),
                        null);

        when(repository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        officeContext());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.transition(
                        command));

        verify(
                departmentService,
                never())
                .requireDepartment(
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void administrativeCloseRequiresReason() {

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.ADMINISTRATIVE_CLOSE,
                        new DepartmentCode("101"),
                        null,
                        null);

        when(repository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        officeContext());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.transition(
                        command));

        verify(
                departmentService,
                never())
                .requireDepartment(
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void administrativeCloseArchivesWithReason() {

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.ADMINISTRATIVE_CLOSE,
                        new DepartmentCode("101"),
                        null,
                        "Closed administratively.");

        when(repository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        officeContext());

        CorrespondenceState result =
                service.transition(
                        command);

        assertEquals(
                CorrespondenceState.ARCHIVED,
                result);

        verify(repository)
                .applyTransition(
                        command,
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        CorrespondenceState.ARCHIVED,
                        null);

        verify(
                departmentService,
                never())
                .requireDepartment(
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void officeArchivesOnlyAfterEveryRequiredDepartmentFinishes() {
        var complete = assignment("117", DepartmentRecipientType.TO, DepartmentAssignmentStatus.COMPLETED);
        var copy = assignment("118", DepartmentRecipientType.COPY, DepartmentAssignmentStatus.PENDING);
        TransitionCorrespondenceCommand close = command(CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceAction.ADMINISTRATIVE_CLOSE, new DepartmentCode("101"), null,
                "All required replies reviewed");
        when(repository.getAccessContext(TECHNICAL_ID)).thenReturn(distributed(complete, copy));
        assertEquals(CorrespondenceState.ARCHIVED, service.transition(close));
        verify(repository).applyTransition(close, CorrespondenceState.WITH_DIRECTOR,
                CorrespondenceState.ARCHIVED, null);
    }

    @Test
    void officeCannotCloseWhenAnyToDepartmentIsPending() {
        var complete = assignment("117", DepartmentRecipientType.TO, DepartmentAssignmentStatus.COMPLETED);
        var pending = assignment("119", DepartmentRecipientType.TO, DepartmentAssignmentStatus.PENDING);
        TransitionCorrespondenceCommand close = command(CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceAction.ADMINISTRATIVE_CLOSE, new DepartmentCode("101"), null, "Not yet ready");
        when(repository.getAccessContext(TECHNICAL_ID)).thenReturn(distributed(complete, pending));
        assertThrows(org.badea.correspondence.workflow.InvalidCorrespondenceTransitionException.class,
                () -> service.transition(close));
        verify(repository, never()).applyTransition(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void directorCannotArchiveEntireDistributedCorrespondence() {
        var complete = assignment("117", DepartmentRecipientType.TO, DepartmentAssignmentStatus.COMPLETED);
        TransitionCorrespondenceCommand close = command(CorrespondenceActorRole.DIRECTOR,
                CorrespondenceAction.MARK_DONE, new DepartmentCode("117"), null, null);
        when(repository.getAccessContext(TECHNICAL_ID)).thenReturn(distributed(complete));
        assertThrows(org.badea.correspondence.workflow.InvalidCorrespondenceTransitionException.class,
                () -> service.transition(close));
    }

    private static CorrespondenceAccessContext distributed(DepartmentAssignment... items) {
        return new CorrespondenceAccessContext(CorrespondenceState.WITH_DIRECTOR,
                new DepartmentCode("117"), CorrespondenceActorRole.DIRECTOR, null,
                java.util.List.of(), java.util.List.of(items));
    }

    private static DepartmentAssignment assignment(String code, DepartmentRecipientType type,
            DepartmentAssignmentStatus status) {
        return new DepartmentAssignment("assignment-" + code, TECHNICAL_ID, new DepartmentCode(code),
                type, status, "18280", java.time.Instant.parse("2026-10-20T11:41:03Z"),
                status == DepartmentAssignmentStatus.COMPLETED ? "18280" : null,
                status == DepartmentAssignmentStatus.COMPLETED
                        ? java.time.Instant.parse("2026-10-20T11:49:27Z") : null,
                status == DepartmentAssignmentStatus.COMPLETED ? "Completed" : null);
    }

    @Test
    void directorMarksAssignedCorrespondenceDone() {

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.DIRECTOR,
                        CorrespondenceAction.MARK_DONE,
                        new DepartmentCode("117"),
                        null,
                        null);

        when(repository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        new CorrespondenceAccessContext(
                                CorrespondenceState.WITH_DIRECTOR,
                                new DepartmentCode("111"),
                                CorrespondenceActorRole.DIRECTOR,
                                new DepartmentCode("117")));

        CorrespondenceState result =
                service.transition(
                        command);

        assertEquals(
                CorrespondenceState.ARCHIVED,
                result);

        verify(repository)
                .applyTransition(
                        command,
                        CorrespondenceState.WITH_DIRECTOR,
                        CorrespondenceState.ARCHIVED,
                        null);

        verify(
                departmentService,
                never())
                .requireDepartment(
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void presidentSubmissionFinalizesBeforeChangingWorkflowState() {
        TransitionCorrespondenceCommand command = command(
                CorrespondenceActorRole.PRESIDENT, CorrespondenceAction.SEND_TO_OFFICE,
                null, null, null);
        when(repository.getAccessContext(TECHNICAL_ID)).thenReturn(
                new CorrespondenceAccessContext(CorrespondenceState.WITH_PRESIDENT,
                        new DepartmentCode("117"), CorrespondenceActorRole.PRESIDENT, null));
        assertEquals(CorrespondenceState.WITH_PRESIDENT_OFFICE, service.transition(command));
        org.mockito.InOrder sequence = org.mockito.Mockito.inOrder(finalizer, repository);
        sequence.verify(finalizer).finalizeSubmission(command);
        sequence.verify(repository).applyTransition(command,
                CorrespondenceState.WITH_PRESIDENT,
                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                CorrespondenceActorRole.PRESIDENT_OFFICE);
    }

    @Test
    void failedPresidentFinalizationNeverTransitions() {
        TransitionCorrespondenceCommand command = command(
                CorrespondenceActorRole.PRESIDENT, CorrespondenceAction.SEND_TO_OFFICE,
                null, null, null);
        when(repository.getAccessContext(TECHNICAL_ID)).thenReturn(
                new CorrespondenceAccessContext(CorrespondenceState.WITH_PRESIDENT,
                        new DepartmentCode("117"), CorrespondenceActorRole.PRESIDENT, null));
        org.mockito.Mockito.doThrow(new IllegalStateException("bad PDF"))
                .when(finalizer).finalizeSubmission(command);
        assertThrows(IllegalStateException.class, () -> service.transition(command));
        verify(repository, never()).applyTransition(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static CorrespondenceAccessContext officeContext() {

        return new CorrespondenceAccessContext(
                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                new DepartmentCode("117"),
                CorrespondenceActorRole.PRESIDENT_OFFICE,
                null);
    }

    private static TransitionCorrespondenceCommand command(
            CorrespondenceActorRole actorRole,
            CorrespondenceAction action,
            DepartmentCode actorDepartmentCode,
            DepartmentCode targetDepartmentCode,
            String closureReason) {

        return new TransitionCorrespondenceCommand(
                TECHNICAL_ID,
                actorRole,
                action,
                new EmployeeId("18280"),
                actorDepartmentCode,
                "Test Employee",
                "موظف اختبار",
                targetDepartmentCode,
                null,
                closureReason);
    }
}