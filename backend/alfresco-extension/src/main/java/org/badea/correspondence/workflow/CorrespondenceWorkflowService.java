package org.badea.correspondence.workflow;

import java.util.Objects;

/**
 * Defines the allowed Presidency correspondence workflow transitions.
 *
 * <p>This service contains workflow rules only. Authentication,
 * Alfresco group membership, persistence and action-sheet validation
 * are handled separately.</p>
 */
public final class CorrespondenceWorkflowService {

    public CorrespondenceState nextState(
        CorrespondenceState currentState,
        CorrespondenceActorRole actorRole,
        CorrespondenceAction action
    ) {
        Objects.requireNonNull(
            currentState,
            "Current state must not be null."
        );

        Objects.requireNonNull(
            actorRole,
            "Actor role must not be null."
        );

        Objects.requireNonNull(
            action,
            "Action must not be null."
        );

        CorrespondenceState nextState =
            resolveTransition(
                currentState,
                actorRole,
                action
            );

        if (nextState == null) {
            throw new InvalidCorrespondenceTransitionException(
                currentState,
                actorRole,
                action
            );
        }

        return nextState;
    }

    private CorrespondenceState resolveTransition(
        CorrespondenceState currentState,
        CorrespondenceActorRole actorRole,
        CorrespondenceAction action
    ) {
        if (currentState == CorrespondenceState.ARCHIVED) {
            return null;
        }

        return switch (currentState) {
            case DRAFT ->
                resolveDraftTransition(
                    actorRole,
                    action
                );

            case WITH_PRESIDENT_OFFICE ->
                resolvePresidentOfficeTransition(
                    actorRole,
                    action
                );

            case WITH_PRESIDENT ->
                resolvePresidentTransition(
                    actorRole,
                    action
                );

            case WITH_DIRECTOR ->
                resolveDirectorTransition(
                    actorRole,
                    action
                );

            case ARCHIVED -> null;
        };
    }

    private CorrespondenceState resolveDraftTransition(
        CorrespondenceActorRole actorRole,
        CorrespondenceAction action
    ) {
        if (
            actorRole == CorrespondenceActorRole.DIRECTOR
                && action == CorrespondenceAction.SEND_TO_OFFICE
        ) {
            return CorrespondenceState.WITH_PRESIDENT_OFFICE;
        }

        if (
            actorRole
                == CorrespondenceActorRole.PRESIDENT_OFFICE
        ) {
            if (
                action
                    == CorrespondenceAction.SEND_TO_PRESIDENT
            ) {
                return CorrespondenceState.WITH_PRESIDENT;
            }

            if (
                action
                    == CorrespondenceAction.SEND_TO_DIRECTOR
            ) {
                return CorrespondenceState.WITH_DIRECTOR;
            }
        }

        return null;
    }

    private CorrespondenceState resolvePresidentOfficeTransition(
        CorrespondenceActorRole actorRole,
        CorrespondenceAction action
    ) {
        if (
            actorRole
                != CorrespondenceActorRole.PRESIDENT_OFFICE
        ) {
            return null;
        }

        return switch (action) {
            case SEND_TO_PRESIDENT ->
                CorrespondenceState.WITH_PRESIDENT;

            case SEND_TO_DIRECTOR ->
                CorrespondenceState.WITH_DIRECTOR;

            case ADMINISTRATIVE_CLOSE ->
                CorrespondenceState.ARCHIVED;

            default -> null;
        };
    }

    private CorrespondenceState resolvePresidentTransition(
        CorrespondenceActorRole actorRole,
        CorrespondenceAction action
    ) {
        if (
            actorRole != CorrespondenceActorRole.PRESIDENT
        ) {
            return null;
        }

        return switch (action) {
            case SEND_TO_OFFICE ->
                CorrespondenceState.WITH_PRESIDENT_OFFICE;

            case MARK_DONE ->
                CorrespondenceState.ARCHIVED;

            default -> null;
        };
    }

    private CorrespondenceState resolveDirectorTransition(
        CorrespondenceActorRole actorRole,
        CorrespondenceAction action
    ) {
        if (
            actorRole != CorrespondenceActorRole.DIRECTOR
        ) {
            return null;
        }

        return switch (action) {
            case SEND_TO_OFFICE ->
                CorrespondenceState.WITH_PRESIDENT_OFFICE;

            case MARK_DONE ->
                CorrespondenceState.ARCHIVED;

            default -> null;
        };
    }
}