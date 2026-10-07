package org.badea.correspondence.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                new DepartmentCode("117"),
                "Information Technology",
                "تقنية المعلومات"
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

        assertEquals(
            "117",
            employee.departmentCode().value()
        );
    }

    @Test
    void trimsNames() {
        EmployeeDirectoryEntry employee =
            new EmployeeDirectoryEntry(
                new EmployeeId("17257"),
                new DirectoryUsername("adil.amin"),
                "  Adil Mustafa Amin  ",
                "  عادل مصطفى امين  ",
                new DepartmentCode("117"),
                "  Information Technology  ",
                "  تقنية المعلومات  "
            );

        assertEquals(
            "Adil Mustafa Amin",
            employee.nameEnglish()
        );

        assertEquals(
            "عادل مصطفى امين",
            employee.nameArabic()
        );

        assertEquals(
            "Information Technology",
            employee.departmentNameEnglish()
        );
    }

    @Test
    void allowsMissingArabicNames() {
        EmployeeDirectoryEntry employee =
            new EmployeeDirectoryEntry(
                new EmployeeId("17257"),
                new DirectoryUsername("adil.amin"),
                "Adil Mustafa Amin",
                null,
                new DepartmentCode("117"),
                "Information Technology",
                "   "
            );

        assertNull(employee.nameArabic());
        assertNull(employee.departmentNameArabic());
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
                new DepartmentCode("117"),
                "Information Technology",
                null
            )
        );
    }

    @Test
    void rejectsBlankEnglishDepartmentName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new EmployeeDirectoryEntry(
                new EmployeeId("17257"),
                new DirectoryUsername("adil.amin"),
                "Adil Mustafa Amin",
                null,
                new DepartmentCode("117"),
                "   ",
                null
            )
        );
    }
}