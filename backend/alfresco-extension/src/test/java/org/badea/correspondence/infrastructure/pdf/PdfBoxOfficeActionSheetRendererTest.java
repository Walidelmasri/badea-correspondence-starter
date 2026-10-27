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
    private static OfficeActionSheetPrintContext printContext() {
        return new OfficeActionSheetPrintContext(LocalDate.of(2026,10,26),
                java.util.Map.of(new DepartmentCode("117"), "أحمد محمد"));
    }
    @Test void fallsBackToRecipientRoleNotUnverifiedPersonalName() {
        var dept = new DepartmentDirectoryEntry(new DepartmentCode("117"),
                "Digital Transformation", "إدارة التحول الرقمي");
        var context = new OfficeActionSheetPrintContext(LocalDate.of(2026,10,26), java.util.Map.of());
        assertEquals("مدير إدارة التحول الرقمي",
                PdfBoxOfficeActionSheetRenderer.recipientHeader(List.of(dept), context));
    }

    @Test void recipientHeaderUsesDepartmentTitleEvenIfDirectorNameExists() {
        var recipients=List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),
                "Digital Transformation","إدارة التحول الرقمي"));
        assertEquals("مدير إدارة التحول الرقمي", PdfBoxOfficeActionSheetRenderer.recipientHeader(recipients,printContext()));
    }

    @Test void multipleDepartmentHeadTitlesUseArabicCommasInRecipientOrder() {
        var recipients=List.of(
                new DepartmentDirectoryEntry(new DepartmentCode("117"),"IT","إدارة التحول الرقمي"),
                new DepartmentDirectoryEntry(new DepartmentCode("118"),"HR","إدارة الموارد البشرية"));
        var context=new OfficeActionSheetPrintContext(LocalDate.of(2026,10,26),
                java.util.Map.of(new DepartmentCode("117"),"أحمد محمد",
                        new DepartmentCode("118"),"خالد عبدالله"));
        assertEquals("مدير إدارة التحول الرقمي، مدير إدارة الموارد البشرية",
                PdfBoxOfficeActionSheetRenderer.recipientHeader(recipients,context));
    }

    @Test void sourceFieldNeverOverwritesPrintedCountryDepartmentLabels() {
        assertEquals("في الملحق", PdfBoxOfficeActionSheetRenderer.fitSourceField(
                "Long department/country office with multiple additional source fields"));
        assertEquals("IT", PdfBoxOfficeActionSheetRenderer.fitSourceField("IT"));
        // A normal current department must not manufacture an annex page.
        assertEquals("Digital Transformation", PdfBoxOfficeActionSheetRenderer.fitSourceField(
                "Digital Transformation"));
        assertFalse(PdfBoxOfficeActionSheetRenderer.needsSourceAnnex("Digital Transformation"));
        assertEquals("Digital Transformation", String.join(" ",
                PdfBoxOfficeActionSheetRenderer.sourceFieldLines("Digital Transformation")));
        assertTrue(PdfBoxOfficeActionSheetRenderer.sourceFieldLines(
                "Digital Transformation").size() <= 2);
        assertFalse(PdfBoxOfficeActionSheetRenderer.needsSourceAnnex("إدارة التحول الرقمي"));
        assertTrue(PdfBoxOfficeActionSheetRenderer.sourceFieldFontSize(
                "Digital Transformation") >= 7f);
        assertTrue(PdfBoxOfficeActionSheetRenderer.sourceFieldFontSize(
                "Digital Transformation") < 9f);
        assertTrue(PdfBoxOfficeActionSheetRenderer.needsSourceAnnex(
                "Long department/country office with multiple additional source fields"));
    }

    @Test void longSourceFieldIsPreservedInContinuation() throws Exception {
        String sourceLabel = "Long department/country office with multiple additional source fields";
        var source = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject", null, "117/20261026/1", LocalDate.of(2026,10,26),
                sourceLabel,"117","Author", null, List.of());
        var plan = new OfficeDistributionPlan(1,1,
                new DistributionRecipients(List.of(new DepartmentCode("117")),List.of()),
                List.of(),"","","","","");
        byte[] bytes = new PdfBoxOfficeActionSheetRenderer().render(source,plan,
                List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),
                        "Digital Transformation","إدارة التحول الرقمي")),List.of(),printContext());
        try (var pdf=Loader.loadPDF(bytes)) {
            assertEquals(2, pdf.getNumberOfPages(),
                    "Long source field must produce an annex rather than collide with template captions");
        }
    }

    @Test void onlyNewActionSheetsCarryTheStoredPresidentialMark() throws Exception {
        var source = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject", null, "117/20261026/1", LocalDate.of(2026,10,26),
                "IT", "117", "Author", null, List.of());
        var plan = new OfficeDistributionPlan(1,1,
                new DistributionRecipients(List.of(new DepartmentCode("117")),List.of()),
                List.of(),"","","","","");
        var recipients = List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),
                "IT", "إدارة التحول الرقمي"));
        var signature = new PresidentSignatureDrawing(List.of(List.of(
                new PresidentSignatureDrawing.Point(.1f,.2f),
                new PresidentSignatureDrawing.Point(.3f,.8f),
                new PresidentSignatureDrawing.Point(.6f,.1f),
                new PresidentSignatureDrawing.Point(.85f,.7f))));
        byte[] blank = new PdfBoxOfficeActionSheetRenderer().render(source,plan,recipients,List.of(),
                new OfficeActionSheetPrintContext(LocalDate.of(2026,10,26),java.util.Map.of()));
        byte[] signed = new PdfBoxOfficeActionSheetRenderer().render(source,plan,recipients,List.of(),
                new OfficeActionSheetPrintContext(LocalDate.of(2026,10,26),java.util.Map.of(),signature));
        assertFalse(java.util.Arrays.equals(blank,signed));
        try (var pdf = Loader.loadPDF(signed)) {
            assertEquals(1,pdf.getNumberOfPages());
        }
    }

    @Test void generationDateIsIndependentOfOriginalDocumentDate() throws Exception {
        var source=new CorrespondenceDetails("id",CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject",null,"117/20261025/1",LocalDate.of(2026,10,20),
                "Digital Transformation","117","Author",null,List.of());
        var plan=new OfficeDistributionPlan(1,1,
                new DistributionRecipients(List.of(new DepartmentCode("117")),List.of()),
                List.of(),"","","","","");
        var to=List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),"IT","إدارة التحول الرقمي"));
        byte[] original = new PdfBoxOfficeActionSheetRenderer().render(source,plan,to,List.of(),
                new OfficeActionSheetPrintContext(LocalDate.of(2026,10,26),printContext().directorNames()));
        byte[] next = new PdfBoxOfficeActionSheetRenderer().render(source,plan,to,List.of(),
                new OfficeActionSheetPrintContext(LocalDate.of(2026,10,27),printContext().directorNames()));
        assertFalse(java.util.Arrays.equals(original,next),
                "Generation date must be stamped rather than reusing the source document date");
    }

    @Test void handwritingIsEmbeddedInExplanationAndPreservedFullSize() throws Exception {
        var source=new CorrespondenceDetails("id",CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Official correspondence",null,"B-12",LocalDate.of(2026,10,20),"IT", "117","Author",null,List.of());
        var recipients=new DistributionRecipients(List.of(new DepartmentCode("117")),List.of());
        var strokes=List.of(new PresidentInkReview.Stroke(1,"pen",List.of(
                new PresidentInkReview.Point(0.1f,0.2f),
                new PresidentInkReview.Point(0.7f,0.7f))));
        var plan=new OfficeDistributionPlan(5,2,recipients,List.of("study-and-opinion"),
                "توجيهات الرئيس", "", "", "", "", List.of(strokes));
        byte[] output=new PdfBoxOfficeActionSheetRenderer().render(source,plan,
                List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),
                        "Digital Transformation","إدارة التحول الرقمي")),List.of(),printContext());
        try (var pdf=Loader.loadPDF(output)) {
            assertEquals(2,pdf.getNumberOfPages(),"Form plus annex only for additional typed directions");
            assertTrue(pdf.getPage(0).getContents().readAllBytes().length>50,
                    "Handwriting must be embedded on the official cover as vector PDF content");
        }
    }

    @Test void officialWordTemplateProducesVectorPdfWithUntruncatedAnnex() throws Exception {
        var source=new CorrespondenceDetails("id",CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Official correspondence",null,"A-11",LocalDate.of(2026,10,20),"IT", "117","Author",null,List.of());
        var recipients=new DistributionRecipients(List.of(new DepartmentCode("117")),List.of());
        var plan=new OfficeDistributionPlan(5,2,recipients,List.of("study-and-opinion"),
                "توجيهات الرئيس", "استكمال مكتب الرئيس", "ملاحظات إدارية", "", "");
        byte[] bytes=new PdfBoxOfficeActionSheetRenderer().render(source,plan,
                List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),"Digital Transformation","إدارة التحول الرقمي")),List.of(),printContext());
        assertTrue(bytes.length>1000);
        try (var pdf=Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages()>=2);
            assertEquals(595.3,pdf.getPage(0).getMediaBox().getWidth(),1.5);
        }
    }
    @Test void simpleHandwrittenExplanationNeedsNoGenericAnnex() throws Exception {
        var source = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject", null, "117/20261025/1", LocalDate.of(2026,10,25),
                "Digital Transformation", "117", "Author", null, List.of());
        var recipients = new DistributionRecipients(List.of(new DepartmentCode("117")), List.of());
        var strokes = List.of(new PresidentInkReview.Stroke(1,"pen",List.of(
                new PresidentInkReview.Point(0.1f,0.3f), new PresidentInkReview.Point(0.6f,0.5f))));
        var plan = new OfficeDistributionPlan(1,1,recipients,List.of("study-and-opinion"),
                "", "", "", "", "", List.of(strokes));
        byte[] bytes = new PdfBoxOfficeActionSheetRenderer().render(source,plan,
                List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),
                        "Digital Transformation","إدارة التحول الرقمي")), List.of(),printContext());
        try (var pdf = Loader.loadPDF(bytes)) {
            assertEquals(1, pdf.getNumberOfPages(), "No unnecessary second page");
        }
    }

}
