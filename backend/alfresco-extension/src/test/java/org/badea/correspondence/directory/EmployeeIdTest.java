package org.badea.correspondence.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmployeeIdTest {

    @Test
    void preservesLeadingZeroes() {
        assertEquals(
            "0685",
            new EmployeeId("0685").value()
        );
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals(
            "17257",
            new EmployeeId(" 17257 ").value()
        );
    }

    @Test
    void rejectsBlankEmployeeId() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new EmployeeId("   ")
        );
    }

    @Test
    void rejectsNullEmployeeId() {
        assertThrows(
            NullPointerException.class,
            () -> new EmployeeId(null)
        );
    }
}