package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class OfficeCompletionDescriptorTest {

    private static final String DIRECTORY =
            "/alfresco/extension/templates/webscripts/"
            + "org/badea/correspondence/";

    private static final String ROUTE =
            "/badea/office-completion/{technicalId}";

    @Test
    void getDescriptorUsesValidReadOnlyTransaction()
            throws Exception {

        Document document = load("get");

        assertEquals(
                ROUTE,
                document.getElementsByTagName("url")
                        .item(0).getTextContent().trim());

        Element transaction = (Element)
                document.getElementsByTagName("transaction")
                        .item(0);

        assertNotNull(transaction);

        assertEquals(
                "required",
                transaction.getTextContent().trim());

        assertEquals(
                "readonly",
                transaction.getAttribute("allow"));
    }

    @Test
    void putDescriptorUsesValidWriteTransaction()
            throws Exception {

        Document document = load("put");

        assertEquals(
                ROUTE,
                document.getElementsByTagName("url")
                        .item(0).getTextContent().trim());

        Element transaction = (Element)
                document.getElementsByTagName("transaction")
                        .item(0);

        assertNotNull(transaction);

        assertEquals(
                "required",
                transaction.getTextContent().trim());
    }

    private static Document load(String method)
            throws Exception {

        String path = DIRECTORY
                + "office-completion."
                + method
                + ".desc.xml";

        try (InputStream input =
                OfficeCompletionDescriptorTest.class
                        .getResourceAsStream(path)) {

            assertNotNull(input, "Missing descriptor: " + path);

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            factory.setFeature(
                    "http://apache.org/xml/features/"
                            + "disallow-doctype-decl",
                    true);

            return factory.newDocumentBuilder().parse(input);
        }
    }
}
