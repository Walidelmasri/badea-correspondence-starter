package org.badea.correspondence.application;

import java.util.Optional;

public interface OfficeCompletionRepository {
    Optional<OfficeCompletionState> find(String technicalId);
    OfficeCompletionState save(SaveOfficeCompletionCommand command);
}
