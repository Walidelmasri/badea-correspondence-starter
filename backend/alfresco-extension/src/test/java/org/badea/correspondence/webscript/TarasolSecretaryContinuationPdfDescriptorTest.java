package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class TarasolSecretaryContinuationPdfDescriptorTest {
    private static final String ROOT =
        "/alfresco/extension/templates/webscripts/org/badea/correspondence/";

    @Test void allPdfRoutesRequireAuthenticatedAlfrescoTransactions() throws Exception {
        for (String name : List.of(
            "tarasol-secretary-round-pdf-upload.post",
            "tarasol-secretary-round-pdfs.get",
            "tarasol-secretary-round-pdf.get",
            "tarasol-secretary-round-pdf.delete",
            "tarasol-manager-round-pdfs.get",
            "tarasol-manager-round-pdf.get")) {
            try (var stream = getClass().getResourceAsStream(ROOT + name + ".desc.xml")) {
                assertNotNull(stream, name);
                var xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
                assertEquals("required", xml.getElementsByTagName("transaction")
                    .item(0).getTextContent().trim(), name);
                assertEquals("user", xml.getElementsByTagName("authentication")
                    .item(0).getTextContent().trim(), name);
            }
        }
    }
}
