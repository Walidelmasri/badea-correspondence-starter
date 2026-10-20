package org.badea.correspondence.infrastructure.pdf;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.badea.correspondence.application.*;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.junit.jupiter.api.Test;

class PdfBoxOfficeActionSheetRendererTest {
    @Test void officialWordTemplateProducesVectorPdfWithUntruncatedAnnex() throws Exception {
        var source=new CorrespondenceDetails("id",CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Official correspondence",null,"A-11",LocalDate.of(2026,10,20),"IT", "117","Author",null,List.of());
        var recipients=new DistributionRecipients(List.of(new DepartmentCode("117")),List.of());
        var plan=new OfficeDistributionPlan(5,2,recipients,List.of("study-and-opinion"),
                "توجيهات الرئيس", "استكمال مكتب الرئيس", "ملاحظات إدارية", "", "");
        byte[] bytes=new PdfBoxOfficeActionSheetRenderer().render(source,plan,
                List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),"Digital Transformation","إدارة التحول الرقمي")),List.of());
        assertTrue(bytes.length>1000);
        try (var pdf=Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages()>=2);
            assertEquals(595.3,pdf.getPage(0).getMediaBox().getWidth(),1.5);
        }
    }
}
