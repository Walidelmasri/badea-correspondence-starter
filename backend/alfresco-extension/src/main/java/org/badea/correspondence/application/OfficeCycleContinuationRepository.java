package org.badea.correspondence.application;

/** Atomic Office continuation after departmental work is complete. */
public interface OfficeCycleContinuationRepository {
    long continueToNextCycle(TransitionCorrespondenceCommand command);
}
