package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;

import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class TarasolSecretaryDescriptorTest {
    private static final String ROOT =
        "alfresco/extension/templates/webscripts/org/badea/correspondence/";

    @Test
    void secretaryGetWebScriptsUseSupportedReadOnlyTransactionDeclaration() throws Exception {
        for (String file : new String[] {
                "tarasol-secretary-documents.get.desc.xml",
                "tarasol-secretary-document.get.desc.xml" }) {
            var input = getClass().getClassLoader().getResourceAsStream(ROOT + file);
            assertNotNull(input, "Missing Alfresco WebScript descriptor: " + file);
            try (input) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                Element webScript = factory.newDocumentBuilder().parse(input).getDocumentElement();
                Element transaction = (Element) webScript.getElementsByTagName("transaction").item(0);
                assertNotNull(transaction, file);
                assertEquals("required", transaction.getTextContent().trim(), file);
                assertEquals("readonly", transaction.getAttribute("allow"), file);
            }
        }
    }
}
