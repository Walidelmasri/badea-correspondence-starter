package org.badea.correspondence.application;

public interface CorrespondenceDetailsRepository {

    CorrespondenceDetails findByTechnicalId(
            String technicalId);
}