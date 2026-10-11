package org.badea.correspondence.application;

import java.util.Objects;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

public record CorrespondenceAccessContext(
        CorrespondenceState state,
        DepartmentCode originDepartmentCode,
        CorrespondenceActorRole assignedRole,
        DepartmentCode assignedDepartmentCode) {

    public CorrespondenceAccessContext {

        Objects.requireNonNull(
                state,
                "state must not be null");

        Objects.requireNonNull(
                originDepartmentCode,
                "originDepartmentCode must not be null");
    }
}