package org.badea.correspondence.application;

import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

public interface CorrespondenceTransitionRepository {

    CorrespondenceState getCurrentState(
            String technicalId);

    void applyTransition(
            TransitionCorrespondenceCommand command,
            CorrespondenceState fromState,
            CorrespondenceState toState,
            CorrespondenceActorRole targetRole);
}