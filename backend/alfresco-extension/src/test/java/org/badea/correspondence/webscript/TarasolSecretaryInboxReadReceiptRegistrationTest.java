package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;

import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class TarasolSecretaryInboxReadReceiptRegistrationTest {
    @Test
    void postRouteIsAuthenticatedWritableAndSpringHasNoConstructorAmbiguity() throws Exception {
        var descriptor = getClass().getResourceAsStream(
            "/alfresco/extension/templates/webscripts/org/badea/correspondence/"
                + "tarasol-secretary-inbox-read.post.desc.xml");
        assertNotNull(descriptor);
        try (descriptor) {
            var xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(descriptor);
            assertEquals("/badea/tarasol/secretary/inbox/{technicalId}/read",
                xml.getElementsByTagName("url").item(0).getTextContent().trim());
            assertEquals("user", xml.getElementsByTagName("authentication").item(0).getTextContent().trim());
            assertEquals("required", xml.getElementsByTagName("transaction").item(0).getTextContent().trim());
        }
        var module = getClass().getResourceAsStream(
            "/alfresco/module/badea-correspondence/module-context.xml");
        assertNotNull(module);
        try (module) {
            var xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(module);
            var beans = xml.getElementsByTagName("bean");
            int found = 0;
            for (int i = 0; i < beans.getLength(); i++) {
                var bean = (Element) beans.item(i);
                if (!"webscript.org.badea.correspondence.tarasol-secretary-inbox-read.post"
                        .equals(bean.getAttribute("id"))) continue;
                found++;
                assertEquals(TarasolSecretaryInboxReadReceiptWebScript.class.getName(),
                    bean.getAttribute("class"));
                var args = bean.getElementsByTagName("constructor-arg");
                assertEquals(3, args.getLength());
                assertEquals("badeaCorrespondenceCurrentEmployeeService", ((Element)args.item(0)).getAttribute("ref"));
                assertEquals("badeaTarasolSecretarySharedInboxService", ((Element)args.item(1)).getAttribute("ref"));
                assertEquals("badeaCorrespondenceReadReceipts", ((Element)args.item(2)).getAttribute("ref"));
            }
            assertEquals(1, found);
        }
    }
}
