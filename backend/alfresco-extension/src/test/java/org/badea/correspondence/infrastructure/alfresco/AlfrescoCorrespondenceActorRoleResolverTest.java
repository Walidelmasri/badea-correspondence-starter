package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.alfresco.service.cmr.security.AuthorityService;
import org.badea.correspondence.security.AuthenticatedUserProvider;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlfrescoCorrespondenceActorRoleResolverTest {

    private static final String USERNAME =
            "walid.salem";

    private AuthorityService authorityService;
    private AuthenticatedUserProvider authenticatedUserProvider;

    private AlfrescoCorrespondenceActorRoleResolver resolver;

    @BeforeEach
    void setUp() {

        authorityService =
                mock(
                        AuthorityService.class);

        authenticatedUserProvider =
                mock(
                        AuthenticatedUserProvider.class);

        resolver =
                new AlfrescoCorrespondenceActorRoleResolver(
                        authorityService,
                        authenticatedUserProvider);

        when(authenticatedUserProvider.getUsername())
                .thenReturn(
                        USERNAME);
    }

    @Test
    void resolvesDirector() {

        when(authorityService.getAuthoritiesForUser(
                USERNAME))
                .thenReturn(
                        Set.of(
                                "GROUP_EVERYONE",
                                "GROUP_BADEA_CORRESPONDENCE_DIRECTORS"));

        CorrespondenceActorRole role =
                resolver.resolveCurrentUserRole();

        assertEquals(
                CorrespondenceActorRole.DIRECTOR,
                role);

        verify(authorityService)
                .getAuthoritiesForUser(
                        USERNAME);
    }

    @Test
    void resolvesPresident() {

        when(authorityService.getAuthoritiesForUser(
                USERNAME))
                .thenReturn(
                        Set.of(
                                "GROUP_BADEA_CORRESPONDENCE_PRESIDENT"));

        assertEquals(
                CorrespondenceActorRole.PRESIDENT,
                resolver.resolveCurrentUserRole());
    }

    @Test
    void resolvesPresidentOffice() {

        when(authorityService.getAuthoritiesForUser(
                USERNAME))
                .thenReturn(
                        Set.of(
                                "GROUP_BADEA_CORRESPONDENCE_PRESIDENT_OFFICE"));

        assertEquals(
                CorrespondenceActorRole.PRESIDENT_OFFICE,
                resolver.resolveCurrentUserRole());
    }

    @Test
    void rejectsUserWithoutCorrespondenceRole() {

        when(authorityService.getAuthoritiesForUser(
                USERNAME))
                .thenReturn(
                        Set.of(
                                "GROUP_EVERYONE",
                                "GROUP_SOME_OTHER_GROUP"));

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> resolver.resolveCurrentUserRole());
    }

    @Test
    void rejectsConflictingCorrespondenceRoles() {

        when(authorityService.getAuthoritiesForUser(
                USERNAME))
                .thenReturn(
                        Set.of(
                                "GROUP_BADEA_CORRESPONDENCE_DIRECTORS",
                                "GROUP_BADEA_CORRESPONDENCE_PRESIDENT"));

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> resolver.resolveCurrentUserRole());
    }
}