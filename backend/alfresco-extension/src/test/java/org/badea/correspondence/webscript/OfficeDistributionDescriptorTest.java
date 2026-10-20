package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Catch Alfresco Web Script transaction/route problems before deployment. */
class OfficeDistributionDescriptorTest {
    private static final String PREFIX =
            "/alfresco/extension/templates/webscripts/org/badea/correspondence/";

    @Test void officeDispatchRequiresWriteTransaction() throws Exception {
        check("office-dispatch.post.desc.xml","/badea/office-dispatch/{technicalId}",false);
    }
    @Test void departmentListIsReadOnly() throws Exception {
        check("department-assignments.get.desc.xml","/badea/department-assignments/{technicalId}",true);
    }
    @Test void departmentCompletionRequiresWriteTransaction() throws Exception {
        check("department-assignment-complete.post.desc.xml",
                "/badea/department-assignments/{technicalId}/{assignmentId}/complete",false);
    }
    private void check(String name,String route,boolean readonly) throws Exception {
        try(InputStream input=getClass().getResourceAsStream(PREFIX + name)) {
            assertNotNull(input,"Missing Web Script descriptor: " + name);
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            Document document=factory.newDocumentBuilder().parse(input);
            assertEquals(route,document.getElementsByTagName("url").item(0).getTextContent().trim());
            Element transaction=(Element)document.getElementsByTagName("transaction").item(0);
            assertEquals("required",transaction.getTextContent().trim());
            assertEquals(readonly?"readonly":"",transaction.getAttribute("allow"));
            assertEquals("user",document.getElementsByTagName("authentication").item(0).getTextContent().trim());
        }
    }
}
