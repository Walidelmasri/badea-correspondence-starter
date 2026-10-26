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
        List<CorrespondenceDocument> documents,
        List<CorrespondenceGeneratedDocument> generatedDocuments,
        String executiveComposeJson,
        long currentWorkflowCycle) {

    public CorrespondenceDetails {

        if (currentWorkflowCycle < 1) {
            throw new IllegalArgumentException("Current workflow round must be positive.");
        }

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

        generatedDocuments = List.copyOf(
                Objects.requireNonNull(generatedDocuments));
    }
    /** Preserve compatibility for services that do not need the active round. */
    public CorrespondenceDetails(String technicalId, CorrespondenceState state,
            String subject, String bodyText, String documentNumber, LocalDate documentDate,
            String sourceText, String originDepartmentCode, String createdByNameEnglish,
            String createdByNameArabic, List<CorrespondenceDocument> documents,
            List<CorrespondenceGeneratedDocument> generatedDocuments, String executiveComposeJson) {
        this(technicalId, state, subject, bodyText, documentNumber, documentDate,
                sourceText, originDepartmentCode, createdByNameEnglish,
                createdByNameArabic, documents, generatedDocuments, executiveComposeJson, 1L);
    }

    // Preserve the existing constructor contract for callers that do not
    // consume the executive form's structured, persisted metadata.
    public CorrespondenceDetails(String technicalId, CorrespondenceState state,
            String subject, String bodyText, String documentNumber, LocalDate documentDate,
            String sourceText, String originDepartmentCode, String createdByNameEnglish,
            String createdByNameArabic, List<CorrespondenceDocument> documents,
            List<CorrespondenceGeneratedDocument> generatedDocuments) {
        this(technicalId, state, subject, bodyText, documentNumber, documentDate,
                sourceText, originDepartmentCode, createdByNameEnglish,
                createdByNameArabic, documents, generatedDocuments, null);
    }

    // Existing callers that deal only with source attachments remain compatible.
    public CorrespondenceDetails(
            String technicalId, CorrespondenceState state, String subject,
            String bodyText, String documentNumber, LocalDate documentDate,
            String sourceText, String originDepartmentCode,
            String createdByNameEnglish, String createdByNameArabic,
            List<CorrespondenceDocument> documents) {
        this(technicalId, state, subject, bodyText, documentNumber,
                documentDate, sourceText, originDepartmentCode,
                createdByNameEnglish, createdByNameArabic, documents, List.of(), null);
    }
}
