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
import org.badea.correspondence.organization.TarasolEffectiveAccessReader;
import org.badea.correspondence.organization.TarasolSecretaryGroupPolicy;

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
    private final TarasolEffectiveAccessReader secretaryAssignments;

    public AlfrescoNotificationRecipientResolver(AuthorityService authorities,
            EmployeeDirectory employees, DepartmentDirectory departments) {
        this(authorities, employees, departments, null);
    }

    public AlfrescoNotificationRecipientResolver(AuthorityService authorities,
            EmployeeDirectory employees, DepartmentDirectory departments,
            TarasolEffectiveAccessReader secretaryAssignments) {
        this.authorities = Objects.requireNonNull(authorities);
        this.employees = Objects.requireNonNull(employees);
        this.departments = Objects.requireNonNull(departments);
        this.secretaryAssignments = secretaryAssignments;
    }

    public record Recipient(String employeeId, String username, String email,
            String nameEnglish, String nameArabic) { }

    /** A secretary return or approval alert has exactly one persisted recipient. */
    public List<Recipient> resolveSpecificEmployee(String targetEmployeeId, String actorEmployeeId) {
        if (targetEmployeeId == null || !targetEmployeeId.matches("[0-9A-Za-z_-]{1,64}")
                || targetEmployeeId.equals(actorEmployeeId)) return List.of();
        var found = employees.findActiveByEmployeeId(new EmployeeId(targetEmployeeId));
        if (found.isEmpty()) return List.of();
        var employee = found.get();
        String username = employee.username().value();
        if (!username.matches("[a-z0-9][a-z0-9._-]{0,63}")) return List.of();
        return List.of(new Recipient(employee.employeeId().value(), username,
                username + "@badea.org", employee.nameEnglish(), employee.nameArabic()));
    }

    /** An approval request is sent only to live managers, never the whole Office. */
    public List<Recipient> resolveApprovalManagers(DepartmentCode unit,
            CorrespondenceActorRole workspaceRole, String actorEmployeeId) {
        Objects.requireNonNull(unit, "Approval unit required");
        if (workspaceRole != CorrespondenceActorRole.DIRECTOR
                && workspaceRole != CorrespondenceActorRole.PRESIDENT_OFFICE) {
            throw new IllegalArgumentException("Invalid manager approval workspace");
        }
        String group = workspaceRole == CorrespondenceActorRole.PRESIDENT_OFFICE ? OFFICE : DIRECTORS;
        return activeManagers(unit, group, actorEmployeeId);
    }

    /** Secretary return from Office is shared with this unit's actively assigned secretaries. */
    public List<Recipient> resolveUnitSecretaries(DepartmentCode unit, String actorEmployeeId) {
        Objects.requireNonNull(unit, "Recipient unit required");
        if (secretaryAssignments == null) return List.of();
        Set<String> gatewayUsers = normalizedGroupMembers(TarasolSecretaryGroupPolicy.AUTHORITY);
        if (gatewayUsers.isEmpty()) return List.of();
        Set<String> seen = new HashSet<>();
        List<Recipient> recipients = new ArrayList<>();
        for (EmployeeId id : secretaryAssignments.findCurrentSecretaryEmployeeIds(unit)) {
            if (id.value().equals(actorEmployeeId) || !seen.add(id.value())) continue;
            var found = employees.findActiveByEmployeeId(id);
            if (found.isEmpty()) continue;
            var person = found.get();
            String username = person.username().value();
            if (!gatewayUsers.contains(username) || !username.matches("[a-z0-9][a-z0-9._-]{0,63}")) continue;
            recipients.add(new Recipient(id.value(), username, username + "@badea.org",
                person.nameEnglish(), person.nameArabic()));
        }
        return recipients.stream().sorted(Comparator.comparing(Recipient::username)).toList();
    }

    private Set<String> normalizedGroupMembers(String group) {
        Set<String> members = authorities.getContainedAuthorities(AuthorityType.USER, group, false);
        if (members == null || members.isEmpty()) return Set.of();
        if (members.size() > MAX_GROUP_MEMBERS)
            throw new IllegalStateException("Correspondence notification group is unexpectedly large");
        Set<String> eligible = new HashSet<>();
        for (String member : members) {
            if (member == null || !member.strip().matches("(?i)[a-z0-9][a-z0-9._-]{0,63}(@badea\\.org)?")) continue;
            eligible.add(new DirectoryUsername(member).value());
        }
        return eligible;
    }

    private List<Recipient> activeManagers(DepartmentCode unit, String group, String actorEmployeeId) {
        Set<String> eligibleUsernames = normalizedGroupMembers(group);
        if (eligibleUsernames.isEmpty()) return List.of();
        Set<String> uniqueIds = new HashSet<>();
        List<Recipient> recipients = new ArrayList<>();
        for (var managerId : departments.findActiveManagerEmployeeIds(unit)) {
            if (!uniqueIds.add(managerId.value()) || managerId.value().equals(actorEmployeeId)) continue;
            var found = employees.findActiveByEmployeeId(managerId);
            if (found.isEmpty()) continue;
            var person = found.get();
            String username = person.username().value();
            if (!eligibleUsernames.contains(username)
                    || !username.matches("[a-z0-9][a-z0-9._-]{0,63}")) continue;
            recipients.add(new Recipient(person.employeeId().value(), username,
                    username + "@badea.org", person.nameEnglish(), person.nameArabic()));
        }
        return recipients.stream().sorted(Comparator.comparing(Recipient::username)).toList();
    }

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
            case SECRETARY, TARASOL_ADMIN, TARASOL_DELEGATE -> throw new IllegalArgumentException(
                "Secretary notifications use specific employee IDs, not a workflow group");
        };
        if (targetRole == CorrespondenceActorRole.DIRECTOR) {
            return activeManagers(targetDepartment, DIRECTORS, actorEmployeeId);
        }
        Set<String> members = authorities.getContainedAuthorities(AuthorityType.USER, group, false);
        if (members == null || members.isEmpty()) return List.of();
        if (members.size() > MAX_GROUP_MEMBERS) {
            throw new IllegalStateException("Correspondence notification group is unexpectedly large");
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
