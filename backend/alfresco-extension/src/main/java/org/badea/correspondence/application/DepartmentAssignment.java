package org.badea.correspondence.application;

import java.time.Instant;
import java.util.Objects;
import org.badea.correspondence.directory.DepartmentCode;

/** A durable individual recipient, associated with one original correspondence. */
public record DepartmentAssignment(
        String assignmentId,
        String technicalId,
        DepartmentCode departmentCode,
        DepartmentRecipientType recipientType,
        DepartmentAssignmentStatus status,
        String assignedByEmployeeId,
        Instant assignedAt,
        String completedByEmployeeId,
        Instant completedAt,
        String responseText) {

    public DepartmentAssignment {
        if (assignmentId == null || assignmentId.isBlank() ||
                technicalId == null || technicalId.isBlank()) {
            throw new IllegalArgumentException("Assignment and correspondence identity required");
        }
        Objects.requireNonNull(departmentCode);
        Objects.requireNonNull(recipientType);
        Objects.requireNonNull(status);
        if (assignedByEmployeeId == null || assignedByEmployeeId.isBlank()) {
            throw new IllegalArgumentException("Authenticated assignment author required");
        }
        Objects.requireNonNull(assignedAt);
        if (recipientType == DepartmentRecipientType.COPY && status != DepartmentAssignmentStatus.PENDING) {
            throw new IllegalArgumentException("Copy recipient cannot have a completed action assignment");
        }
        if (status == DepartmentAssignmentStatus.COMPLETED &&
                (completedByEmployeeId == null || completedByEmployeeId.isBlank() || completedAt == null)) {
            throw new IllegalArgumentException("Completion must identify the actor and time");
        }
        if (status == DepartmentAssignmentStatus.PENDING &&
                (completedByEmployeeId != null || completedAt != null || responseText != null)) {
            throw new IllegalArgumentException("Pending assignment cannot contain completion data");
        }
        if (responseText != null && responseText.length() > 10000) {
            throw new IllegalArgumentException("Director response exceeds permitted length");
        }
    }
    public boolean isActionableFor(DepartmentCode code) {
        return recipientType == DepartmentRecipientType.TO &&
                status == DepartmentAssignmentStatus.PENDING && departmentCode.equals(code);
    }
    public boolean isVisibleTo(DepartmentCode code) {
        return departmentCode.equals(code);
    }
}
