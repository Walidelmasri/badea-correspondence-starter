package org.badea.correspondence.directory;

import java.util.Objects;

/**
 * Resolved employee information from BADEA's employee directory.
 *
 * <p>The employee ID is the canonical application identity after
 * directory resolution. Username is retained only as the directory
 * binding identity.</p>
 */
public record EmployeeDirectoryEntry(
    EmployeeId employeeId,
    DirectoryUsername username,
    String nameEnglish,
    String nameArabic,
    DepartmentCode departmentCode,
    String departmentNameEnglish,
    String departmentNameArabic
) {

    public EmployeeDirectoryEntry {
        Objects.requireNonNull(
            employeeId,
            "Employee ID must not be null."
        );

        Objects.requireNonNull(
            username,
            "Directory username must not be null."
        );

        nameEnglish = requireText(
            nameEnglish,
            "English employee name"
        );

        nameArabic = normalizeOptionalText(nameArabic);

        Objects.requireNonNull(
            departmentCode,
            "Department code must not be null."
        );

        departmentNameEnglish = requireText(
            departmentNameEnglish,
            "English department name"
        );

        departmentNameArabic = normalizeOptionalText(
            departmentNameArabic
        );
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

    private static String normalizeOptionalText(
        String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }
}