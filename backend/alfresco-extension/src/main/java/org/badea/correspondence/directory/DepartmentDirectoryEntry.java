package org.badea.correspondence.directory;

import java.util.Objects;

/**
 * Current BADEA department information resolved from the employee directory.
 */
public record DepartmentDirectoryEntry(
    DepartmentCode code,
    String nameEnglish,
    String nameArabic
) {

    public DepartmentDirectoryEntry {
        Objects.requireNonNull(
            code,
            "Department code must not be null."
        );

        nameEnglish = requireText(
            nameEnglish,
            "English department name"
        );

        nameArabic = normalizeOptionalText(nameArabic);
    }

    private static String requireText(
        String value,
        String fieldName
    ) {
        Objects.requireNonNull(
            value,
            fieldName + " must not be null."
        );

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank."
            );
        }

        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }
}