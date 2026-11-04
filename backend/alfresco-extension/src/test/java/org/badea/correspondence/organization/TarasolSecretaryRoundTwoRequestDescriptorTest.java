package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class TarasolSecretaryRoundTwoRequestDescriptorTest {
    @Test void allSecretaryRevisionRoutesUseAuthenticatedRequiredTransactions() throws Exception {
        for (String method : new String[]{"get", "post", "put"}) {
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
        try (InputStream stream = getClass().getResourceAsStream(
                "/alfresco/extension/templates/webscripts/org/badea/correspondence/"
                + "tarasol-secretary-round-two-resubmit.post.desc.xml")) {
            assertNotNull(stream);
            var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
            assertEquals("required", doc.getElementsByTagName("transaction").item(0)
                .getTextContent().trim());
            assertEquals("user", doc.getElementsByTagName("authentication").item(0)
                .getTextContent().trim());
            assertEquals("/badea/tarasol/secretary/round-two/{technicalId}/resubmit",
                doc.getElementsByTagName("url").item(0).getTextContent().trim());
        }
    }
}
