package org.badea.correspondence.application;

import java.util.List;
import java.util.Objects;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/** Independent receipt/completion permissions without modifying parent lifecycle. */
public final class DepartmentAssignmentService {
    private final CorrespondenceTransitionRepository correspondence;
    private final CorrespondenceAccessAuthorizer authorizer;
    private final OfficeDistributionRepository assignments;
    public DepartmentAssignmentService(CorrespondenceTransitionRepository correspondence,
            CorrespondenceAccessAuthorizer authorizer,OfficeDistributionRepository assignments) {
        this.correspondence=Objects.requireNonNull(correspondence);
        this.authorizer=Objects.requireNonNull(authorizer);
        this.assignments=Objects.requireNonNull(assignments);
    }
    public List<DepartmentAssignment> list(String id, CorrespondenceActorRole role,
            DepartmentCode department,String employeeId) {
        authorizer.authorizeRead(correspondence.getAccessContext(id),role,department,employeeId);
        List<DepartmentAssignment> all=assignments.assignments(id);
        if (role == CorrespondenceActorRole.DIRECTOR)
            return all.stream().filter(a->a.isVisibleTo(department)).toList();
        return all;
    }
    public DepartmentAssignment complete(String id,String assignmentId,
            CorrespondenceActorRole role,DepartmentCode department,EmployeeId employeeId,
            String employeeNameEnglish,String response) {
        if (role!=CorrespondenceActorRole.DIRECTOR || department==null)
            throw new CorrespondenceAuthorizationException("Director role and department required");
        // Only the department receiving a TO assignment can perform this action.
        boolean eligible=assignments.assignments(id).stream().anyMatch(a ->
                a.assignmentId().equals(assignmentId)
                && a.departmentCode().equals(department)
                && a.recipientType()==DepartmentRecipientType.TO);
        if (!eligible) throw new CorrespondenceAuthorizationException("No matching actionable assignment");
        return assignments.complete(id,assignmentId,employeeId,employeeNameEnglish,department,response);
    }
}
