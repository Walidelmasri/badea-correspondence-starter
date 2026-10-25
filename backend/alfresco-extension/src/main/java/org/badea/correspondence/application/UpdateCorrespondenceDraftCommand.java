package org.badea.correspondence.application;

import java.time.LocalDate;
import java.util.Objects;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

public record UpdateCorrespondenceDraftCommand(
        String technicalId,
        CorrespondenceActorRole actorRole,
        DepartmentCode actorDepartmentCode,
        EmployeeId actorEmployeeId,
        String subject,
        String bodyText,
        String documentNumber,
        LocalDate documentDate,
        String sourceText,
        String executiveComposeJson) {

    /** Preserve every existing Director/editor call and its update contract. */
    public UpdateCorrespondenceDraftCommand(
            String technicalId,
            CorrespondenceActorRole actorRole,
            DepartmentCode actorDepartmentCode,
            EmployeeId actorEmployeeId,
            String subject,
            String bodyText,
            String documentNumber,
            LocalDate documentDate,
            String sourceText) {
        this(technicalId, actorRole, actorDepartmentCode, actorEmployeeId,
                subject, bodyText, documentNumber, documentDate, sourceText, null);
    }


    public UpdateCorrespondenceDraftCommand {
        technicalId = requireText(technicalId, "technicalId");

        Objects.requireNonNull(actorRole, "actorRole");
        Objects.requireNonNull(actorDepartmentCode, "actorDepartmentCode");
        Objects.requireNonNull(actorEmployeeId, "actorEmployeeId");

        subject = requireText(subject, "subject");

        bodyText = optionalText(bodyText);
        documentNumber = optionalText(documentNumber);
        sourceText = optionalText(sourceText);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank.");
        }

        return value.trim();
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}