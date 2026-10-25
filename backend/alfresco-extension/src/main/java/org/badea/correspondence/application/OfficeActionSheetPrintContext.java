package org.badea.correspondence.application;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import org.badea.correspondence.directory.DepartmentCode;

/** Snapshot used only to generate this immutable official PDF. */
public record OfficeActionSheetPrintContext(LocalDate generatedOn,
        Map<DepartmentCode, String> directorNames) {
    public OfficeActionSheetPrintContext {
        Objects.requireNonNull(generatedOn, "generatedOn");
        directorNames = Map.copyOf(Objects.requireNonNull(directorNames, "directorNames"));
        if (directorNames.values().stream().anyMatch(s -> s == null || s.isBlank())) {
            throw new IllegalArgumentException("Director names must be nonblank");
        }
    }
}
