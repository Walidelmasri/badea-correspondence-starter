package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class DirectorContinueDescriptorTest {
    @Test void endpointIsAuthenticatedAndTransactional() throws Exception {
        String path = "/alfresco/extension/templates/webscripts/org/badea/correspondence/"
                + "director-continue.post.desc.xml";
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, "Missing Director continuation Web Script descriptor");
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(input);
            assertEquals("/badea/director-continue/{technicalId}",
                    doc.getElementsByTagName("url").item(0).getTextContent().trim());
            assertEquals("user", doc.getElementsByTagName("authentication").item(0).getTextContent().trim());
            assertEquals("required", doc.getElementsByTagName("transaction").item(0).getTextContent().trim());
        }
    }
}
