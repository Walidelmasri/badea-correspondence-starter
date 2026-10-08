package org.badea.correspondence.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CorrespondenceStateTest {

    @Test
    void archivedIsTerminal() {
        assertTrue(
            CorrespondenceState.ARCHIVED.isTerminal()
        );
    }

    @Test
    void activeWorkflowStatesAreNotTerminal() {
        assertFalse(
            CorrespondenceState.DRAFT.isTerminal()
        );

        assertFalse(
            CorrespondenceState.WITH_PRESIDENT_OFFICE.isTerminal()
        );

        assertFalse(
            CorrespondenceState.WITH_PRESIDENT.isTerminal()
        );

        assertFalse(
            CorrespondenceState.WITH_DIRECTOR.isTerminal()
        );
    }
}