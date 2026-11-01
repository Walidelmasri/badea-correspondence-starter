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
    private final DepartmentDirectory departments = mock(DepartmentDirectory.class);
    private final AlfrescoDirectorRecipientDirectory resolver =
            new AlfrescoDirectorRecipientDirectory(groups, people, departments);
    private static final DepartmentCode IT = new DepartmentCode("117");

    private void group(String... usernames) {
        when(groups.getContainedAuthorities(AuthorityType.USER,
                "GROUP_BADEA_CORRESPONDENCE_DIRECTORS", false))
                .thenReturn(Set.of(usernames));
    }

    private void active(String id, String username, String arabicName) {
        when(people.findActiveByEmployeeId(new EmployeeId(id)))
                .thenReturn(Optional.of(new EmployeeDirectoryEntry(new EmployeeId(id),
                        new DirectoryUsername(username), "English Name", arabicName,
                        Optional.empty())));
    }

    @Test void resolvesCurrentManagerWithoutRelyingOnEmployeeHomeDepartment() {
        group("director.it", "director.other");
        when(departments.findActiveManagerEmployeeIds(IT))
                .thenReturn(List.of(new EmployeeId("142")));
        active("142", "director.it", "أحمد محمد");
        assertEquals("أحمد محمد", resolver.requireDirectorNames(List.of(IT)).get(IT));
    }

    @Test void missingOrUnauthorizedManagerUsesRoleTitle() {
        group("director.other");
        when(departments.findActiveManagerEmployeeIds(IT))
                .thenReturn(List.of(new EmployeeId("142")));
        active("142", "director.it", "أحمد محمد");
        assertTrue(resolver.requireDirectorNames(List.of(IT)).isEmpty());
    }

    @Test void twoActingManagersDoNotProduceArbitraryPersonalName() {
        group("director.one", "director.two");
        when(departments.findActiveManagerEmployeeIds(IT))
                .thenReturn(List.of(new EmployeeId("142"), new EmployeeId("143")));
        assertTrue(resolver.requireDirectorNames(List.of(IT)).isEmpty());
        verify(people, never()).findActiveByEmployeeId(any());
    }

    @Test void groupUnavailableDoesNotBlockOfficeDispatch() {
        assertTrue(resolver.requireDirectorNames(List.of(IT)).isEmpty());
    }

    @Test void vacantUnitPrintsRoleTitle() {
        group("director.it");
        when(departments.findActiveManagerEmployeeIds(IT)).thenReturn(List.of());
        assertTrue(resolver.requireDirectorNames(List.of(IT)).isEmpty());
    }
}
