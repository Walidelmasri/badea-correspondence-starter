package org.badea.correspondence.directory;

import java.util.Objects;

/**
 * Department identifier as stored by BADEA's employee directory.
 *
 * <p>Current departments use numeric codes. Legacy organizational
 * codes may contain non-numeric values and are not valid for current
 * correspondence routing.</p>
 */
public record DepartmentCode(String value) {

    public DepartmentCode {
        Objects.requireNonNull(value, "Department code must not be null.");

        value = value.trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                "Department code must not be blank."
            );
        }

        if (!value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(
                "Current department code must be numeric."
            );
        }
    }
}