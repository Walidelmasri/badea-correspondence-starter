package org.badea.correspondence.application;

import java.util.Objects;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

/** No client-supplied role or identity is trusted. The Web Script resolves both. */
public final class OfficeCycleContinuationService {
    private final OfficeCycleContinuationRepository repository;

    public OfficeCycleContinuationService(OfficeCycleContinuationRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public long continueCycle(TransitionCorrespondenceCommand command) {
        Objects.requireNonNull(command);
        boolean officeRole = command.actorRole() == CorrespondenceActorRole.PRESIDENT_OFFICE;
        boolean director = command.actorRole() == CorrespondenceActorRole.DIRECTOR
                && command.action() == CorrespondenceAction.DIRECTOR_CONTINUE_CORRESPONDENCE;
        if (officeRole && command.action() != CorrespondenceAction.CONTINUE_CORRESPONDENCE) {
            throw new IllegalArgumentException("Invalid Office continuation action");
        }
        if (!officeRole && !director) {
            throw new CorrespondenceAuthorizationException("Only Office or an assigned Director may continue");
        }
        if (command.targetDepartmentCode() != null || command.closureReason() != null
                || (director && (command.actorDepartmentCode() == null
                    || command.explanation() == null || command.explanation().length() > 2000))) {
            throw new IllegalArgumentException("Invalid continuation command");
        }
        return repository.continueToNextCycle(command);
    }
}
