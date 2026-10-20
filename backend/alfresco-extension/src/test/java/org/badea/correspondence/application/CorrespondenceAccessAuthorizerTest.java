package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorrespondenceAccessAuthorizerTest {

    private CorrespondenceAccessAuthorizer authorizer;

    @BeforeEach
    void setUp() {

        authorizer =
                new CorrespondenceAccessAuthorizer();
    }

    @Test
    void directorCanReadSentCorrespondenceButCannotTransitionIt() {
        CorrespondenceAccessContext sent = new CorrespondenceAccessContext(
                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                new DepartmentCode("117"),
                CorrespondenceActorRole.PRESIDENT_OFFICE, null);
        assertDoesNotThrow(() -> authorizer.authorizeRead(
                sent, CorrespondenceActorRole.DIRECTOR, new DepartmentCode("117")));
        assertThrows(CorrespondenceAuthorizationException.class,
                () -> authorizer.authorizeTransition(
                        sent, CorrespondenceActorRole.DIRECTOR, new DepartmentCode("117")));
    }

    @Test
    void otherDirectorCannotReadSentCorrespondence() {
        CorrespondenceAccessContext sent = new CorrespondenceAccessContext(
                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                new DepartmentCode("118"),
                CorrespondenceActorRole.PRESIDENT_OFFICE, null);
        assertThrows(CorrespondenceAuthorizationException.class,
                () -> authorizer.authorizeRead(
                        sent, CorrespondenceActorRole.DIRECTOR, new DepartmentCode("117")));
    }

    @Test
    void allowsDirectorToActOnDraftFromOwnDepartment() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.DRAFT,
                        new DepartmentCode("117"),
                        null,
                        null);

        assertDoesNotThrow(
                () -> authorizer.authorizeTransition(
                        correspondence,
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("117")));
    }

    @Test
    void allowsDirectorToReadDraftFromOwnDepartment() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.DRAFT,
                        new DepartmentCode("117"),
                        null,
                        null);

        assertDoesNotThrow(
                () -> authorizer.authorizeRead(
                        correspondence,
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("117")));
    }

    @Test
    void rejectsDirectorActingOnAnotherDepartmentsDraft() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.DRAFT,
                        new DepartmentCode("111"),
                        null,
                        null);

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> authorizer.authorizeTransition(
                        correspondence,
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("117")));
    }

    @Test
    void rejectsDirectorReadingAnotherDepartmentsDraft() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.DRAFT,
                        new DepartmentCode("111"),
                        null,
                        null);

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> authorizer.authorizeRead(
                        correspondence,
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("117")));
    }

    @Test
    void allowsDirectorWhenCorrespondenceIsAssignedToOwnDepartment() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.WITH_DIRECTOR,
                        new DepartmentCode("111"),
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("117"));

        assertDoesNotThrow(
                () -> authorizer.authorizeTransition(
                        correspondence,
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("117")));
    }

    @Test
    void rejectsDirectorWhenCorrespondenceIsAssignedToAnotherDepartment() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.WITH_DIRECTOR,
                        new DepartmentCode("111"),
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("118"));

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> authorizer.authorizeTransition(
                        correspondence,
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("117")));
    }

    @Test
    void allowsPresidentOfficeWhenAssignedToPresidentOffice() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        new DepartmentCode("117"),
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        null);

        assertDoesNotThrow(
                () -> authorizer.authorizeTransition(
                        correspondence,
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        new DepartmentCode("101")));
    }

    @Test
    void rejectsPresidentOfficeFromAnotherDepartmentsDraft() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.DRAFT,
                        new DepartmentCode("117"),
                        null,
                        null);

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> authorizer.authorizeTransition(
                        correspondence,
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        new DepartmentCode("101")));
    }

    @Test
    void rejectsPresidentOfficeFromUnsubmittedDraftEvenIfDepartmentMatches() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.DRAFT,
                        new DepartmentCode("117"),
                        null,
                        null);

        DepartmentCode officeDepartment =
                new DepartmentCode("117");

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> authorizer.authorizeTransition(
                        correspondence,
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        officeDepartment));

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> authorizer.authorizeRead(
                        correspondence,
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        officeDepartment));
    }

    @Test
    void allowsPresidentWhenAssignedToPresident() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.WITH_PRESIDENT,
                        new DepartmentCode("117"),
                        CorrespondenceActorRole.PRESIDENT,
                        null);

        assertDoesNotThrow(
                () -> authorizer.authorizeTransition(
                        correspondence,
                        CorrespondenceActorRole.PRESIDENT,
                        null));
    }

    @Test
    void rejectsPresidentWhenCorrespondenceIsNotWithPresident() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        new DepartmentCode("117"),
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        null);

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> authorizer.authorizeTransition(
                        correspondence,
                        CorrespondenceActorRole.PRESIDENT,
                        null));
    }

    @Test
    void rejectsPresidentReadingDirectorDraft() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.DRAFT,
                        new DepartmentCode("117"),
                        null,
                        null);

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> authorizer.authorizeRead(
                        correspondence,
                        CorrespondenceActorRole.PRESIDENT,
                        null));
    }

    @Test
    void rejectsDirectorWithoutOracleDepartment() {

        CorrespondenceAccessContext correspondence =
                new CorrespondenceAccessContext(
                        CorrespondenceState.WITH_DIRECTOR,
                        new DepartmentCode("111"),
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("117"));

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> authorizer.authorizeTransition(
                        correspondence,
                        CorrespondenceActorRole.DIRECTOR,
                        null));
    }
    @Test
    void historicalOfficeReaderCannotModifyCorrespondence() {
        CorrespondenceParticipation priorReceipt = new CorrespondenceParticipation(
                "10001", CorrespondenceActorRole.DIRECTOR,
                CorrespondenceActorRole.PRESIDENT_OFFICE, null,
                java.time.Instant.parse("2026-10-20T08:00:00Z"));
        CorrespondenceAccessContext context = new CorrespondenceAccessContext(
                CorrespondenceState.WITH_PRESIDENT, new DepartmentCode("117"),
                CorrespondenceActorRole.PRESIDENT, null, java.util.List.of(priorReceipt));
        assertDoesNotThrow(() -> authorizer.authorizeRead(context,
                CorrespondenceActorRole.PRESIDENT_OFFICE, null, "10009"));
        assertThrows(CorrespondenceAuthorizationException.class, () ->
                authorizer.authorizeTransition(context, CorrespondenceActorRole.PRESIDENT_OFFICE, null));
        assertThrows(CorrespondenceAuthorizationException.class, () ->
                authorizer.authorizeRead(context, CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("118"), "10009"));
    }

}