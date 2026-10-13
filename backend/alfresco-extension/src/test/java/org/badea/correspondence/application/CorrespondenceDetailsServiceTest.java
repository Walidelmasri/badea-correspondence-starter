package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorrespondenceDetailsServiceTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private CorrespondenceTransitionRepository
            transitionRepository;

    private CorrespondenceDetailsRepository
            detailsRepository;

    private CorrespondenceDetailsService service;

    @BeforeEach
    void setUp() {

        transitionRepository =
                mock(CorrespondenceTransitionRepository.class);

        detailsRepository =
                mock(CorrespondenceDetailsRepository.class);

        service =
                new CorrespondenceDetailsService(
                        transitionRepository,
                        detailsRepository,
                        new CorrespondenceAccessAuthorizer());
    }

    @Test
    void presidentCanReadAssignedCorrespondence() {

        CorrespondenceAccessContext context =
                new CorrespondenceAccessContext(
                        CorrespondenceState.WITH_PRESIDENT,
                        new DepartmentCode("117"),
                        CorrespondenceActorRole.PRESIDENT,
                        null);

        CorrespondenceDetails expected =
                new CorrespondenceDetails(
                        TECHNICAL_ID,
                        CorrespondenceState.WITH_PRESIDENT,
                        "Test correspondence",
                        null,
                        null,
                        null,
                        null,
                        "117",
                        "Test Employee",
                        null,
                        List.of());

        when(transitionRepository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(context);

        when(detailsRepository.findByTechnicalId(
                TECHNICAL_ID))
                .thenReturn(expected);

        CorrespondenceDetails actual =
                service.getDetails(
                        TECHNICAL_ID,
                        CorrespondenceActorRole.PRESIDENT,
                        null);

        assertEquals(expected, actual);

        verify(detailsRepository)
                .findByTechnicalId(TECHNICAL_ID);
    }

    @Test
    void directorCannotReadCorrespondenceAssignedToPresident() {

        CorrespondenceAccessContext context =
                new CorrespondenceAccessContext(
                        CorrespondenceState.WITH_PRESIDENT,
                        new DepartmentCode("117"),
                        CorrespondenceActorRole.PRESIDENT,
                        null);

        when(transitionRepository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(context);

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> service.getDetails(
                        TECHNICAL_ID,
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("117")));

        verify(detailsRepository, never())
                .findByTechnicalId(TECHNICAL_ID);
    }

    @Test
    void assignedDirectorCanReadOwnDepartmentCorrespondence() {

        CorrespondenceAccessContext context =
                new CorrespondenceAccessContext(
                        CorrespondenceState.WITH_DIRECTOR,
                        new DepartmentCode("101"),
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("117"));

        CorrespondenceDetails expected =
                new CorrespondenceDetails(
                        TECHNICAL_ID,
                        CorrespondenceState.WITH_DIRECTOR,
                        "Assigned correspondence",
                        null,
                        null,
                        null,
                        null,
                        "101",
                        "Test Employee",
                        null,
                        List.of());

        when(transitionRepository.getAccessContext(
                TECHNICAL_ID))
                .thenReturn(context);

        when(detailsRepository.findByTechnicalId(
                TECHNICAL_ID))
                .thenReturn(expected);

        CorrespondenceDetails actual =
                service.getDetails(
                        TECHNICAL_ID,
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("117"));

        assertEquals(expected, actual);
    }
}