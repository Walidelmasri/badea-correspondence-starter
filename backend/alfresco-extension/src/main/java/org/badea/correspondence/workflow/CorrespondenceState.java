package org.badea.correspondence.workflow;

/**
 * Lifecycle state of a correspondence.
 *
 * <p>The state represents where the correspondence currently sits in the
 * Presidency workflow. ARCHIVED is terminal. The reason for archival is
 * preserved separately in the immutable action history.</p>
 */
public enum CorrespondenceState {

    DRAFT(false),

    WITH_PRESIDENT_OFFICE(false),

    WITH_PRESIDENT(false),

    WITH_DIRECTOR(false),

    ARCHIVED(true);

    private final boolean terminal;

    CorrespondenceState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}