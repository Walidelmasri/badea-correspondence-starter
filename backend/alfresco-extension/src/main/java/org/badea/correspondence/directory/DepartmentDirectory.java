package org.badea.correspondence.directory;

import java.util.List;
import java.util.Optional;

public interface DepartmentDirectory {

    List<DepartmentDirectoryEntry> findAllCorrespondenceDepartments();

    Optional<DepartmentDirectoryEntry> findByCode(
            DepartmentCode code);
}