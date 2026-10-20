package org.badea.correspondence.application;

import java.util.Objects;
import java.util.List;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

public record CorrespondenceAccessContext(
        CorrespondenceState state,
        DepartmentCode originDepartmentCode,
        CorrespondenceActorRole assignedRole,
        DepartmentCode assignedDepartmentCode,
        List<CorrespondenceParticipation> participation,
        List<DepartmentAssignment> assignments) {

    public CorrespondenceAccessContext(CorrespondenceState state,
            DepartmentCode originDepartmentCode,
            CorrespondenceActorRole assignedRole, DepartmentCode assignedDepartmentCode) {
        this(state, originDepartmentCode, assignedRole, assignedDepartmentCode, List.of(), List.of());
    }

    public CorrespondenceAccessContext(CorrespondenceState state,
            DepartmentCode originDepartmentCode, CorrespondenceActorRole assignedRole,
            DepartmentCode assignedDepartmentCode, List<CorrespondenceParticipation> participation) {
        this(state, originDepartmentCode, assignedRole, assignedDepartmentCode, participation, List.of());
    }

    public CorrespondenceAccessContext {

        Objects.requireNonNull(
                state,
                "state must not be null");

        Objects.requireNonNull(
                originDepartmentCode,
                "originDepartmentCode must not be null");
        participation = List.copyOf(Objects.requireNonNull(participation));
        assignments = List.copyOf(Objects.requireNonNull(assignments));
    }
}