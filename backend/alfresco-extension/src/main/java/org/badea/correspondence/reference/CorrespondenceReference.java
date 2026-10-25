package org.badea.correspondence.reference;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Immutable display reference. Numeric department codes are deliberate until BADEA approves aliases. */
public record CorrespondenceReference(String departmentCode, LocalDate registeredDate, long sequence) {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    public CorrespondenceReference {
        departmentCode = Objects.requireNonNull(departmentCode, "departmentCode").trim();
        if (!departmentCode.matches("[0-9]+")) {
            throw new IllegalArgumentException("A numeric originating department code is required");
        }
        Objects.requireNonNull(registeredDate, "registeredDate");
        if (sequence < 1) throw new IllegalArgumentException("sequence must be positive");
    }

    @Override public String toString() {
        return departmentCode + "/" + DATE.format(registeredDate) + "/" + sequence;
    }
}
