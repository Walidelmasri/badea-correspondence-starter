package org.badea.correspondence.infrastructure.alfresco;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.alfresco.service.cmr.security.AuthorityService;
import org.badea.correspondence.security.AuthenticatedUserProvider;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

public final class AlfrescoCorrespondenceActorRoleResolver
        implements CorrespondenceActorRoleResolver {

    private static final String PRESIDENT_AUTHORITY =
            "GROUP_BADEA_CORRESPONDENCE_PRESIDENT";

    private static final String PRESIDENT_OFFICE_AUTHORITY =
            "GROUP_BADEA_CORRESPONDENCE_PRESIDENT_OFFICE";

    private static final String DIRECTORS_AUTHORITY =
            "GROUP_BADEA_CORRESPONDENCE_DIRECTORS";

    private final AuthorityService authorityService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public AlfrescoCorrespondenceActorRoleResolver(
            AuthorityService authorityService,
            AuthenticatedUserProvider authenticatedUserProvider) {

        this.authorityService =
                Objects.requireNonNull(
                        authorityService,
                        "authorityService must not be null");

        this.authenticatedUserProvider =
                Objects.requireNonNull(
                        authenticatedUserProvider,
                        "authenticatedUserProvider must not be null");
    }

    @Override
    public CorrespondenceActorRole resolveCurrentUserRole() {

        String username =
                authenticatedUserProvider.getUsername();

        Set<String> authorities =
                authorityService.getAuthoritiesForUser(
                        username);

        EnumSet<CorrespondenceActorRole> roles =
                EnumSet.noneOf(
                        CorrespondenceActorRole.class);

        if (authorities.contains(
                PRESIDENT_AUTHORITY)) {

            roles.add(
                    CorrespondenceActorRole.PRESIDENT);
        }

        if (authorities.contains(
                PRESIDENT_OFFICE_AUTHORITY)) {

            roles.add(
                    CorrespondenceActorRole.PRESIDENT_OFFICE);
        }

        if (authorities.contains(
                DIRECTORS_AUTHORITY)) {

            roles.add(
                    CorrespondenceActorRole.DIRECTOR);
        }

        if (roles.isEmpty()) {
            throw new CorrespondenceAuthorizationException(
                    "Authenticated user has no correspondence workflow role.");
        }

        if (roles.size() > 1) {
            throw new CorrespondenceAuthorizationException(
                    "Authenticated user has multiple correspondence workflow roles.");
        }

        return roles.iterator()
                .next();
    }
}