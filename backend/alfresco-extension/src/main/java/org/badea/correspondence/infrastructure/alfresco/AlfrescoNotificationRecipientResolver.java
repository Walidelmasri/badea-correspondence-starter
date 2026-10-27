package org.badea.correspondence.infrastructure.alfresco;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectory;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/**
 * Resolves possible notification recipients strictly from the existing
 * correspondence AD role groups and ACTIVE Oracle employee records.
 *
 * This is NOT a recipient list inferred from the sender, the document subject,
 * a department label, or the filename. The caller must only supply the target
 * of a successfully committed workflow action.
 */
public final class AlfrescoNotificationRecipientResolver {
    private static final String PRESIDENT = "GROUP_BADEA_CORRESPONDENCE_PRESIDENT";
    private static final String OFFICE = "GROUP_BADEA_CORRESPONDENCE_PRESIDENT_OFFICE";
    private static final String DIRECTORS = "GROUP_BADEA_CORRESPONDENCE_DIRECTORS";
    private static final int MAX_GROUP_MEMBERS = 250;

    private final AuthorityService authorities;
    private final EmployeeDirectory employees;
    private final DepartmentDirectory departments;

    public AlfrescoNotificationRecipientResolver(AuthorityService authorities,
            EmployeeDirectory employees, DepartmentDirectory departments) {
        this.authorities = Objects.requireNonNull(authorities);
        this.employees = Objects.requireNonNull(employees);
        this.departments = Objects.requireNonNull(departments);
    }

    public record Recipient(String employeeId, String username, String email,
            String nameEnglish, String nameArabic) { }

    public List<Recipient> resolve(CorrespondenceActorRole targetRole,
            DepartmentCode targetDepartment, String actorEmployeeId) {
        Objects.requireNonNull(targetRole, "Notification target role required");
        if (targetRole == CorrespondenceActorRole.DIRECTOR && targetDepartment == null) {
            throw new IllegalArgumentException("Director notification needs a department code");
        }
        if (targetRole != CorrespondenceActorRole.DIRECTOR && targetDepartment != null) {
            throw new IllegalArgumentException("Only Director notifications have a department code");
        }
        String group = switch (targetRole) {
            case PRESIDENT -> PRESIDENT;
            case PRESIDENT_OFFICE -> OFFICE;
            case DIRECTOR -> DIRECTORS;
        };
        Set<String> members = authorities.getContainedAuthorities(AuthorityType.USER, group, false);
        if (members == null || members.isEmpty()) return List.of();
        if (members.size() > MAX_GROUP_MEMBERS) {
            throw new IllegalStateException("Correspondence notification group is unexpectedly large");
        }
        if (targetRole == CorrespondenceActorRole.DIRECTOR) {
            // COR_DEPARTMENTS is the authoritative *active manager* mapping.
            // Do not guess from any AD group member who happens to work in the unit.
            var managerId = departments.findActiveManagerEmployeeId(targetDepartment);
            if (managerId.isEmpty() || managerId.get().value().equals(actorEmployeeId)) return List.of();
            var manager = employees.findActiveByEmployeeId(managerId.get());
            if (manager.isEmpty()) return List.of();
            var person = manager.get();
            String username = person.username().value();
            boolean inAuthorizedGroup = members.stream().filter(Objects::nonNull)
                    .map(String::strip).filter(value -> value.matches("(?i)[a-z0-9][a-z0-9._-]{0,63}(@badea\\.org)?"))
                    .map(DirectoryUsername::new).anyMatch(account -> account.value().equals(username));
            if (!inAuthorizedGroup || !username.matches("[a-z0-9][a-z0-9._-]{0,63}")) return List.of();
            return List.of(new Recipient(person.employeeId().value(), username,
                    username + "@badea.org", person.nameEnglish(), person.nameArabic()));
        }
        Set<String> uniqueAccounts = new HashSet<>();
        Set<String> uniqueEmployeeIds = new HashSet<>();
        List<Recipient> recipients = new ArrayList<>();
        for (String member : members.stream().filter(Objects::nonNull).sorted().toList()) {
            // Role memberships are not permission to email arbitrary external UPNs.
            String normalized = member.strip().toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,63}(@badea\\.org)?")) continue;
            DirectoryUsername username = new DirectoryUsername(normalized);
            if (!uniqueAccounts.add(username.value())) continue;
            var found = employees.findActiveByUsername(username);
            if (found.isEmpty()) continue;
            EmployeeDirectoryEntry person = found.get();
            if (actorEmployeeId != null && actorEmployeeId.equals(person.employeeId().value())) continue;
            if (!uniqueEmployeeIds.add(person.employeeId().value())) continue;
            recipients.add(new Recipient(person.employeeId().value(), username.value(),
                    username.value() + "@badea.org", person.nameEnglish(), person.nameArabic()));
        }
        // President is a single-person workflow. Never guess which of several
        // active President group members should receive a confidential alert.
        if (targetRole == CorrespondenceActorRole.PRESIDENT && recipients.size() > 1) {
            throw new IllegalStateException("Ambiguous President notification recipient");
        }
        return recipients.stream().sorted(Comparator.comparing(Recipient::username)).toList();
    }
}
