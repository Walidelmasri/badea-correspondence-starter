package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Set;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectory;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;

class AlfrescoNotificationRecipientResolverTest {
    private final AuthorityService groups = mock(AuthorityService.class);
    private final EmployeeDirectory directory = mock(EmployeeDirectory.class);
    private final DepartmentDirectory departments = mock(DepartmentDirectory.class);
    private final AlfrescoNotificationRecipientResolver resolver =
            new AlfrescoNotificationRecipientResolver(groups, directory, departments);

    private void group(String role, String... members) {
        when(groups.getContainedAuthorities(AuthorityType.USER,
                "GROUP_BADEA_CORRESPONDENCE_" + role, false)).thenReturn(Set.of(members));
    }

    private void person(String username, String id, String department) {
        var dept = department == null ? Optional.<DepartmentDirectoryEntry>empty()
                : Optional.of(new DepartmentDirectoryEntry(
                        new DepartmentCode(department), "Digital", "التحول الرقمي"));
        when(directory.findActiveByUsername(new DirectoryUsername(username)))
                .thenReturn(Optional.of(new EmployeeDirectoryEntry(new EmployeeId(id),
                        new DirectoryUsername(username), username, "الاسم العربي", dept)));
    }

    private void manager(String department, String id, String username) {
        when(departments.findActiveManagerEmployeeId(new DepartmentCode(department)))
                .thenReturn(Optional.of(new EmployeeId(id)));
        when(directory.findActiveByEmployeeId(new EmployeeId(id)))
                .thenReturn(Optional.of(new EmployeeDirectoryEntry(
                        new EmployeeId(id), new DirectoryUsername(username),
                        username, "الاسم العربي", Optional.empty())));
    }

    @Test void directorUsesOnlyActiveOfficialManagerAndNotAnotherDepartmentEmployee() {
        group("DIRECTORS", "director.one", "director.other");
        manager("117", "17258", "director.one");
        person("director.other", "201", "117");
        var recipients = resolver.resolve(CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("117"), "202");
        assertEquals(1, recipients.size());
        assertEquals("17258", recipients.get(0).employeeId());
        assertEquals("director.one@badea.org", recipients.get(0).email());
        verify(directory, never()).findActiveByUsername(new DirectoryUsername("director.other"));
    }

    @Test void actorAndUnauthorizedManagerAreNeverNotified() {
        group("DIRECTORS", "director.other");
        manager("117", "17258", "director.one");
        assertTrue(resolver.resolve(CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("117"), "100").isEmpty());
        group("DIRECTORS", "director.one");
        assertTrue(resolver.resolve(CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("117"), "17258").isEmpty());
    }

    @Test void presidentReceivesOneAuthorizedActiveAccountOnly() {
        group("PRESIDENT", "president");
        person("president", "100", null);
        var recipients = resolver.resolve(CorrespondenceActorRole.PRESIDENT, null, "200");
        assertEquals(1, recipients.size());
        assertEquals("president@badea.org", recipients.get(0).email());
    }

    @Test void presidentGroupAmbiguityFailsClosed() {
        group("PRESIDENT", "president.one", "president.two");
        person("president.one", "100", null);
        person("president.two", "101", null);
        assertThrows(IllegalStateException.class,
                () -> resolver.resolve(CorrespondenceActorRole.PRESIDENT, null, "200"));
    }

    @Test void officeCanNotifyMultipleAuthorizedEmployeesButNeverActor() {
        group("PRESIDENT_OFFICE", "office.two", "office.one", "sender");
        person("office.one", "111", null);
        person("office.two", "112", null);
        person("sender", "200", null);
        var recipients = resolver.resolve(CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "200");
        assertEquals(2, recipients.size());
        assertEquals("office.one@badea.org", recipients.get(0).email());
        assertEquals("office.two@badea.org", recipients.get(1).email());
    }

    @Test void malformedOrExternalDirectoryAccountsAreExcluded() {
        group("DIRECTORS", "external@other.com", "director.valid", "!bad");
        manager("117", "210", "director.valid");
        var recipients = resolver.resolve(CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("117"), "200");
        assertEquals(1, recipients.size());
        verify(directory, never()).findActiveByUsername(new DirectoryUsername("external"));
    }

    @Test void directorNeedsDepartmentAndInactiveEmployeesAreIgnored() {
        group("DIRECTORS", "director.missing");
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(CorrespondenceActorRole.DIRECTOR, null, "200"));
        assertTrue(resolver.resolve(CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("117"), "200").isEmpty());
    }
}
