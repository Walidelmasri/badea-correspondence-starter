package org.badea.correspondence.infrastructure.alfresco;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.badea.correspondence.application.DirectorRecipientDirectory;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;

/**
 * Uses the existing authorized Directors group and current Oracle department membership.
 * Never derives a person's name from a department label or from the sender.
 * Ambiguous/missing mappings are omitted; the renderer prints the department role title.
 */
public final class AlfrescoDirectorRecipientDirectory implements DirectorRecipientDirectory {
    private static final String DIRECTORS = "GROUP_BADEA_CORRESPONDENCE_DIRECTORS";
    private final AuthorityService authorities;
    private final EmployeeDirectory employees;

    public AlfrescoDirectorRecipientDirectory(AuthorityService authorities, EmployeeDirectory employees) {
        this.authorities = Objects.requireNonNull(authorities);
        this.employees = Objects.requireNonNull(employees);
    }

    @Override
    public Map<DepartmentCode, String> requireDirectorNames(List<DepartmentCode> departments) {
        Objects.requireNonNull(departments);
        if (departments.isEmpty()) throw new IllegalArgumentException("No recipient departments");
        var needed = new java.util.HashSet<>(departments);
        Map<DepartmentCode, String> names = new HashMap<>();
        var ambiguous = new java.util.HashSet<DepartmentCode>();
        var groupMembers = authorities.getContainedAuthorities(AuthorityType.USER, DIRECTORS, false);
        // AD group membership is not a reliable, complete source of department heads.
        // Never block an otherwise authorized distribution or guess an identity.
        if (groupMembers == null) return Map.of();
        for (String username : groupMembers.stream().sorted().toList()) {
            var employee = employees.findActiveByUsername(new DirectoryUsername(username));
            if (employee.isEmpty()) continue;
            EmployeeDirectoryEntry person = employee.get();
            var department = person.department().map(DepartmentDirectoryEntry::code);
            if (department.isEmpty() || !needed.contains(department.get())) continue;
            DepartmentCode code = department.get();
            String name = person.nameArabic() == null || person.nameArabic().isBlank()
                    ? person.nameEnglish() : person.nameArabic();
            if (name == null || name.isBlank() || ambiguous.contains(code)) continue;
            if (names.putIfAbsent(code, name) != null) {
                names.remove(code);
                ambiguous.add(code);
            }
        }
        return Map.copyOf(names);
    }
}
