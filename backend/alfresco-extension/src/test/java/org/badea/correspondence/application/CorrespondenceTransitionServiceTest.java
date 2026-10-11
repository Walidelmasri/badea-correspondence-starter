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

    @BeforeEach
    void setUp() {

        repository =
                mock(
                        CorrespondenceTransitionRepository.class);

        departmentService =
                mock(
                        CorrespondenceDepartmentService.class);

        service =
                new CorrespondenceTransitionService(
                        new CorrespondenceWorkflowService(),
                        new CorrespondenceAccessAuthorizer(),
                        departmentService,
                        repository);
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