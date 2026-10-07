package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.AuthenticatedUserProvider;
import org.junit.jupiter.api.Test;

class CurrentEmployeeServiceTest {

    @Test
    void resolvesAuthenticatedUserToEmployee() {
        EmployeeDirectoryEntry expectedEmployee =
            createEmployee();

        AuthenticatedUserProvider userProvider =
            () -> "adil.amin";

        EmployeeDirectory directory =
            username -> Optional.of(expectedEmployee);

        CurrentEmployeeService service =
            new CurrentEmployeeService(
                userProvider,
                directory
            );

        EmployeeDirectoryEntry result =
            service.getCurrentEmployee();

        assertSame(expectedEmployee, result);
    }

    @Test
    void normalizesAuthenticatedUsernameBeforeLookup() {
        AtomicReference<DirectoryUsername> receivedUsername =
            new AtomicReference<>();

        AuthenticatedUserProvider userProvider =
            () -> "  Adil.Amin@badea.org  ";

        EmployeeDirectory directory = username -> {
            receivedUsername.set(username);
            return Optional.of(createEmployee());
        };

        CurrentEmployeeService service =
            new CurrentEmployeeService(
                userProvider,
                directory
            );

        service.getCurrentEmployee();

        assertEquals(
            "adil.amin",
            receivedUsername.get().value()
        );
    }

    @Test
    void throwsWhenAuthenticatedUserHasNoActiveEmployeeEntry() {
        AuthenticatedUserProvider userProvider =
            () -> "unknown.user";

        EmployeeDirectory directory =
            username -> Optional.empty();

        CurrentEmployeeService service =
            new CurrentEmployeeService(
                userProvider,
                directory
            );

        CurrentEmployeeNotFoundException exception =
            assertThrows(
                CurrentEmployeeNotFoundException.class,
                service::getCurrentEmployee
            );

        assertEquals(
            "No active employee directory entry was found for username: "
                + "unknown.user",
            exception.getMessage()
        );
    }

    @Test
    void propagatesEmployeeDirectoryFailure() {
        EmployeeDirectoryException expected =
            new EmployeeDirectoryException(
                "Employee directory returned ambiguous results."
            );

        AuthenticatedUserProvider userProvider =
            () -> "adil.amin";

        EmployeeDirectory directory = username -> {
            throw expected;
        };

        CurrentEmployeeService service =
            new CurrentEmployeeService(
                userProvider,
                directory
            );

        EmployeeDirectoryException actual =
            assertThrows(
                EmployeeDirectoryException.class,
                service::getCurrentEmployee
            );

        assertSame(expected, actual);
    }

    private static EmployeeDirectoryEntry createEmployee() {
        return new EmployeeDirectoryEntry(
            new EmployeeId("17257"),
            new DirectoryUsername("adil.amin"),
            "Adil Mustafa Amin",
            "عادل مصطفى امين",
            new DepartmentCode("117"),
            "Information Technology",
            "تقنية المعلومات"
        );
    }
}