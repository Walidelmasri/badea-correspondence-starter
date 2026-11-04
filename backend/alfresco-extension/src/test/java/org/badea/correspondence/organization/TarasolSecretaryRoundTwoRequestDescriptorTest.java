package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.organization.TarasolSecretaryRoundTwoRequestService;
import org.badea.correspondence.webscript.TarasolSecretaryRoundTwoRequestWebScript;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import static org.mockito.Mockito.mock;

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
        for (String action : new String[]{"submit", "resubmit"}) {
            try (InputStream stream = getClass().getResourceAsStream(
                    "/alfresco/extension/templates/webscripts/org/badea/correspondence/"
                    + "tarasol-secretary-round-two-" + action + ".post.desc.xml")) {
                assertNotNull(stream);
                var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
                assertEquals("required", doc.getElementsByTagName("transaction").item(0)
                    .getTextContent().trim());
                assertEquals("user", doc.getElementsByTagName("authentication").item(0)
                    .getTextContent().trim());
                assertEquals("/badea/tarasol/secretary/round-two/{technicalId}/" + action,
                    doc.getElementsByTagName("url").item(0).getTextContent().trim());
            }
        }
    }
    @Test void secretaryRequestSpringBeansUseUnambiguousSupportedModes() throws Exception {
        Map<String, String> expected = Map.of(
            "webscript.org.badea.correspondence.tarasol-secretary-round-two-request.post", "SUBMIT",
            "webscript.org.badea.correspondence.tarasol-secretary-round-two-request.get", "CURRENT",
            "webscript.org.badea.correspondence.tarasol-secretary-round-two-request.put", "REVISE",
            "webscript.org.badea.correspondence.tarasol-secretary-round-two-submit.post", "SUBMIT_PREPARED",
            "webscript.org.badea.correspondence.tarasol-secretary-round-two-resubmit.post", "RESUBMIT");
        try (InputStream stream = getClass().getResourceAsStream(
                "/alfresco/module/badea-correspondence/module-context.xml")) {
            assertNotNull(stream);
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var beans = factory.newDocumentBuilder().parse(stream).getElementsByTagName("bean");
            int checked = 0;
            for (int i = 0; i < beans.getLength(); i++) {
                Element bean = (Element) beans.item(i);
                if (!TarasolSecretaryRoundTwoRequestWebScript.class.getName()
                        .equals(bean.getAttribute("class"))) continue;
                String id = bean.getAttribute("id");
                assertTrue(expected.containsKey(id), "Unexpected secretary request WebScript: " + id);
                var arguments = bean.getElementsByTagName("constructor-arg");
                assertEquals(3, arguments.getLength(), "Constructor argument count for " + id);
                String mode = ((Element) arguments.item(2)).getAttribute("value");
                assertEquals(expected.get(id), mode, "Spring constructor mode for " + id);
                assertDoesNotThrow(() -> new TarasolSecretaryRoundTwoRequestWebScript(
                    mock(CurrentEmployeeService.class),
                    mock(TarasolSecretaryRoundTwoRequestService.class), mode), id);
                checked++;
            }
            assertEquals(expected.size(), checked, "Missing secretary request WebScript bean");
        }
    }

}
