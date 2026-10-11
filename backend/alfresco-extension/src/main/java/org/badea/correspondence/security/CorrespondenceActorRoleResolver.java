package org.badea.correspondence.security;

import org.badea.correspondence.workflow.CorrespondenceActorRole;

public interface CorrespondenceActorRoleResolver {

    CorrespondenceActorRole resolveCurrentUserRole();
}