package org.badea.correspondence.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DepartmentCodeTest {

    @Test
    void acceptsNumericDepartmentCodes() {
        assertEquals("101", new DepartmentCode("101").value());
        assertEquals("119", new DepartmentCode("119").value());
    }

    @Test
    void acceptsFutureNumericDepartmentCodesWithoutCodeChanges() {
        assertEquals("120", new DepartmentCode("120").value());
        assertEquals("250", new DepartmentCode("250").value());
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("101", new DepartmentCode(" 101 ").value());
    }

    @Test
    void rejectsLegacyNonNumericDepartmentCodes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new DepartmentCode("HRA")
        );
    }

    @Test
    void rejectsBlankDepartmentCodes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new DepartmentCode("   ")
        );
    }

    @Test
    void rejectsNullDepartmentCodes() {
        assertThrows(
            NullPointerException.class,
            () -> new DepartmentCode(null)
        );
    }
}
