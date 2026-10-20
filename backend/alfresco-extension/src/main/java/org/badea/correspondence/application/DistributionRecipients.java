package org.badea.correspondence.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.badea.correspondence.directory.DepartmentCode;

/**
 * Merges independent President/Office instructions without overwriting presidential selections.
 * Explicit TO always takes precedence over informational COPY for a department.
 */
public record DistributionRecipients(
        List<DepartmentCode> to,
        List<DepartmentCode> copy) {
    public DistributionRecipients {
        Objects.requireNonNull(to);
        Objects.requireNonNull(copy);
        if (to.isEmpty() || to.size() + copy.size() > 100) {
            throw new IllegalArgumentException("Distribution requires 1-100 recipient departments");
        }
        if (new LinkedHashSet<>(to).size() != to.size() ||
                new LinkedHashSet<>(copy).size() != copy.size() ||
                to.stream().anyMatch(copy::contains)) {
            throw new IllegalArgumentException("Recipients must be distinct");
        }
        to = List.copyOf(to);
        copy = List.copyOf(copy);
    }

    public static DistributionRecipients merge(
            List<DepartmentCode> presidentTo, List<DepartmentCode> presidentCopy,
            List<DepartmentCode> officeTo, List<DepartmentCode> officeCopy) {
        Objects.requireNonNull(presidentTo);
        Objects.requireNonNull(presidentCopy);
        Objects.requireNonNull(officeTo);
        Objects.requireNonNull(officeCopy);
        var action = new LinkedHashSet<DepartmentCode>();
        action.addAll(presidentTo);
        action.addAll(officeTo);
        if (action.contains(null)) throw new IllegalArgumentException("Invalid TO recipient");
        var information = new LinkedHashSet<DepartmentCode>();
        information.addAll(presidentCopy);
        information.addAll(officeCopy);
        if (information.contains(null)) throw new IllegalArgumentException("Invalid COPY recipient");
        information.removeAll(action);
        return new DistributionRecipients(new ArrayList<>(action), new ArrayList<>(information));
    }
}
