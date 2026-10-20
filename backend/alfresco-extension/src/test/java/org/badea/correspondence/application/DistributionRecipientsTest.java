package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.time.Instant;
import org.badea.correspondence.directory.DepartmentCode;
import org.junit.jupiter.api.Test;

class DistributionRecipientsTest {
    private static DepartmentCode d(String value) { return new DepartmentCode(value); }

    @Test void presidentRecipientsArePreservedAndToOverridesCopy() {
        var result = DistributionRecipients.merge(
                List.of(d("117")), List.of(d("118")),
                List.of(d("119"), d("118")), List.of(d("117"), d("120")));
        assertEquals(List.of(d("117"), d("119"), d("118")), result.to());
        assertEquals(List.of(d("120")), result.copy());
    }

    @Test void cannotSendWithoutAnyActionRecipient() {
        assertThrows(IllegalArgumentException.class, () -> DistributionRecipients.merge(
                List.of(), List.of(d("118")), List.of(), List.of()));
    }

    @Test void copyIsVisibleButNeverActionable() {
        var copy = new DepartmentAssignment("a", "c", d("117"),
                DepartmentRecipientType.COPY, DepartmentAssignmentStatus.PENDING,
                "123", Instant.now(), null, null, null);
        assertTrue(copy.isVisibleTo(d("117")));
        assertFalse(copy.isActionableFor(d("117")));
    }

    @Test void oneDepartmentsCompletionDoesNotAffectOthers() {
        var completed = new DepartmentAssignment("a", "c", d("117"),
                DepartmentRecipientType.TO, DepartmentAssignmentStatus.COMPLETED,
                "123", Instant.parse("2026-10-20T10:00:00Z"), "456",
                Instant.parse("2026-10-20T11:00:00Z"), "Completed");
        var pending = new DepartmentAssignment("b", "c", d("118"),
                DepartmentRecipientType.TO, DepartmentAssignmentStatus.PENDING,
                "123", Instant.parse("2026-10-20T10:00:00Z"), null, null, null);
        assertFalse(completed.isActionableFor(d("117")));
        assertTrue(pending.isActionableFor(d("118")));
    }
}
