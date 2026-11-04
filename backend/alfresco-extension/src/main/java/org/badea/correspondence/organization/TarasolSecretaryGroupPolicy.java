package org.badea.correspondence.organization;

import java.util.Set;
import org.alfresco.service.cmr.security.AuthorityService;
import org.badea.correspondence.security.AuthenticatedUserProvider;

/** Alfresco secretary group is a gateway; unit authority remains in Oracle. */
public final class TarasolSecretaryGroupPolicy {
    public static final String AUTHORITY = "GROUP_BADEA_CORRESPONDENCE_SECRETARIES";

    private TarasolSecretaryGroupPolicy() { }

    public static boolean isMember(AuthorityService authorityService,
            AuthenticatedUserProvider userProvider) {
        if (authorityService == null || userProvider == null) return false;
        String username = userProvider.getUsername();
        if (username == null || username.isBlank()) return false;
        Set<String> authorities = authorityService.getAuthoritiesForUser(username);
        return authorities != null && authorities.contains(AUTHORITY);
    }
}
