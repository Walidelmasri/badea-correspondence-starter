package org.badea.correspondence.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DepartmentDirectoryEntryTest {

    @Test
    void createsDepartmentEntry() {
        DepartmentDirectoryEntry department =
            new DepartmentDirectoryEntry(
                new DepartmentCode("117"),
                "Information Technology",
                "تقنية المعلومات"
            );

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
    void trimsDepartmentNames() {
        DepartmentDirectoryEntry department =
            new DepartmentDirectoryEntry(
                new DepartmentCode("117"),
                "  Information Technology  ",
                "  تقنية المعلومات  "
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
    void allowsMissingArabicDepartmentName() {
        DepartmentDirectoryEntry department =
            new DepartmentDirectoryEntry(
                new DepartmentCode("117"),
                "Information Technology",
                "   "
            );

        assertNull(department.nameArabic());
    }

    @Test
    void rejectsBlankEnglishDepartmentName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new DepartmentDirectoryEntry(
                new DepartmentCode("117"),
                "   ",
                null
            )
        );
    }
}