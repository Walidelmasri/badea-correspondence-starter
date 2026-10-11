package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
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

    private CorrespondenceTransitionService service;

    @BeforeEach
    void setUp() {

        repository =
                mock(
                        CorrespondenceTransitionRepository.class);

        service =
                new CorrespondenceTransitionService(
                        new CorrespondenceWorkflowService(),
                        repository);
    }

    @Test
    void directorSendsDraftToPresidentOffice() {

        when(repository.getCurrentState(
                TECHNICAL_ID))
                .thenReturn(
                        CorrespondenceState.DRAFT);

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.DIRECTOR,
                        CorrespondenceAction.SEND_TO_OFFICE,
                        null,
                        null);

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
    }

    @Test
    void presidentOfficeSendsToSelectedDirector() {

        when(repository.getCurrentState(
                TECHNICAL_ID))
                .thenReturn(
                        CorrespondenceState.WITH_PRESIDENT_OFFICE);

        DepartmentCode targetDepartment =
                new DepartmentCode("117");

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.SEND_TO_DIRECTOR,
                        targetDepartment,
                        null);

        CorrespondenceState result =
                service.transition(
                        command);

        assertEquals(
                CorrespondenceState.WITH_DIRECTOR,
                result);

        verify(repository)
                .applyTransition(
                        command,
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        CorrespondenceState.WITH_DIRECTOR,
                        CorrespondenceActorRole.DIRECTOR);
    }

    @Test
    void sendingToDirectorRequiresDepartment() {

        when(repository.getCurrentState(
                TECHNICAL_ID))
                .thenReturn(
                        CorrespondenceState.WITH_PRESIDENT_OFFICE);

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.SEND_TO_DIRECTOR,
                        null,
                        null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.transition(
                        command));

        verify(repository, never())
                .applyTransition(
                        command,
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        CorrespondenceState.WITH_DIRECTOR,
                        CorrespondenceActorRole.DIRECTOR);
    }

    @Test
    void rejectsDepartmentForNonDirectorRoute() {

        when(repository.getCurrentState(
                TECHNICAL_ID))
                .thenReturn(
                        CorrespondenceState.WITH_PRESIDENT_OFFICE);

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.SEND_TO_PRESIDENT,
                        new DepartmentCode("117"),
                        null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.transition(
                        command));

        verify(repository, never())
                .applyTransition(
                        command,
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        CorrespondenceState.WITH_PRESIDENT,
                        CorrespondenceActorRole.PRESIDENT);
    }

    @Test
    void administrativeCloseRequiresReason() {

        when(repository.getCurrentState(
                TECHNICAL_ID))
                .thenReturn(
                        CorrespondenceState.WITH_PRESIDENT_OFFICE);

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.ADMINISTRATIVE_CLOSE,
                        null,
                        null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.transition(
                        command));

        verify(repository, never())
                .applyTransition(
                        command,
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        CorrespondenceState.ARCHIVED,
                        null);
    }

    @Test
    void administrativeCloseArchivesWithReason() {

        when(repository.getCurrentState(
                TECHNICAL_ID))
                .thenReturn(
                        CorrespondenceState.WITH_PRESIDENT_OFFICE);

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.ADMINISTRATIVE_CLOSE,
                        null,
                        "Closed administratively.");

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
    }

    @Test
    void archivedTransitionClearsTargetRole() {

        when(repository.getCurrentState(
                TECHNICAL_ID))
                .thenReturn(
                        CorrespondenceState.WITH_DIRECTOR);

        TransitionCorrespondenceCommand command =
                command(
                        CorrespondenceActorRole.DIRECTOR,
                        CorrespondenceAction.MARK_DONE,
                        null,
                        null);

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
    }

    private static TransitionCorrespondenceCommand command(
            CorrespondenceActorRole actorRole,
            CorrespondenceAction action,
            DepartmentCode targetDepartmentCode,
            String closureReason) {

        return new TransitionCorrespondenceCommand(
                TECHNICAL_ID,
                actorRole,
                action,
                new EmployeeId("18280"),
                "Test Employee",
                "موظف اختبار",
                targetDepartmentCode,
                null,
                closureReason);
    }
}