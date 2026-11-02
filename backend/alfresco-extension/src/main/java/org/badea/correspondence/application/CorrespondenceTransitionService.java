package org.badea.correspondence.application;

import java.util.Objects;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
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
    private final TarasolOrganizationReader organizationReader;
    private final CorrespondenceActorRoleResolver actorRoleResolver;

    // Backwards-compatible test/legacy constructor. Direct delivery is disabled
    // without both authorization dependencies.
    public CorrespondenceTransitionService(
            CorrespondenceWorkflowService workflowService,
            CorrespondenceAccessAuthorizer accessAuthorizer,
            CorrespondenceDepartmentService departmentService,
            CorrespondenceTransitionRepository repository,
            PresidentDocumentFinalizer presidentDocumentFinalizer) {

        this(workflowService, accessAuthorizer, departmentService, repository,
                presidentDocumentFinalizer, null, null);
    }

    public CorrespondenceTransitionService(
            CorrespondenceWorkflowService workflowService,
            CorrespondenceAccessAuthorizer accessAuthorizer,
            CorrespondenceDepartmentService departmentService,
            CorrespondenceTransitionRepository repository,
            PresidentDocumentFinalizer presidentDocumentFinalizer,
            TarasolOrganizationReader organizationReader,
            CorrespondenceActorRoleResolver actorRoleResolver) {
        this.organizationReader = organizationReader;
        this.actorRoleResolver = actorRoleResolver;
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
        } else if (accessContext.state() == CorrespondenceState.DRAFT
                && accessContext.createdByRole() != null) {
            // The executive draft path is creator-bound; never borrow Director's
            // department-scoped draft permission or any assigned-role permission.
            accessAuthorizer.authorizeExecutiveDraftSubmission(
                    accessContext, command.actorRole(), command.actorEmployeeId().value());
            CorrespondenceAction allowed = command.actorRole() == CorrespondenceActorRole.PRESIDENT_OFFICE
                    ? CorrespondenceAction.SEND_TO_PRESIDENT : CorrespondenceAction.SEND_TO_OFFICE;
            if (command.action() != allowed) {
                throw new org.badea.correspondence.workflow.InvalidCorrespondenceTransitionException(
                        accessContext.state(), command.actorRole(), command.action());
            }
        } else {
            accessAuthorizer.authorizeTransition(
                    accessContext, command.actorRole(), command.actorDepartmentCode());
        }

        if (command.action() == CorrespondenceAction.SEND_TO_PRESIDENT
                && accessContext.state() == CorrespondenceState.DRAFT
                && command.actorRole() == CorrespondenceActorRole.DIRECTOR) {
            authorizeDirectPresidentSubmission(command, accessContext);
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
        if (accessContext.state() == CorrespondenceState.WITH_PRESIDENT
                && command.actorRole() == CorrespondenceActorRole.PRESIDENT
                && command.action() == CorrespondenceAction.SEND_TO_OFFICE) {
            try {
                presidentDocumentFinalizer.finalizeSubmission(command);
                repository.applyTransition(command, accessContext.state(), nextState, targetRole);
            } catch (RuntimeException failure) {
                throw new IllegalStateException(
                        "President submission could not be committed", failure);
            }
        } else if (accessContext.state() == CorrespondenceState.DRAFT) {
            // The new reference reservation, document number, history, and state
            // are one REQUIRED transaction for Director AND executive senders.
            // The Web Script handles domain exceptions, so storage exceptions must
            // escape it to force a rollback of the entire submission.
            try {
                repository.applyTransition(command, accessContext.state(), nextState, targetRole);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Correspondence submission was not committed", failure);
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


    private void authorizeDirectPresidentSubmission(
            TransitionCorrespondenceCommand command,
            CorrespondenceAccessContext context) {
        // Fail closed: no special path for executive drafts, stale appointments,
        // President users, or a caller borrowing someone else's Director draft.
        if (command.actorRole() != CorrespondenceActorRole.DIRECTOR
                || context.createdByRole() != null
                || context.createdByEmployeeId() == null
                || !context.createdByEmployeeId().equals(command.actorEmployeeId().value())
                || !context.originDepartmentCode().equals(command.actorDepartmentCode())
                || new DepartmentCode("101").equals(command.actorDepartmentCode())
                || organizationReader == null || actorRoleResolver == null) {
            throw new CorrespondenceAuthorizationException("Direct President delivery is not authorized");
        }
        var roles = actorRoleResolver.resolveCurrentUserRoles();
        if (!roles.contains(CorrespondenceActorRole.DIRECTOR)
                || !roles.contains(CorrespondenceActorRole.PRESIDENT_OFFICE)
                || roles.contains(CorrespondenceActorRole.PRESIDENT)) {
            throw new CorrespondenceAuthorizationException("Both Director and Office authority are required");
        }
        var workspaces = organizationReader.findCurrentManagerWorkspaces(command.actorEmployeeId());
        if (workspaces.stream().filter(w -> w.code().equals(context.originDepartmentCode())).count() != 1
                || workspaces.stream().filter(w -> "101".equals(w.code().value())).count() != 1) {
            throw new CorrespondenceAuthorizationException("Both active managerial appointments are required");
        }
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