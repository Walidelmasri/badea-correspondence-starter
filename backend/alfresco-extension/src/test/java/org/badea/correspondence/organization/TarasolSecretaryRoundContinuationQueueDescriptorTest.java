package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class TarasolSecretaryRoundContinuationQueueDescriptorTest {
    @Test void registersReadOnlyAuthenticatedManagerQueue() throws Exception {
        String path = "/alfresco/extension/templates/webscripts/org/badea/correspondence/"
            + "tarasol-manager-round-continuations.get.desc.xml";
        try (InputStream data = getClass().getResourceAsStream(path)) {
            assertNotNull(data);
            var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(data);
            assertEquals("user", doc.getElementsByTagName("authentication").item(0).getTextContent());
            var transactions = doc.getElementsByTagName("transaction");
            assertEquals("required", transactions.item(0).getTextContent());
            assertEquals("readonly", transactions.item(0).getAttributes()
                .getNamedItem("allow").getNodeValue());
        }
    }
}
