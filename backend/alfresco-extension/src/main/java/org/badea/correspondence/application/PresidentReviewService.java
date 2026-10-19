package org.badea.correspondence.application;

import java.util.Objects;
import java.util.Optional;

import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

public final class PresidentReviewService {

    private final CorrespondenceTransitionRepository correspondenceRepository;
    private final PresidentReviewStateRepository reviewStateRepository;
    private final PresidentReturnEvidence returnEvidence;

    public PresidentReviewService(
            CorrespondenceTransitionRepository correspondenceRepository,
            PresidentReviewStateRepository reviewStateRepository,
            PresidentReturnEvidence returnEvidence) {

        this.correspondenceRepository =
                Objects.requireNonNull(
                        correspondenceRepository,
                        "correspondenceRepository must not be null");

        this.reviewStateRepository =
                Objects.requireNonNull(
                        reviewStateRepository,
                        "reviewStateRepository must not be null");

        this.returnEvidence = Objects.requireNonNull(
                returnEvidence,
                "returnEvidence must not be null");
    }

    public Optional<PresidentReviewState> load(
            String technicalId,
            CorrespondenceActorRole actorRole) {

        authorizeLoad(
                technicalId,
                actorRole);

        return reviewStateRepository.find(
                technicalId);
    }

    public PresidentReviewState save(
            SavePresidentReviewStateCommand command,
            CorrespondenceActorRole actorRole) {

        Objects.requireNonNull(
                command,
                "command must not be null");

        authorizePresidentSave(
                command.technicalId(),
                actorRole);

        return reviewStateRepository.save(
                command);
    }

    private void authorizeLoad(
            String technicalId,
            CorrespondenceActorRole actorRole) {

        Objects.requireNonNull(technicalId, "technicalId must not be null");
        Objects.requireNonNull(actorRole, "actorRole must not be null");

        CorrespondenceAccessContext context =
                correspondenceRepository.getAccessContext(technicalId);

        if (presidentCurrentlyAssigned(context, actorRole)) {
            return;
        }

        if (actorRole == CorrespondenceActorRole.PRESIDENT_OFFICE
                && context.state() == CorrespondenceState.WITH_PRESIDENT_OFFICE
                && context.assignedRole() == CorrespondenceActorRole.PRESIDENT_OFFICE
                && returnEvidence.latestActionIsPresidentReturn(technicalId)) {
            // Office may inspect the immutable President submission.
            // Office completion must be recorded in a separate editable layer.
            return;
        }

        throw new CorrespondenceAuthorizationException(
                "The review is not assigned to this actor for reading.");
    }

    private void authorizePresidentSave(
            String technicalId,
            CorrespondenceActorRole actorRole) {

        Objects.requireNonNull(
                technicalId,
                "technicalId must not be null");

        Objects.requireNonNull(
                actorRole,
                "actorRole must not be null");

        CorrespondenceAccessContext context =
                correspondenceRepository.getAccessContext(
                        technicalId);

        if (!presidentCurrentlyAssigned(context, actorRole)) {
            throw new CorrespondenceAuthorizationException(
                    "Only the assigned President may save this review.");
        }
    }

    private static boolean presidentCurrentlyAssigned(
            CorrespondenceAccessContext context,
            CorrespondenceActorRole actorRole) {
        return actorRole == CorrespondenceActorRole.PRESIDENT
                && context.state() == CorrespondenceState.WITH_PRESIDENT
                && context.assignedRole() == CorrespondenceActorRole.PRESIDENT;
    }
}