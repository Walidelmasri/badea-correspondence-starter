package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;

class OfficeCycleContinuationServiceTest {
    private final OfficeCycleContinuationRepository repository = mock(OfficeCycleContinuationRepository.class);
    private final OfficeCycleContinuationService service = new OfficeCycleContinuationService(repository);

    private TransitionCorrespondenceCommand command(CorrespondenceActorRole role,
            CorrespondenceAction action) {
        return new TransitionCorrespondenceCommand("b9ff7420-3ae3-4b19-9a35-42fe2ba29c91",
                role, action, new EmployeeId("99999"), null, "Office Clerk", null,
                null, "Begin another workflow round", null);
    }

    @Test void officeCanBeginNextCycle() {
        var command = command(CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceAction.CONTINUE_CORRESPONDENCE);
        when(repository.continueToNextCycle(command)).thenReturn(2L);
        assertEquals(2L, service.continueCycle(command));
        verify(repository).continueToNextCycle(command);
    }

    @Test void presidentAndDirectorCannotBeginCycle() {
        for (var role : new CorrespondenceActorRole[]{CorrespondenceActorRole.PRESIDENT,
                CorrespondenceActorRole.DIRECTOR}) {
            assertThrows(CorrespondenceAuthorizationException.class,
                    () -> service.continueCycle(command(role, CorrespondenceAction.CONTINUE_CORRESPONDENCE)));
        }
        verifyNoInteractions(repository);
    }

    @Test void directorCanBeginNewRoundOnlyWithDepartmentAndExplanation() {
        var command = new TransitionCorrespondenceCommand("b9ff7420-3ae3-4b19-9a35-42fe2ba29c91",
                CorrespondenceActorRole.DIRECTOR, CorrespondenceAction.DIRECTOR_CONTINUE_CORRESPONDENCE,
                new EmployeeId("99999"), new org.badea.correspondence.directory.DepartmentCode("117"),
                "Director", null, null, "New PDF for same subject", null);
        when(repository.continueToNextCycle(command)).thenReturn(3L);
        assertEquals(3L, service.continueCycle(command));
        verify(repository).continueToNextCycle(command);
    }

    @Test void directorWithoutDepartmentCannotInitiateNewRound() {
        assertThrows(IllegalArgumentException.class, () -> service.continueCycle(
                command(CorrespondenceActorRole.DIRECTOR,
                        CorrespondenceAction.DIRECTOR_CONTINUE_CORRESPONDENCE)));
        verifyNoInteractions(repository);
    }

    @Test void cannotUseGenericHandoffOrArchiveAction() {
        assertThrows(IllegalArgumentException.class, () -> service.continueCycle(
                command(CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.ADMINISTRATIVE_CLOSE)));
        verifyNoInteractions(repository);
    }
}
