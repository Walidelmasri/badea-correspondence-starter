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

    DIRECTOR,

    /** Initiates personal drafts; never inherits manager or Office authority. */
    SECRETARY,

    /** Organizational administration is not a correspondence workflow authority. */
    TARASOL_ADMIN,

    /** Scoped case grants only; this role never authorizes ordinary workflow endpoints. */
    TARASOL_DELEGATE
}