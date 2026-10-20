package org.badea.correspondence.workflow;

/**
 * Business actions that can move a correspondence through the
 * Presidency workflow.
 *
 * <p>These values are persisted in the immutable correspondence
 * history, so their meaning must remain stable.</p>
 */
public enum CorrespondenceAction {

    /**
     * Director returns/sends the correspondence to President Office.
     */
    SEND_TO_OFFICE,

    /**
     * President Office routes the correspondence to the President.
     */
    SEND_TO_PRESIDENT,

    /**
     * President Office routes the correspondence to a Director.
     */
    SEND_TO_DIRECTOR,

    /** Immutable Office fan-out to multiple independent departmental assignments. */
    DISTRIBUTE_TO_DEPARTMENTS,

    /** Office opens a new, independently audited round after all departments finish. */
    CONTINUE_CORRESPONDENCE,

    /** Completed assigned Director initiates another round on the same correspondence. */
    DIRECTOR_CONTINUE_CORRESPONDENCE,

    /** One Director's independently completed TO assignment; parent remains active. */
    COMPLETE_DEPARTMENT_ASSIGNMENT,

    /**
     * President or Director completes the correspondence.
     */
    MARK_DONE,

    /**
     * President Office closes the correspondence administratively.
     *
     * <p>A closure reason will be required by the workflow/application
     * layer.</p>
     */
    ADMINISTRATIVE_CLOSE
}