package org.badea.correspondence.application;

import java.util.Objects;

import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.security.AuthenticatedUserProvider;

/**
 * Resolves the currently authenticated Alfresco user to BADEA's canonical
 * employee identity.
 */
public final class CurrentEmployeeService {

    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final EmployeeDirectory employeeDirectory;

    public CurrentEmployeeService(
        AuthenticatedUserProvider authenticatedUserProvider,
        EmployeeDirectory employeeDirectory
    ) {
        this.authenticatedUserProvider = Objects.requireNonNull(
            authenticatedUserProvider,
            "Authenticated user provider must not be null."
        );

        this.employeeDirectory = Objects.requireNonNull(
            employeeDirectory,
            "Employee directory must not be null."
        );
    }

    public EmployeeDirectoryEntry getCurrentEmployee() {
        DirectoryUsername username = new DirectoryUsername(
            authenticatedUserProvider.getUsername()
        );

        return employeeDirectory
            .findActiveByUsername(username)
            .orElseThrow(
                () -> new CurrentEmployeeNotFoundException(
                    username.value()
                )
            );
    }
}