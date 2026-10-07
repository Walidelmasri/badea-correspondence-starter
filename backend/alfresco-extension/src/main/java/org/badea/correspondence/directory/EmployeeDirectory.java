package org.badea.correspondence.directory;

import java.util.Optional;

/**
 * Provides access to BADEA's authoritative employee directory.
 *
 * <p>Implementations are responsible for resolving active employees
 * from the underlying directory source. Callers do not need to know
 * whether that source is Oracle, LDAP, or another system.</p>
 */
public interface EmployeeDirectory {

    /**
     * Finds the active employee associated with the supplied directory
     * username.
     *
     * @param username normalized authenticated directory username
     * @return the resolved employee, or empty when no active employee exists
     *         for the username
     * @throws EmployeeDirectoryException when the directory cannot be queried
     *         reliably or the underlying data is ambiguous
     */
    Optional<EmployeeDirectoryEntry> findActiveByUsername(
        DirectoryUsername username
    );
}