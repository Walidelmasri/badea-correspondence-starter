package org.badea.correspondence.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import org.badea.correspondence.directory.DepartmentCode;

/** Snapshot used only to generate this immutable official PDF. */
public record OfficeActionSheetPrintContext(LocalDate generatedOn,
        Map<DepartmentCode, String> directorNames,
        PresidentSignatureDrawing presidentSignature, LocalTime generatedTime) {
    public OfficeActionSheetPrintContext(LocalDate generatedOn, Map<DepartmentCode, String> directorNames) {
        this(generatedOn, directorNames, null, null);
    }

    public OfficeActionSheetPrintContext(LocalDate generatedOn, Map<DepartmentCode, String> directorNames,
            PresidentSignatureDrawing presidentSignature) {
        this(generatedOn, directorNames, presidentSignature, null);
    }

    /** The creation clock is only available for newly issued sheets; legacy callers remain date-only. */
    public String generatedAtLabel() {
        return generatedOn.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            + (generatedTime == null ? "" : " " + generatedTime.format(DateTimeFormatter.ofPattern("HH:mm")));
    }

    public OfficeActionSheetPrintContext {
        Objects.requireNonNull(generatedOn, "generatedOn");
        directorNames = Map.copyOf(Objects.requireNonNull(directorNames, "directorNames"));
        if (directorNames.values().stream().anyMatch(s -> s == null || s.isBlank())) {
            throw new IllegalArgumentException("Director names must be nonblank");
        }
    }
}
