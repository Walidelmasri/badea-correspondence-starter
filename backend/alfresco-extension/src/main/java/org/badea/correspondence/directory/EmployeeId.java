package org.badea.correspondence.directory;

import java.util.Objects;

/**
 * Canonical BADEA employee identifier from
 * BADEA_ADDONS.EMPLOYEES.EMP_ID.
 *
 * <p>The source column is VARCHAR2 and identifiers may contain
 * leading zeroes, so employee IDs must never be treated as numeric
 * values.</p>
 */
public record EmployeeId(String value) {

    public EmployeeId {
        Objects.requireNonNull(value, "Employee ID must not be null.");

        value = value.trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                "Employee ID must not be blank."
            );
        }
    }
}