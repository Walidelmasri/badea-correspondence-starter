package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;

class CorrespondenceParticipationTest {
    private final CorrespondenceParticipation officeToPresident = new CorrespondenceParticipation(
        "10001", CorrespondenceActorRole.PRESIDENT_OFFICE,
        CorrespondenceActorRole.PRESIDENT, null, Instant.parse("2026-10-20T08:00:00Z"));

    @Test void officeSentIsPersonalWhileInboxDeliveryRemainsInstitutional() {
        assertTrue(officeToPresident.sentBy(CorrespondenceActorRole.PRESIDENT_OFFICE,"10001"));
        assertFalse(officeToPresident.sentBy(CorrespondenceActorRole.PRESIDENT_OFFICE,"20002"));
        assertTrue(officeToPresident.receivedBy(CorrespondenceActorRole.PRESIDENT,null));
        assertFalse(officeToPresident.receivedBy(CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("117")));
    }

    @Test void directorRecipientsStayInTheirOwnDepartment() {
        var action = new CorrespondenceParticipation("10001",
                CorrespondenceActorRole.PRESIDENT_OFFICE, CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("117"), Instant.parse("2026-10-20T08:00:00Z"));
        assertTrue(action.receivedBy(CorrespondenceActorRole.DIRECTOR,new DepartmentCode("117")));
        assertFalse(action.receivedBy(CorrespondenceActorRole.DIRECTOR,new DepartmentCode("118")));
        assertFalse(action.sentBy(CorrespondenceActorRole.DIRECTOR,"10001"));
    }

    @Test void directorSentRequiresSameEmployee() {
        var action = new CorrespondenceParticipation("10001",CorrespondenceActorRole.DIRECTOR,
                CorrespondenceActorRole.PRESIDENT_OFFICE,null,Instant.parse("2026-10-20T08:00:00Z"));
        assertTrue(action.sentBy(CorrespondenceActorRole.DIRECTOR,"10001"));
        assertFalse(action.sentBy(CorrespondenceActorRole.DIRECTOR,"99999"));
    }
}
