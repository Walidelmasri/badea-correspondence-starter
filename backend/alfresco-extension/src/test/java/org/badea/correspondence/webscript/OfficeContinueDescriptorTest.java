package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class OfficeContinueDescriptorTest {
    @Test void continuationUsesUnambiguousAuthenticatedWriteWebscript() throws Exception {
        String path = "/alfresco/extension/templates/webscripts/org/badea/correspondence/office-continue.post.desc.xml";
        try (var input = getClass().getResourceAsStream(path)) {
            assertNotNull(input);
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(input);
            assertEquals("/badea/office-continue/{technicalId}",
                    doc.getElementsByTagName("url").item(0).getTextContent().trim());
            assertEquals("user",doc.getElementsByTagName("authentication").item(0).getTextContent().trim());
            Element transaction = (Element) doc.getElementsByTagName("transaction").item(0);
            assertEquals("required",transaction.getTextContent().trim());
        }
    }
}
