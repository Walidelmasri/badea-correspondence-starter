package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class TarasolSecretaryRoundTwoRequestDescriptorTest {
    @Test void bothEndpointsUseAuthenticatedRequiredTransactions() throws Exception {
        for (String method : new String[]{"get", "post"}) {
            String path = "/alfresco/extension/templates/webscripts/org/badea/correspondence/"
                + "tarasol-secretary-round-two-request." + method + ".desc.xml";
            try (InputStream stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
                assertEquals("required", doc.getElementsByTagName("transaction").item(0)
                    .getTextContent().trim());
                assertEquals("user", doc.getElementsByTagName("authentication").item(0)
                    .getTextContent().trim());
                assertEquals("/badea/tarasol/secretary/round-two/{technicalId}",
                    doc.getElementsByTagName("url").item(0).getTextContent().trim());
            }
        }
    }
}
