package org.badea.correspondence.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.badea.correspondence.workflow.CorrespondenceState;

public record CorrespondenceDetails(
        String technicalId,
        CorrespondenceState state,
        String subject,
        String bodyText,
        String documentNumber,
        LocalDate documentDate,
        String sourceText,
        String originDepartmentCode,
        String createdByNameEnglish,
        String createdByNameArabic,
        List<CorrespondenceDocument> documents) {

    public CorrespondenceDetails {

        Objects.requireNonNull(
                technicalId,
                "technicalId must not be null");

        Objects.requireNonNull(
                state,
                "state must not be null");

        Objects.requireNonNull(
                subject,
                "subject must not be null");

        Objects.requireNonNull(
                originDepartmentCode,
                "originDepartmentCode must not be null");

        Objects.requireNonNull(
                createdByNameEnglish,
                "createdByNameEnglish must not be null");

        Objects.requireNonNull(
                documents,
                "documents must not be null");

        documents = List.copyOf(
                documents);
    }
}