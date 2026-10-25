package org.badea.correspondence.application;

import java.time.LocalDate;
import java.util.Objects;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

public record CreateCorrespondenceCommand(
        DepartmentCode originDepartmentCode,
        String subject,
        String bodyText,
        String documentNumber,
        LocalDate documentDate,
        String sourceText,
        EmployeeId createdByEmployeeId,
        String createdByNameEnglish,
        String createdByNameArabic,
        CorrespondenceActorRole createdByRole,
        String executiveComposeJson) {

    /** Preserve the existing Director creation contract unchanged. */
    public CreateCorrespondenceCommand(
            DepartmentCode originDepartmentCode,
            String subject,
            String bodyText,
            String documentNumber,
            LocalDate documentDate,
            String sourceText,
            EmployeeId createdByEmployeeId,
            String createdByNameEnglish,
            String createdByNameArabic) {
        this(originDepartmentCode, subject, bodyText, documentNumber,
                documentDate, sourceText, createdByEmployeeId,
                createdByNameEnglish, createdByNameArabic,
                CorrespondenceActorRole.DIRECTOR, null);
    }

    public CreateCorrespondenceCommand {
        Objects.requireNonNull(
                originDepartmentCode,
                "originDepartmentCode must not be null");

        Objects.requireNonNull(
                createdByEmployeeId,
                "createdByEmployeeId must not be null");

        Objects.requireNonNull(createdByRole, "createdByRole must not be null");
        if (createdByRole == CorrespondenceActorRole.DIRECTOR
                && executiveComposeJson != null) {
            throw new IllegalArgumentException(
                    "Director drafts must not contain executive Compose metadata");
        }
        if (createdByRole != CorrespondenceActorRole.DIRECTOR
                && (executiveComposeJson == null || executiveComposeJson.isBlank())) {
            throw new IllegalArgumentException(
                    "Executive drafts require their Compose metadata");
        }

        subject = requireText(
                subject,
                "subject");

        createdByNameEnglish = requireText(
                createdByNameEnglish,
                "createdByNameEnglish");
    }

    private static String requireText(
            String value,
            String fieldName) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank");
        }

        return value.trim();
    }
}