package org.badea.correspondence.infrastructure.alfresco;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.badea.correspondence.application.DirectorRecipientDirectory;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectory;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectory;

/** Resolves printable Director names from active Tarasol unit appointments. */
public final class AlfrescoDirectorRecipientDirectory implements DirectorRecipientDirectory {
    private static final String DIRECTORS = "GROUP_BADEA_CORRESPONDENCE_DIRECTORS";
    private final AuthorityService authorities;
    private final EmployeeDirectory employees;
    private final DepartmentDirectory departments;

    public AlfrescoDirectorRecipientDirectory(AuthorityService authorities,
            EmployeeDirectory employees, DepartmentDirectory departments) {
        this.authorities = Objects.requireNonNull(authorities);
        this.employees = Objects.requireNonNull(employees);
        this.departments = Objects.requireNonNull(departments);
    }

    @Override
    public Map<DepartmentCode, String> requireDirectorNames(List<DepartmentCode> targetDepartments) {
        Objects.requireNonNull(targetDepartments);
        if (targetDepartments.isEmpty()) throw new IllegalArgumentException("No recipient departments");
        Set<String> members = authorities.getContainedAuthorities(AuthorityType.USER, DIRECTORS, false);
        if (members == null || members.isEmpty()) return Map.of();
        Set<String> allowed = members.stream().filter(Objects::nonNull)
                .map(String::strip)
                .filter(name -> name.matches("(?i)[a-z0-9][a-z0-9._-]{0,63}(@badea\\.org)?"))
                .map(name -> new DirectoryUsername(name).value())
                .collect(Collectors.toUnmodifiableSet());
        Map<DepartmentCode, String> names = new HashMap<>();
        for (DepartmentCode code : new java.util.HashSet<>(targetDepartments)) {
            var active = departments.findActiveManagerEmployeeIds(code);
            // Never guess which manager's personal name belongs on the action sheet.
            if (active.size() != 1) continue;
            var found = employees.findActiveByEmployeeId(active.getFirst());
            if (found.isEmpty() || !allowed.contains(found.get().username().value())) continue;
            var person = found.get();
            String name = person.nameArabic() == null || person.nameArabic().isBlank()
                    ? person.nameEnglish() : person.nameArabic();
            if (name != null && !name.isBlank()) names.put(code, name);
        }
        return Map.copyOf(names);
    }
}
