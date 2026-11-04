package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class TarasolSecretarySharedInboxDescriptorTest {
    @Test
    void registeredEndpointHasReadOnlyTransactionAndAuthenticatedAccess() throws Exception {
        var location = "alfresco/extension/templates/webscripts/org/badea/correspondence/"
            + "tarasol-secretary-inbox.get.desc.xml";
        var input = getClass().getClassLoader().getResourceAsStream(location);
        assertNotNull(input);
        try (input) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Element root = factory.newDocumentBuilder().parse(input).getDocumentElement();
            assertEquals("/badea/tarasol/secretary/inbox",
                root.getElementsByTagName("url").item(0).getTextContent().trim());
            assertEquals("user",
                root.getElementsByTagName("authentication").item(0).getTextContent().trim());
            Element transaction = (Element) root.getElementsByTagName("transaction").item(0);
            assertEquals("required", transaction.getTextContent().trim());
            assertEquals("readonly", transaction.getAttribute("allow"));
        }
        var spring = getClass().getClassLoader().getResourceAsStream(
            "alfresco/module/badea-correspondence/module-context.xml");
        assertNotNull(spring, "Module context not packaged");
        try (spring) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var beans = factory.newDocumentBuilder().parse(spring).getElementsByTagName("bean");
            boolean registered = false;
            for (int i = 0; i < beans.getLength(); ++i) {
                var bean = (Element) beans.item(i);
                if ("webscript.org.badea.correspondence.tarasol-secretary-inbox.get"
                        .equals(bean.getAttribute("id"))) {
                    registered = "org.badea.correspondence.webscript.TarasolSecretarySharedInboxWebScript"
                        .equals(bean.getAttribute("class"));
                }
            }
            assertTrue(registered, "Secretary Inbox WebScript missing Spring bean");
        }
    }
}
