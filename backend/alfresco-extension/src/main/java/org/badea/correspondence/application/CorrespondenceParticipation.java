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

    /** Office and President are institutional roles; Director deliveries are departmental. */
    public boolean receivedBy(CorrespondenceActorRole role, DepartmentCode department) {
        return role == targetRole && (role != CorrespondenceActorRole.DIRECTOR
                || (department != null && department.equals(targetDepartment)));
    }

    public boolean sentBy(CorrespondenceActorRole role, String employeeId) {
        if (actorRole != role) return false;
        // The President's Office is an institutional mailbox shared by authorized Office members.
        if (role == CorrespondenceActorRole.PRESIDENT_OFFICE) return true;
        return employeeId != null && employeeId.equals(actorEmployeeId);
    }
}
