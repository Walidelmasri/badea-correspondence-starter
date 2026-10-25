package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.badea.correspondence.directory.*;
import org.junit.jupiter.api.Test;

class AlfrescoDirectorRecipientDirectoryTest {
    private final AuthorityService groups = mock(AuthorityService.class);
    private final EmployeeDirectory people = mock(EmployeeDirectory.class);
    private final AlfrescoDirectorRecipientDirectory resolver =
            new AlfrescoDirectorRecipientDirectory(groups, people);
    private static final DepartmentCode IT = new DepartmentCode("117");

    private void group(String... names) {
        when(groups.getContainedAuthorities(AuthorityType.USER,
                "GROUP_BADEA_CORRESPONDENCE_DIRECTORS", false))
                .thenReturn(Set.of(names));
    }

    private void active(String username, String department, String name) {
        when(people.findActiveByUsername(new DirectoryUsername(username)))
                .thenReturn(Optional.of(new EmployeeDirectoryEntry(new EmployeeId("142"),
                        new DirectoryUsername(username), "English Name", name,
                        Optional.of(new DepartmentDirectoryEntry(new DepartmentCode(department),
                                "Department", "الإدارة")))));
    }

    @Test void resolvesActiveDirectorInMatchingDepartment() {
        group("director.it", "director.other");
        active("director.it", "117", "أحمد محمد");
        active("director.other", "118", "شخص آخر");
        assertEquals("أحمد محمد", resolver.requireDirectorNames(List.of(IT)).get(IT));
    }

    @Test void missingDirectorUsesRoleTitleInsteadOfInventingName() {
        group("director.other");
        active("director.other", "118", "شخص آخر");
        assertTrue(resolver.requireDirectorNames(List.of(IT)).isEmpty());
    }

    @Test void ambiguousDirectorReturnsNoPersonalName() {
        group("director.one", "director.two");
        active("director.one", "117", "أحمد محمد");
        active("director.two", "117", "خالد محمد");
        assertTrue(resolver.requireDirectorNames(List.of(IT)).isEmpty());
    }

    @Test void groupUnavailableDoesNotBlockOfficialDispatch() {
        assertTrue(resolver.requireDirectorNames(List.of(IT)).isEmpty());
    }
}
