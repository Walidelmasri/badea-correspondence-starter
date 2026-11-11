package org.badea.correspondence.infrastructure.mail;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorkflowEmailDeliveryPolicyTest {
    @Test void onlyTwoApprovedTestMailboxesArePermitted() {
        var policy = WorkflowEmailDeliveryPolicy.parse("ALLOWLIST",
            "walid.salem@badea.org, beilahi@badea.org");
        assertTrue(policy.permits("walid.salem@badea.org"));
        assertTrue(policy.permits("beilahi@badea.org"));
        assertFalse(policy.permits("president@badea.org"));
        assertFalse(policy.permits("someone@badea.org"));
        assertFalse(policy.permits("attacker@example.com"));
    }

    @Test void absentInvalidOrDuplicateAllowlistNeverOpensTheGate() {
        for (String invalid : new String[] {null, "", "president@example.com", "walid.salem@badea.org,",
                "walid.salem@badea.org,walid.salem@badea.org"}) {
            assertThrows(IllegalArgumentException.class,
                () -> WorkflowEmailDeliveryPolicy.parse("ALLOWLIST", invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> WorkflowEmailDeliveryPolicy.parse(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> WorkflowEmailDeliveryPolicy.parse("ALL", "walid.salem@badea.org"));
        assertFalse(WorkflowEmailDeliveryPolicy.disabled().permits("walid.salem@badea.org"));
    }

    @Test void productionRequiresExplicitAllModeAndEmptyAllowlist() {
        var policy = WorkflowEmailDeliveryPolicy.parse("ALL", null);
        assertTrue(policy.permits("president@badea.org"));
        assertFalse(policy.permits("external@example.com"));
    }
}
