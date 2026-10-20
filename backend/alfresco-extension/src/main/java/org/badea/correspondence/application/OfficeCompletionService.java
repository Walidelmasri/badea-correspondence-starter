package org.badea.correspondence.application;

import java.util.Objects;
import java.util.Optional;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

/** Office may supplement, but never modify, the President-authored review. */
public final class OfficeCompletionService {
    private final CorrespondenceTransitionRepository correspondence;
    private final PresidentReturnEvidence returnEvidence;
    private final PresidentReviewStateRepository presidentReview;
    private final OfficeCompletionRepository completion;

    public OfficeCompletionService(CorrespondenceTransitionRepository correspondence,
            PresidentReturnEvidence returnEvidence,
            PresidentReviewStateRepository presidentReview,
            OfficeCompletionRepository completion) {
        this.correspondence = Objects.requireNonNull(correspondence);
        this.returnEvidence = Objects.requireNonNull(returnEvidence);
        this.presidentReview = Objects.requireNonNull(presidentReview);
        this.completion = Objects.requireNonNull(completion);
    }

    public long presidentRevision(String id, CorrespondenceActorRole role) {
        authorize(id,role);
        return presidentReview.find(id).map(PresidentReviewState::revision).orElse(0L);
    }

    public long headRevision(String id, CorrespondenceActorRole role) {
        authorize(id, role);
        return completion.headRevision(id);
    }

    public Optional<OfficeCompletionState> load(String id, CorrespondenceActorRole role) {
        authorize(id,role);
        return completion.find(id);
    }

    public OfficeCompletionState save(SaveOfficeCompletionCommand command,
            CorrespondenceActorRole role) {
        Objects.requireNonNull(command);
        authorize(command.technicalId(),role);
        long current = presidentReview.find(command.technicalId())
                .map(PresidentReviewState::revision).orElse(0L);
        if (command.expectedPresidentReviewRevision() != current) {
            throw new OfficeCompletionStateConflictException(
                    "President review revision changed. Reload before saving Office edits.");
        }
        String clean = OfficeCompletionJsonValidator.validate(command.json());
        SaveOfficeCompletionCommand verified = new SaveOfficeCompletionCommand(
                command.technicalId(),command.expectedRevision(),current,
                command.actorEmployeeId(), command.actorNameEnglish(),
                command.actorNameArabic(), clean);
        return completion.save(verified);
    }

    private void authorize(String id, CorrespondenceActorRole role) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(role);
        CorrespondenceAccessContext context = correspondence.getAccessContext(id);
        if (role != CorrespondenceActorRole.PRESIDENT_OFFICE
                || context.state() != CorrespondenceState.WITH_PRESIDENT_OFFICE
                || context.assignedRole() != CorrespondenceActorRole.PRESIDENT_OFFICE
                || !returnEvidence.latestActionIsPresidentReturn(id)) {
            throw new CorrespondenceAuthorizationException(
                    "Only the Office handling a President-returned correspondence may complete it.");
        }
    }
}
