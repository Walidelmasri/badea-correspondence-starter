package org.badea.correspondence.application;

import java.util.Objects;

import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.badea.correspondence.workflow.CorrespondenceWorkflowService;

public final class CorrespondenceTransitionService {

    private final CorrespondenceWorkflowService workflowService;
    private final CorrespondenceTransitionRepository repository;

    public CorrespondenceTransitionService(
            CorrespondenceWorkflowService workflowService,
            CorrespondenceTransitionRepository repository) {

        this.workflowService =
                Objects.requireNonNull(
                        workflowService,
                        "workflowService must not be null");

        this.repository =
                Objects.requireNonNull(
                        repository,
                        "repository must not be null");
    }

    public CorrespondenceState transition(
            TransitionCorrespondenceCommand command) {

        Objects.requireNonNull(
                command,
                "command must not be null");

        CorrespondenceState currentState =
                repository.getCurrentState(
                        command.technicalId());

        CorrespondenceState nextState =
                workflowService.nextState(
                        currentState,
                        command.actorRole(),
                        command.action());

        validateTransitionData(
                command,
                nextState);

        CorrespondenceActorRole targetRole =
                targetRoleFor(
                        nextState);

        repository.applyTransition(
                command,
                currentState,
                nextState,
                targetRole);

        return nextState;
    }

    private static void validateTransitionData(
            TransitionCorrespondenceCommand command,
            CorrespondenceState nextState) {

        if (command.action()
                == CorrespondenceAction.SEND_TO_DIRECTOR) {

            if (command.targetDepartmentCode() == null) {
                throw new IllegalArgumentException(
                        "targetDepartmentCode is required when sending to a director");
            }

            return;
        }

        if (command.targetDepartmentCode() != null) {
            throw new IllegalArgumentException(
                    "targetDepartmentCode is only allowed when sending to a director");
        }

        if (command.action()
                == CorrespondenceAction.ADMINISTRATIVE_CLOSE
                && command.closureReason() == null) {

            throw new IllegalArgumentException(
                    "closureReason is required for administrative closure");
        }

        if (nextState == CorrespondenceState.WITH_DIRECTOR) {
            throw new IllegalStateException(
                    "A director assignment requires a target department");
        }
    }

    private static CorrespondenceActorRole targetRoleFor(
            CorrespondenceState nextState) {

        return switch (nextState) {
            case WITH_PRESIDENT_OFFICE ->
                    CorrespondenceActorRole.PRESIDENT_OFFICE;

            case WITH_PRESIDENT ->
                    CorrespondenceActorRole.PRESIDENT;

            case WITH_DIRECTOR ->
                    CorrespondenceActorRole.DIRECTOR;

            case DRAFT, ARCHIVED ->
                    null;
        };
    }
}