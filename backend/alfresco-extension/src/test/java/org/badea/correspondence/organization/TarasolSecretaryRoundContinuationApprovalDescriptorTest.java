package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class TarasolSecretaryRoundContinuationApprovalDescriptorTest {
    private static final String ROOT =
        "/alfresco/extension/templates/webscripts/org/badea/correspondence/";

    @Test void managerDecisionRoutesUseRegisteredValidTransactions() throws Exception {
        for (String method : new String[] {"get", "post"}) {
            try (var input = getClass().getResourceAsStream(
                    ROOT + "tarasol-manager-round-continuation." + method + ".desc.xml")) {
                assertNotNull(input);
                var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
                assertEquals("required", doc.getElementsByTagName("transaction").item(0)
                    .getTextContent().trim());
                assertEquals("/badea/tarasol/manager/round-continuation/{technicalId}",
                    doc.getElementsByTagName("url").item(0).getTextContent().trim());
            }
        }
        try (var input = getClass().getResourceAsStream(
                "/alfresco/module/badea-correspondence/module-context.xml")) {
            assertNotNull(input);
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("tarasol-manager-round-continuation.post"));
            assertTrue(text.contains("badeaSecretaryRoundContinuationApprovalService"));
        }
    }
}
