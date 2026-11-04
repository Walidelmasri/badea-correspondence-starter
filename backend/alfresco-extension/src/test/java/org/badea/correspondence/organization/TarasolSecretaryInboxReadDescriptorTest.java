package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class TarasolSecretaryInboxReadDescriptorTest {
    private static final String BASE =
        "alfresco/extension/templates/webscripts/org/badea/correspondence/";

    @Test
    void newWebScriptsHaveSupportedTransactionsAndMatchingSpringBeans() throws Exception {
        String[][] entries = {
            {"tarasol-secretary-inbox-details.get", "/badea/tarasol/secretary/inbox/{technicalId}"},
            {"tarasol-secretary-inbox-history.get", "/badea/tarasol/secretary/inbox/{technicalId}/history"},
            {"tarasol-secretary-inbox-document.get", "/badea/tarasol/secretary/inbox/{technicalId}/documents/{nodeId}/content"}
        };
        var parser = DocumentBuilderFactory.newInstance();
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var springInput = getClass().getClassLoader().getResourceAsStream(
            "alfresco/module/badea-correspondence/module-context.xml");
        assertNotNull(springInput);
        org.w3c.dom.NodeList beans;
        try (springInput) {
            beans = parser.newDocumentBuilder().parse(springInput).getElementsByTagName("bean");
            for (String[] entry : entries) {
                boolean registered = false;
                for (int i = 0; i < beans.getLength(); ++i) {
                    var bean = (Element) beans.item(i);
                    if (("webscript.org.badea.correspondence." + entry[0])
                            .equals(bean.getAttribute("id"))) {
                        registered = "org.badea.correspondence.webscript.TarasolSecretaryInboxReadWebScript"
                            .equals(bean.getAttribute("class"));
                    }
                }
                assertTrue(registered, "Missing Spring WebScript: " + entry[0]);
            }
        }
        for (String[] entry : entries) {
            var input = getClass().getClassLoader().getResourceAsStream(BASE + entry[0] + ".desc.xml");
            assertNotNull(input, entry[0]);
            try (input) {
                var root = parser.newDocumentBuilder().parse(input).getDocumentElement();
                assertEquals(entry[1], root.getElementsByTagName("url").item(0)
                    .getTextContent().trim());
                assertEquals("user", root.getElementsByTagName("authentication").item(0)
                    .getTextContent().trim());
                var transaction = (Element) root.getElementsByTagName("transaction").item(0);
                assertEquals("required", transaction.getTextContent().trim());
                assertEquals("readonly", transaction.getAttribute("allow"));
            }
        }
    }
}
