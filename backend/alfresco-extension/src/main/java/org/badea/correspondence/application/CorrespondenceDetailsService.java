package org.badea.correspondence.application;

import java.util.Objects;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

public final class CorrespondenceDetailsService {

    private final CorrespondenceTransitionRepository
            transitionRepository;

    private final CorrespondenceDetailsRepository
            detailsRepository;

    private final CorrespondenceAccessAuthorizer
            accessAuthorizer;

    public CorrespondenceDetailsService(
            CorrespondenceTransitionRepository transitionRepository,
            CorrespondenceDetailsRepository detailsRepository,
            CorrespondenceAccessAuthorizer accessAuthorizer) {

        this.transitionRepository =
                Objects.requireNonNull(
                        transitionRepository,
                        "transitionRepository must not be null");

        this.detailsRepository =
                Objects.requireNonNull(
                        detailsRepository,
                        "detailsRepository must not be null");

        this.accessAuthorizer =
                Objects.requireNonNull(
                        accessAuthorizer,
                        "accessAuthorizer must not be null");
    }

    public CorrespondenceDetails getDetails(
            String technicalId,
            CorrespondenceActorRole actorRole,
            DepartmentCode actorDepartmentCode) {

        Objects.requireNonNull(
                technicalId,
                "technicalId must not be null");

        Objects.requireNonNull(
                actorRole,
                "actorRole must not be null");

        CorrespondenceAccessContext accessContext =
                transitionRepository.getAccessContext(
                        technicalId);

        accessAuthorizer.authorizeRead(
                accessContext,
                actorRole,
                actorDepartmentCode);

        return detailsRepository.findByTechnicalId(
                technicalId);
    }
}