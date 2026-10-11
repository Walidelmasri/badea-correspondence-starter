package org.badea.correspondence.application;

import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

public interface CorrespondenceTransitionRepository {

    CorrespondenceAccessContext getAccessContext(
            String technicalId);

    void applyTransition(
            TransitionCorrespondenceCommand command,
            CorrespondenceState fromState,
            CorrespondenceState toState,
            CorrespondenceActorRole targetRole);
}