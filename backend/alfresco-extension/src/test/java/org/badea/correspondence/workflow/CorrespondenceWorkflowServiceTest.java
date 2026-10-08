package org.badea.correspondence.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CorrespondenceWorkflowServiceTest {

    private final CorrespondenceWorkflowService workflow =
        new CorrespondenceWorkflowService();

    @Test
    void directorCanSendDraftToPresidentOffice() {
        assertEquals(
            CorrespondenceState.WITH_PRESIDENT_OFFICE,
            workflow.nextState(
                CorrespondenceState.DRAFT,
                CorrespondenceActorRole.DIRECTOR,
                CorrespondenceAction.SEND_TO_OFFICE
            )
        );
    }

    @Test
    void presidentOfficeCanSendDraftToPresident() {
        assertEquals(
            CorrespondenceState.WITH_PRESIDENT,
            workflow.nextState(
                CorrespondenceState.DRAFT,
                CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceAction.SEND_TO_PRESIDENT
            )
        );
    }

    @Test
    void presidentOfficeCanSendDraftToDirector() {
        assertEquals(
            CorrespondenceState.WITH_DIRECTOR,
            workflow.nextState(
                CorrespondenceState.DRAFT,
                CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceAction.SEND_TO_DIRECTOR
            )
        );
    }

    @Test
    void presidentOfficeCanRouteToPresident() {
        assertEquals(
            CorrespondenceState.WITH_PRESIDENT,
            workflow.nextState(
                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceAction.SEND_TO_PRESIDENT
            )
        );
    }

    @Test
    void presidentOfficeCanRouteToDirector() {
        assertEquals(
            CorrespondenceState.WITH_DIRECTOR,
            workflow.nextState(
                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceAction.SEND_TO_DIRECTOR
            )
        );
    }

    @Test
    void presidentOfficeCanAdministrativelyClose() {
        assertEquals(
            CorrespondenceState.ARCHIVED,
            workflow.nextState(
                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceAction.ADMINISTRATIVE_CLOSE
            )
        );
    }

    @Test
    void presidentCanReturnToOffice() {
        assertEquals(
            CorrespondenceState.WITH_PRESIDENT_OFFICE,
            workflow.nextState(
                CorrespondenceState.WITH_PRESIDENT,
                CorrespondenceActorRole.PRESIDENT,
                CorrespondenceAction.SEND_TO_OFFICE
            )
        );
    }

    @Test
    void presidentCanMarkCorrespondenceDone() {
        assertEquals(
            CorrespondenceState.ARCHIVED,
            workflow.nextState(
                CorrespondenceState.WITH_PRESIDENT,
                CorrespondenceActorRole.PRESIDENT,
                CorrespondenceAction.MARK_DONE
            )
        );
    }

    @Test
    void directorCanReturnToOffice() {
        assertEquals(
            CorrespondenceState.WITH_PRESIDENT_OFFICE,
            workflow.nextState(
                CorrespondenceState.WITH_DIRECTOR,
                CorrespondenceActorRole.DIRECTOR,
                CorrespondenceAction.SEND_TO_OFFICE
            )
        );
    }

    @Test
    void directorCanMarkCorrespondenceDone() {
        assertEquals(
            CorrespondenceState.ARCHIVED,
            workflow.nextState(
                CorrespondenceState.WITH_DIRECTOR,
                CorrespondenceActorRole.DIRECTOR,
                CorrespondenceAction.MARK_DONE
            )
        );
    }

    @Test
    void directorCannotSendDirectlyToPresident() {
        assertThrows(
            InvalidCorrespondenceTransitionException.class,
            () -> workflow.nextState(
                CorrespondenceState.WITH_DIRECTOR,
                CorrespondenceActorRole.DIRECTOR,
                CorrespondenceAction.SEND_TO_PRESIDENT
            )
        );
    }

    @Test
    void presidentCannotSendDirectlyToDirector() {
        assertThrows(
            InvalidCorrespondenceTransitionException.class,
            () -> workflow.nextState(
                CorrespondenceState.WITH_PRESIDENT,
                CorrespondenceActorRole.PRESIDENT,
                CorrespondenceAction.SEND_TO_DIRECTOR
            )
        );
    }

    @Test
    void actorCannotActOnAnotherRolesInbox() {
        assertThrows(
            InvalidCorrespondenceTransitionException.class,
            () -> workflow.nextState(
                CorrespondenceState.WITH_PRESIDENT,
                CorrespondenceActorRole.DIRECTOR,
                CorrespondenceAction.MARK_DONE
            )
        );
    }

    @Test
    void archivedCorrespondenceCannotTransition() {
        assertThrows(
            InvalidCorrespondenceTransitionException.class,
            () -> workflow.nextState(
                CorrespondenceState.ARCHIVED,
                CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceAction.SEND_TO_DIRECTOR
            )
        );

        assertThrows(
            InvalidCorrespondenceTransitionException.class,
            () -> workflow.nextState(
                CorrespondenceState.ARCHIVED,
                CorrespondenceActorRole.PRESIDENT,
                CorrespondenceAction.MARK_DONE
            )
        );

        assertThrows(
            InvalidCorrespondenceTransitionException.class,
            () -> workflow.nextState(
                CorrespondenceState.ARCHIVED,
                CorrespondenceActorRole.DIRECTOR,
                CorrespondenceAction.SEND_TO_OFFICE
            )
        );
    }

    @Test
    void rejectsNullWorkflowInputs() {
        assertThrows(
            NullPointerException.class,
            () -> workflow.nextState(
                null,
                CorrespondenceActorRole.DIRECTOR,
                CorrespondenceAction.SEND_TO_OFFICE
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> workflow.nextState(
                CorrespondenceState.DRAFT,
                null,
                CorrespondenceAction.SEND_TO_OFFICE
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> workflow.nextState(
                CorrespondenceState.DRAFT,
                CorrespondenceActorRole.DIRECTOR,
                null
            )
        );
    }
}