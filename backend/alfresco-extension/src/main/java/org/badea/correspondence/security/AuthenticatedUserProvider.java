package org.badea.correspondence.security;

/**
 * Provides the username of the currently authenticated application user.
 *
 * <p>The application layer depends on this abstraction rather than directly
 * on Alfresco authentication APIs. The Alfresco-specific implementation is
 * supplied by the infrastructure layer.</p>
 */
@FunctionalInterface
public interface AuthenticatedUserProvider {

    /**
     * Returns the authenticated directory username.
     *
     * @return authenticated username, such as {@code adil.amin}
     * @throws IllegalStateException when no authenticated user is available
     */
    String getUsername();
}