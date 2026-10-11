package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PresidentReviewServiceTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private CorrespondenceTransitionRepository correspondenceRepository;
    private PresidentReviewStateRepository reviewStateRepository;

    private PresidentReviewService service;

    @BeforeEach
    void setUp() {

        correspondenceRepository =
                mock(
                        CorrespondenceTransitionRepository.class);

        reviewStateRepository =
                mock(
                        PresidentReviewStateRepository.class);

        service =
                new PresidentReviewService(
                        correspondenceRepository,
                        reviewStateRepository);
    }

    @Test
    void presidentLoadsAssignedReviewState() {

        PresidentReviewState expected =
                new PresidentReviewState(
                        3L,
                        """
                        {
                          "schemaVersion": 1
                        }
                        """);

        when(correspondenceRepository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        presidentContext());

        when(reviewStateRepository.find(
                TECHNICAL_ID))
                .thenReturn(
                        Optional.of(
                                expected));

        Optional<PresidentReviewState> result =
                service.load(
                        TECHNICAL_ID,
                        CorrespondenceActorRole.PRESIDENT);

        assertTrue(
                result.isPresent());

        assertEquals(
                expected,
                result.orElseThrow());
    }

    @Test
    void presidentMayLoadCorrespondenceWithNoSavedReviewStateYet() {

        when(correspondenceRepository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        presidentContext());

        when(reviewStateRepository.find(
                TECHNICAL_ID))
                .thenReturn(
                        Optional.empty());

        Optional<PresidentReviewState> result =
                service.load(
                        TECHNICAL_ID,
                        CorrespondenceActorRole.PRESIDENT);

        assertTrue(
                result.isEmpty());
    }

    @Test
    void rejectsNonPresidentBeforeReadingReviewState() {

        when(correspondenceRepository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        presidentContext());

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> service.load(
                        TECHNICAL_ID,
                        CorrespondenceActorRole.DIRECTOR));

        verify(
                reviewStateRepository,
                never())
                .find(
                        TECHNICAL_ID);
    }

    @Test
    void rejectsPresidentWhenCorrespondenceIsNotWithPresident() {

        when(correspondenceRepository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        new CorrespondenceAccessContext(
                                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                                new DepartmentCode("117"),
                                CorrespondenceActorRole.PRESIDENT_OFFICE,
                                null));

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> service.load(
                        TECHNICAL_ID,
                        CorrespondenceActorRole.PRESIDENT));

        verify(
                reviewStateRepository,
                never())
                .find(
                        TECHNICAL_ID);
    }

    @Test
    void rejectsPresidentWhenAssignmentDoesNotMatch() {

        when(correspondenceRepository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        new CorrespondenceAccessContext(
                                CorrespondenceState.WITH_PRESIDENT,
                                new DepartmentCode("117"),
                                CorrespondenceActorRole.DIRECTOR,
                                new DepartmentCode("117")));

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> service.load(
                        TECHNICAL_ID,
                        CorrespondenceActorRole.PRESIDENT));

        verify(
                reviewStateRepository,
                never())
                .find(
                        TECHNICAL_ID);
    }

    @Test
    void presidentSavesAssignedReviewState() {

        String json =
                """
                {
                  "schemaVersion": 1,
                  "selectedRecipientIds": ["117"]
                }
                """;

        SavePresidentReviewStateCommand command =
                new SavePresidentReviewStateCommand(
                        TECHNICAL_ID,
                        2L,
                        new EmployeeId("18280"),
                        json);

        PresidentReviewState expected =
                new PresidentReviewState(
                        3L,
                        json);

        when(correspondenceRepository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        presidentContext());

        when(reviewStateRepository.save(
                command))
                .thenReturn(
                        expected);

        PresidentReviewState result =
                service.save(
                        command,
                        CorrespondenceActorRole.PRESIDENT);

        assertEquals(
                expected,
                result);

        verify(reviewStateRepository)
                .save(
                        command);
    }

    @Test
    void rejectsUnauthorizedSaveBeforeRepositoryMutation() {

        String json =
                """
                {
                  "schemaVersion": 1
                }
                """;

        SavePresidentReviewStateCommand command =
                new SavePresidentReviewStateCommand(
                        TECHNICAL_ID,
                        0L,
                        new EmployeeId("18280"),
                        json);

        when(correspondenceRepository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(
                        new CorrespondenceAccessContext(
                                CorrespondenceState.WITH_DIRECTOR,
                                new DepartmentCode("117"),
                                CorrespondenceActorRole.DIRECTOR,
                                new DepartmentCode("117")));

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> service.save(
                        command,
                        CorrespondenceActorRole.PRESIDENT));

        verify(
                reviewStateRepository,
                never())
                .save(
                        command);
    }

    private static CorrespondenceAccessContext presidentContext() {

        return new CorrespondenceAccessContext(
                CorrespondenceState.WITH_PRESIDENT,
                new DepartmentCode("117"),
                CorrespondenceActorRole.PRESIDENT,
                null);
    }
}