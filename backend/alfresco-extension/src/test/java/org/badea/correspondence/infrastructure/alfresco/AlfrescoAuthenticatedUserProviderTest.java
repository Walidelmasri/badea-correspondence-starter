package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.alfresco.service.cmr.security.AuthenticationService;
import org.junit.jupiter.api.Test;

class AlfrescoAuthenticatedUserProviderTest {

    @Test
    void returnsCurrentAlfrescoUsername() {
        AuthenticationService authenticationService =
            mock(AuthenticationService.class);

        when(authenticationService.getCurrentUserName())
            .thenReturn("adil.amin");

        AlfrescoAuthenticatedUserProvider provider =
            new AlfrescoAuthenticatedUserProvider(
                authenticationService
            );

        assertEquals(
            "adil.amin",
            provider.getUsername()
        );
    }

    @Test
    void rejectsMissingAuthenticatedUser() {
        AuthenticationService authenticationService =
            mock(AuthenticationService.class);

        when(authenticationService.getCurrentUserName())
            .thenReturn(null);

        AlfrescoAuthenticatedUserProvider provider =
            new AlfrescoAuthenticatedUserProvider(
                authenticationService
            );

        assertThrows(
            IllegalStateException.class,
            provider::getUsername
        );
    }

    @Test
    void rejectsBlankAuthenticatedUser() {
        AuthenticationService authenticationService =
            mock(AuthenticationService.class);

        when(authenticationService.getCurrentUserName())
            .thenReturn("   ");

        AlfrescoAuthenticatedUserProvider provider =
            new AlfrescoAuthenticatedUserProvider(
                authenticationService
            );

        assertThrows(
            IllegalStateException.class,
            provider::getUsername
        );
    }

    @Test
    void rejectsNullAuthenticationService() {
        assertThrows(
            NullPointerException.class,
            () -> new AlfrescoAuthenticatedUserProvider(null)
        );
    }
}