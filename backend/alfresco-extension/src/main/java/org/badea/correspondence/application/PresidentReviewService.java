package org.badea.correspondence.application;

import java.util.Objects;
import java.util.Optional;

import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

public final class PresidentReviewService {

    private final CorrespondenceTransitionRepository correspondenceRepository;
    private final PresidentReviewStateRepository reviewStateRepository;

    public PresidentReviewService(
            CorrespondenceTransitionRepository correspondenceRepository,
            PresidentReviewStateRepository reviewStateRepository) {

        this.correspondenceRepository =
                Objects.requireNonNull(
                        correspondenceRepository,
                        "correspondenceRepository must not be null");

        this.reviewStateRepository =
                Objects.requireNonNull(
                        reviewStateRepository,
                        "reviewStateRepository must not be null");
    }

    public Optional<PresidentReviewState> load(
            String technicalId,
            CorrespondenceActorRole actorRole) {

        authorize(
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

        authorize(
                command.technicalId(),
                actorRole);

        return reviewStateRepository.save(
                command);
    }

    private void authorize(
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

        if (actorRole
                != CorrespondenceActorRole.PRESIDENT) {

            throw new CorrespondenceAuthorizationException(
                    "Only the President may access President review state.");
        }

        if (context.state()
                != CorrespondenceState.WITH_PRESIDENT) {

            throw new CorrespondenceAuthorizationException(
                    "Correspondence is not currently with the President.");
        }

        if (context.assignedRole()
                != CorrespondenceActorRole.PRESIDENT) {

            throw new CorrespondenceAuthorizationException(
                    "Correspondence is not assigned to the President.");
        }
    }
}