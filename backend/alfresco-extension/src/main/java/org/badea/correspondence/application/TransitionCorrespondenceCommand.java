package org.badea.correspondence.application;

import java.util.Objects;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

public record TransitionCorrespondenceCommand(
        String technicalId,
        CorrespondenceActorRole actorRole,
        CorrespondenceAction action,
        EmployeeId actorEmployeeId,
        DepartmentCode actorDepartmentCode,
        String actorNameEnglish,
        String actorNameArabic,
        DepartmentCode targetDepartmentCode,
        String explanation,
        String closureReason) {

    public TransitionCorrespondenceCommand {

        technicalId =
                requireText(
                        technicalId,
                        "technicalId");

        Objects.requireNonNull(
                actorRole,
                "actorRole must not be null");

        Objects.requireNonNull(
                action,
                "action must not be null");

        Objects.requireNonNull(
                actorEmployeeId,
                "actorEmployeeId must not be null");

        actorNameEnglish =
                requireText(
                        actorNameEnglish,
                        "actorNameEnglish");

        actorNameArabic =
                normalizeOptionalText(
                        actorNameArabic);

        explanation =
                normalizeOptionalText(
                        explanation);

        closureReason =
                normalizeOptionalText(
                        closureReason);
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

    private static String normalizeOptionalText(
            String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}