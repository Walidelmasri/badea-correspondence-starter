package org.badea.correspondence.infrastructure.alfresco;

import java.util.Objects;

import org.alfresco.service.cmr.security.AuthenticationService;
import org.badea.correspondence.security.AuthenticatedUserProvider;

/**
 * Supplies the username of the user authenticated by Alfresco.
 *
 * <p>Authentication itself remains owned by Alfresco and BADEA's configured
 * AD/LDAP authentication chain. This adapter only exposes the authenticated
 * username to the correspondence application layer.</p>
 */
public final class AlfrescoAuthenticatedUserProvider
    implements AuthenticatedUserProvider {

    private final AuthenticationService authenticationService;

    public AlfrescoAuthenticatedUserProvider(
        AuthenticationService authenticationService
    ) {
        this.authenticationService = Objects.requireNonNull(
            authenticationService,
            "Authentication service must not be null."
        );
    }

    @Override
    public String getUsername() {
        String username =
            authenticationService.getCurrentUserName();

        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                "No authenticated Alfresco user is available."
            );
        }

        return username;
    }
}