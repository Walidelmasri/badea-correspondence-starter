package org.badea.correspondence.workflow;

/**
 * Business roles that may act on a correspondence.
 *
 * <p>These are workflow roles, not authentication identities.
 * Authentication and Alfresco group membership are resolved separately.</p>
 */
public enum CorrespondenceActorRole {

    PRESIDENT,

    PRESIDENT_OFFICE,

    DIRECTOR
}