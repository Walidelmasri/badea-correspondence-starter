package org.badea.correspondence.application;

import java.util.Objects;

import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.badea.correspondence.workflow.CorrespondenceWorkflowService;

public final class CorrespondenceTransitionService {

    private final CorrespondenceWorkflowService workflowService;
    private final CorrespondenceAccessAuthorizer accessAuthorizer;
    private final CorrespondenceDepartmentService departmentService;
    private final CorrespondenceTransitionRepository repository;
    private final PresidentDocumentFinalizer presidentDocumentFinalizer;

    public CorrespondenceTransitionService(
            CorrespondenceWorkflowService workflowService,
            CorrespondenceAccessAuthorizer accessAuthorizer,
            CorrespondenceDepartmentService departmentService,
            CorrespondenceTransitionRepository repository,
            PresidentDocumentFinalizer presidentDocumentFinalizer) {

        this.workflowService =
                Objects.requireNonNull(
                        workflowService,
                        "workflowService must not be null");

        this.accessAuthorizer =
                Objects.requireNonNull(
                        accessAuthorizer,
                        "accessAuthorizer must not be null");

        this.departmentService =
                Objects.requireNonNull(
                        departmentService,
                        "departmentService must not be null");

        this.repository =
                Objects.requireNonNull(
                        repository,
                        "repository must not be null");

        this.presidentDocumentFinalizer = Objects.requireNonNull(
                presidentDocumentFinalizer, "presidentDocumentFinalizer must not be null");
    }

    public CorrespondenceState transition(
            TransitionCorrespondenceCommand command) {

        Objects.requireNonNull(
                command,
                "command must not be null");

        CorrespondenceAccessContext accessContext =
                repository.getAccessContext(
                        command.technicalId());

        // Distribution is a separate transactional operation. The legacy transition API
        // may not fabricate a fan-out without persisted assignments/action sheet.
        if (command.action() == CorrespondenceAction.DISTRIBUTE_TO_DEPARTMENTS) {
            throw new IllegalArgumentException("Use the Office distribution endpoint");
        }
        boolean distributedClosure = !accessContext.assignments().isEmpty()
                && command.action() == CorrespondenceAction.ADMINISTRATIVE_CLOSE
                && command.actorRole() == CorrespondenceActorRole.PRESIDENT_OFFICE;
        if (!accessContext.assignments().isEmpty() && !distributedClosure) {
            throw new org.badea.correspondence.workflow.InvalidCorrespondenceTransitionException(
                    accessContext.state(), command.actorRole(), command.action());
        }
        if (distributedClosure) {
            if (accessContext.state() != CorrespondenceState.WITH_DIRECTOR
                    || accessContext.assignedRole() != CorrespondenceActorRole.DIRECTOR
                    || accessContext.assignments().stream().anyMatch(assignment ->
                            assignment.recipientType() == DepartmentRecipientType.TO
                            && assignment.status() != DepartmentAssignmentStatus.COMPLETED)) {
                throw new org.badea.correspondence.workflow.InvalidCorrespondenceTransitionException(
                        accessContext.state(), command.actorRole(), command.action());
            }
        } else {
            accessAuthorizer.authorizeTransition(
                    accessContext, command.actorRole(), command.actorDepartmentCode());
        }

        CorrespondenceState nextState =
                workflowService.nextState(
                        accessContext.state(),
                        command.actorRole(),
                        command.action());

        validateTransitionData(
                command,
                nextState);

        CorrespondenceActorRole targetRole =
                targetRoleFor(
                        nextState);

        // A Web Script that catches repository exceptions may accidentally
        // commit partially written content. Force *any* President handoff
        // failure to escape the Web Script so Alfresco rolls back its REQUIRED
        // transaction (including binary content, history and assignment).
        if (command.actorRole() == CorrespondenceActorRole.PRESIDENT
                && command.action() == CorrespondenceAction.SEND_TO_OFFICE) {
            try {
                presidentDocumentFinalizer.finalizeSubmission(command);
                repository.applyTransition(command, accessContext.state(), nextState, targetRole);
            } catch (RuntimeException failure) {
                throw new IllegalStateException(
                        "President submission could not be committed", failure);
            }
        } else if (distributedClosure) {
            // A Web Script can catch domain failures; force storage failures to
            // escape the REQUIRED transaction rather than commit a partial archive.
            try {
                repository.applyTransition(command, accessContext.state(), nextState, targetRole);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Distributed administrative closure was not committed", failure);
            }
        } else {
            repository.applyTransition(command, accessContext.state(), nextState, targetRole);
        }

        return nextState;
    }

    private void validateTransitionData(
            TransitionCorrespondenceCommand command,
            CorrespondenceState nextState) {

        if (command.action()
                == CorrespondenceAction.SEND_TO_DIRECTOR) {

            if (command.targetDepartmentCode() == null) {
                throw new IllegalArgumentException(
                        "targetDepartmentCode is required when sending to a director");
            }

            departmentService.requireDepartment(
                    command.targetDepartmentCode());

        } else if (command.targetDepartmentCode() != null) {

            throw new IllegalArgumentException(
                    "targetDepartmentCode is only allowed when sending to a director");
        }

        if (command.action()
                == CorrespondenceAction.ADMINISTRATIVE_CLOSE) {

            if (command.closureReason() == null) {
                throw new IllegalArgumentException(
                        "closureReason is required for administrative closure");
            }

        } else if (command.closureReason() != null) {

            throw new IllegalArgumentException(
                    "closureReason is only allowed for administrative closure");
        }

        if (nextState == CorrespondenceState.WITH_DIRECTOR
                && command.targetDepartmentCode() == null) {

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