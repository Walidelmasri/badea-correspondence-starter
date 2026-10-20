package org.badea.correspondence.application;

import java.util.Optional;

public interface OfficeCompletionRepository {
    Optional<OfficeCompletionState> find(String technicalId);
    /** Monotonic revision cursor, even when the current cycle has no saved Office draft. */
    default long headRevision(String technicalId) {
        return find(technicalId).map(OfficeCompletionState::revision).orElse(0L);
    }
    OfficeCompletionState save(SaveOfficeCompletionCommand command);
}
