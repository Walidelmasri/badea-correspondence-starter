package org.badea.correspondence.application;

import org.badea.correspondence.application.TransitionCorrespondenceCommand;

/** Creates immutable review artifacts before a President's handoff commits. */
public interface PresidentDocumentFinalizer {
    void finalizeSubmission(TransitionCorrespondenceCommand command);
}
