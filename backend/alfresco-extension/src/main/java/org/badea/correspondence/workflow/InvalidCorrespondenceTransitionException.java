package org.badea.correspondence.workflow;

/**
 * Raised when an actor attempts an action that is not valid for the
 * correspondence's current workflow state.
 */
public final class InvalidCorrespondenceTransitionException
    extends RuntimeException {

    public InvalidCorrespondenceTransitionException(
        CorrespondenceState state,
        CorrespondenceActorRole actorRole,
        CorrespondenceAction action
    ) {
        super(
            "Action "
                + action
                + " is not allowed for "
                + actorRole
                + " while correspondence is in state "
                + state
                + "."
        );
    }
}