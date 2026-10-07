package org.badea.correspondence.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class EmployeeDirectoryEntryTest {

    @Test
    void createsResolvedEmployeeEntry() {
        EmployeeDirectoryEntry employee =
            new EmployeeDirectoryEntry(
                new EmployeeId("17257"),
                new DirectoryUsername(
                    "adil.amin@badea.org"
                ),
                "Adil Mustafa Amin",
                "عادل مصطفى امين",
                Optional.of(
                    new DepartmentDirectoryEntry(
                        new DepartmentCode("117"),
                        "Information Technology",
                        "تقنية المعلومات"
                    )
                )
            );

        assertEquals(
            "17257",
            employee.employeeId().value()
        );

        assertEquals(
            "adil.amin",
            employee.username().value()
        );

        assertEquals(
            "Adil Mustafa Amin",
            employee.nameEnglish()
        );

        assertEquals(
            "عادل مصطفى امين",
            employee.nameArabic()
        );

        DepartmentDirectoryEntry department =
            employee.department().orElseThrow();

        assertEquals(
            "117",
            department.code().value()
        );

        assertEquals(
            "Information Technology",
            department.nameEnglish()
        );

        assertEquals(
            "تقنية المعلومات",
            department.nameArabic()
        );
    }

    @Test
    void trimsEmployeeNames() {
        EmployeeDirectoryEntry employee =
            new EmployeeDirectoryEntry(
                new EmployeeId("17257"),
                new DirectoryUsername("adil.amin"),
                "  Adil Mustafa Amin  ",
                "  عادل مصطفى امين  ",
                Optional.of(
                    new DepartmentDirectoryEntry(
                        new DepartmentCode("117"),
                        "Information Technology",
                        "تقنية المعلومات"
                    )
                )
            );

        assertEquals(
            "Adil Mustafa Amin",
            employee.nameEnglish()
        );

        assertEquals(
            "عادل مصطفى امين",
            employee.nameArabic()
        );
    }

    @Test
    void allowsMissingArabicEmployeeName() {
        EmployeeDirectoryEntry employee =
            new EmployeeDirectoryEntry(
                new EmployeeId("17257"),
                new DirectoryUsername("adil.amin"),
                "Adil Mustafa Amin",
                null,
                Optional.of(
                    new DepartmentDirectoryEntry(
                        new DepartmentCode("117"),
                        "Information Technology",
                        null
                    )
                )
            );

        assertNull(employee.nameArabic());
    }

    @Test
    void allowsEmployeeWithoutCurrentDepartment() {
        EmployeeDirectoryEntry employee =
            new EmployeeDirectoryEntry(
                new EmployeeId("0685"),
                new DirectoryUsername("employee.user"),
                "Example Employee",
                null,
                Optional.empty()
            );

        assertTrue(employee.department().isEmpty());
    }

    @Test
    void rejectsBlankEnglishEmployeeName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new EmployeeDirectoryEntry(
                new EmployeeId("17257"),
                new DirectoryUsername("adil.amin"),
                "   ",
                null,
                Optional.empty()
            )
        );
    }
}