package org.badea.correspondence.application;

/**
 * Immutable workflow evidence that the latest action was a President return
 * to the President's Office.  State alone cannot distinguish Office visits.
 */
public interface PresidentReturnEvidence {
    boolean latestActionIsPresidentReturn(String technicalId);
}
