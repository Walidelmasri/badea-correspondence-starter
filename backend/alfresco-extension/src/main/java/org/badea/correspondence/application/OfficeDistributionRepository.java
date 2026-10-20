package org.badea.correspondence.application;

import java.util.List;
import java.util.Optional;

/** Alfresco transaction-bound assignment and document persistence port. */
public interface OfficeDistributionRepository {
    Optional<String> dispatchId(String technicalId, long presidentRevision, long officeRevision);
    String distribute(OfficeDispatchCommand command, OfficeDistributionPlan plan,
            byte[] actionSheetPdf, List<DepartmentAssignment> assignments);
    List<DepartmentAssignment> assignments(String technicalId);
    DepartmentAssignment complete(String technicalId, String assignmentId,
            org.badea.correspondence.directory.EmployeeId employeeId,
            String employeeNameEnglish, org.badea.correspondence.directory.DepartmentCode department,
            String responseText);
}
