package org.badea.correspondence.application;

import java.util.Optional;
import org.badea.correspondence.directory.EmployeeId;

/** Append-only document persistence with correspondence-local idempotency. */
public interface PresidentGeneratedDocumentStore {
    Optional<CorrespondenceGeneratedDocument> find(
            String technicalId, long revision, String artifactType, String sourceNodeId);

    void save(String technicalId, long revision, String artifactType,
            String sourceNodeId, String filename, byte[] pdf,
            EmployeeId actorEmployeeId);
}
