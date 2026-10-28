package org.badea.correspondence.application;

import java.time.Instant;
import java.util.Objects;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/** Immutable provenance from one persisted Alfresco workflow action. */
public record CorrespondenceParticipation(
        String actorEmployeeId,
        CorrespondenceActorRole actorRole,
        CorrespondenceActorRole targetRole,
        DepartmentCode targetDepartment,
        Instant actedAt) {
    public CorrespondenceParticipation {
        if (actorEmployeeId == null || actorEmployeeId.isBlank()) {
            throw new IllegalArgumentException("Historical action lacks actor identity");
        }
        Objects.requireNonNull(actorRole);
        Objects.requireNonNull(actedAt);
    }

    /** Incoming Office and President deliveries are shared by role; Director deliveries are departmental. */
    public boolean receivedBy(CorrespondenceActorRole role, DepartmentCode department) {
        return role == targetRole && (role != CorrespondenceActorRole.DIRECTOR
                || (department != null && department.equals(targetDepartment)));
    }

    public boolean sentBy(CorrespondenceActorRole role, String employeeId) {
        if (actorRole != role) return false;
        // Sent is personal for every role, including President's Office.
        // The shared Office Inbox is determined by the target role, not by sent ownership.
        return employeeId != null && employeeId.equals(actorEmployeeId);
    }
}
