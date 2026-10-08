package org.badea.correspondence.application;

import java.time.LocalDate;
import java.util.Objects;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;

public record CreateCorrespondenceCommand(
        DepartmentCode originDepartmentCode,
        String subject,
        String bodyText,
        String documentNumber,
        LocalDate documentDate,
        String sourceText,
        EmployeeId createdByEmployeeId,
        String createdByNameEnglish,
        String createdByNameArabic) {

    public CreateCorrespondenceCommand {
        Objects.requireNonNull(
                originDepartmentCode,
                "originDepartmentCode must not be null");

        Objects.requireNonNull(
                createdByEmployeeId,
                "createdByEmployeeId must not be null");

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