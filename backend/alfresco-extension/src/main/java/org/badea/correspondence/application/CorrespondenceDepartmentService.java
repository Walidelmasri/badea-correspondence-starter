package org.badea.correspondence.application;

import java.util.List;
import java.util.Objects;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectory;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;

public final class CorrespondenceDepartmentService {

    private final DepartmentDirectory departmentDirectory;

    public CorrespondenceDepartmentService(
            DepartmentDirectory departmentDirectory) {

        this.departmentDirectory =
                Objects.requireNonNull(
                        departmentDirectory,
                        "departmentDirectory must not be null");
    }

    public List<DepartmentDirectoryEntry> listDepartments() {

        return departmentDirectory
                .findAllCorrespondenceDepartments();
    }

    public DepartmentDirectoryEntry requireDepartment(
            DepartmentCode code) {

        Objects.requireNonNull(
                code,
                "code must not be null");

        return departmentDirectory
                .findByCode(
                        code)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Unknown correspondence department: "
                                        + code.value()));
    }
}