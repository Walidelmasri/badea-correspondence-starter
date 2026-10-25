package org.badea.correspondence.reference;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CorrespondenceReferenceTest {
    @Test void usesDepartmentDateAndSequenceWithoutPadding() {
        assertEquals("117/20261025/1",
                new CorrespondenceReference("117", LocalDate.of(2026,10,25),1).toString());
    }
    @Test void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorrespondenceReference("DTD", LocalDate.now(),1));
        assertThrows(IllegalArgumentException.class,
                () -> new CorrespondenceReference("117", LocalDate.now(),0));
    }
}
