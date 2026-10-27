package org.badea.correspondence.directory;

import java.util.List;
import java.util.Optional;

public interface DepartmentDirectory {

    /** Active official head assignment for a correspondence department; legacy codes are not routed. */
    default java.util.Optional<EmployeeId> findActiveManagerEmployeeId(DepartmentCode code) {
        return java.util.Optional.empty();
    }


    List<DepartmentDirectoryEntry> findAllCorrespondenceDepartments();

    Optional<DepartmentDirectoryEntry> findByCode(
            DepartmentCode code);
}