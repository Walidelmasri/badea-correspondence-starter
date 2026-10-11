package org.badea.correspondence.application;

import java.util.Optional;

public interface PresidentReviewStateRepository {

    Optional<PresidentReviewState> find(
            String technicalId);

    PresidentReviewState save(
            SavePresidentReviewStateCommand command);
}