package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectory;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorrespondenceDepartmentServiceTest {

    private DepartmentDirectory directory;
    private CorrespondenceDepartmentService service;

    @BeforeEach
    void setUp() {

        directory =
                mock(
                        DepartmentDirectory.class);

        service =
                new CorrespondenceDepartmentService(
                        directory);
    }

    @Test
    void listsCorrespondenceDepartments() {

        List<DepartmentDirectoryEntry> expected =
                List.of(
                        new DepartmentDirectoryEntry(
                                new DepartmentCode("117"),
                                "Digital Transformation Department",
                                "إدارة التحول الرقمي"));

        when(directory.findAllCorrespondenceDepartments())
                .thenReturn(
                        expected);

        assertEquals(
                expected,
                service.listDepartments());

        verify(directory)
                .findAllCorrespondenceDepartments();
    }

    @Test
    void requiresExistingDepartment() {

        DepartmentCode code =
                new DepartmentCode("117");

        DepartmentDirectoryEntry expected =
                new DepartmentDirectoryEntry(
                        code,
                        "Digital Transformation Department",
                        "إدارة التحول الرقمي");

        when(directory.findByCode(
                code))
                .thenReturn(
                        Optional.of(
                                expected));

        assertEquals(
                expected,
                service.requireDepartment(
                        code));
    }

    @Test
    void rejectsUnknownDepartment() {

        DepartmentCode code =
                new DepartmentCode("999");

        when(directory.findByCode(
                code))
                .thenReturn(
                        Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.requireDepartment(
                        code));
    }
}