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
        assertEquals(List.of("Digital Transformation"),
                PdfBoxOfficeActionSheetRenderer.sourceFieldLines("Digital Transformation"));
        assertEquals(List.of("إدارة التحول الرقمي"),
                PdfBoxOfficeActionSheetRenderer.sourceFieldLines("إدارة التحول الرقمي"));
        assertFalse(PdfBoxOfficeActionSheetRenderer.needsSourceAnnex("إدارة التحول الرقمي"));
        assertTrue(PdfBoxOfficeActionSheetRenderer.sourceFieldFontSize(
                "Digital Transformation") >= 7f);
        assertTrue(PdfBoxOfficeActionSheetRenderer.sourceFieldFontSize(
                "Digital Transformation") <= 9f);
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
                "توجيهات الرئيس", "استكمال مكتب الرئيس", "ملاحظات إدارية", "جهة إضافية خاصة", "");
        byte[] bytes=new PdfBoxOfficeActionSheetRenderer().render(source,plan,
                List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),"Digital Transformation","إدارة التحول الرقمي")),List.of(),printContext());
        assertTrue(bytes.length>1000);
        try (var pdf=Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages()>=2);
            assertEquals(595.3,pdf.getPage(0).getMediaBox().getWidth(),1.5);
        }
    }
    @Test void ordinaryOfficeMetadataDoesNotInsertAnUnrequestedSecondPacketPage() throws Exception {
        var source = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Test President Inbox", null, "117/20261102/7", LocalDate.of(2026,11,2),
                "إدارة التحول الرقمي", "117", "Author", null, List.of());
        var recipients = new DistributionRecipients(List.of(new DepartmentCode("117")),List.of());
        var plan = new OfficeDistributionPlan(2,2,recipients,List.of("required-action"),
                "", "مكتمل من مكتب الرئيس", "ملاحظات إدارية مسجلة في النظام", "", "");
        byte[] bytes = new PdfBoxOfficeActionSheetRenderer().render(source,plan,
                List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),
                        "Digital Transformation","إدارة التحول الرقمي")), List.of(), printContext());
        try (var pdf = Loader.loadPDF(bytes)) {
            assertEquals(1,pdf.getNumberOfPages(),
                    "Ordinary office metadata is recorded in history, not a duplicate summary page");
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

    @Test void legacyExecutiveEntityRendersEvenWhenSourceTextWasNotSaved() {
        var source = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject", null, "101/20261103/1", LocalDate.of(2026,11,3), null,
                "101", "Office", null, List.of(), List.of(), "{\"entity\":\"Kenya\"}");
        assertEquals("Kenya", PdfBoxOfficeActionSheetRenderer.sourceValue(source));
    }

    @Test void attachmentsShowOnCoverUnlessFullListNeedsAnnex() {
        var one = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject", null, "101/20261103/1", LocalDate.of(2026,11,3), "Kenya",
                "101", "Office", null, List.of(new org.badea.correspondence.application.CorrespondenceDocument(
                        "attachment-1", "brief.pdf", "application/pdf", 123L)),
                List.of(), null);
        assertEquals("brief.pdf", PdfBoxOfficeActionSheetRenderer.attachmentCoverLabel(one));
        assertFalse(PdfBoxOfficeActionSheetRenderer.needsAttachmentAnnex(one));

        var longList = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject", null, "101/20261103/1", LocalDate.of(2026,11,3), "Kenya",
                "101", "Office", null, List.of(
                        new org.badea.correspondence.application.CorrespondenceDocument(
                                "attachment-1", "regional-governance-report-long-name.pdf", "application/pdf", 123L),
                        new org.badea.correspondence.application.CorrespondenceDocument(
                                "attachment-2", "presidential-departmental-communication-reference.pdf", "application/pdf", 123L)),
                List.of(), null);
        assertTrue(PdfBoxOfficeActionSheetRenderer.needsAttachmentAnnex(longList));
        assertTrue(PdfBoxOfficeActionSheetRenderer.attachmentCoverLabel(longList).contains("2"));
    }

    @Test void roundTwoCoverAndAnnexDoNotCallEarlierAttachmentsNew() throws Exception {
        var roundOne = new org.badea.correspondence.application.CorrespondenceDocument(
                "old", "a-long-original-attachment-from-round-one-and-earlier-correspondence.pdf",
                "application/pdf", 123L, 1L);
        var legacy = new org.badea.correspondence.application.CorrespondenceDocument(
                "legacy", "unclassified-legacy.pdf", "application/pdf", 123L);
        var roundTwo = new org.badea.correspondence.application.CorrespondenceDocument(
                "new", "round-2-only.pdf", "application/pdf", 123L, 2L);
        var source = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject", null, "117/20261105/2", LocalDate.of(2026,11,5), "IT",
                "117", "Author", null, List.of(roundOne, legacy, roundTwo), List.of(), null, 2L);
        assertEquals(List.of(roundTwo), PdfBoxOfficeActionSheetRenderer.printedRoundAttachments(source));
        assertEquals("round-2-only.pdf", PdfBoxOfficeActionSheetRenderer.attachmentCoverLabel(source));
        assertFalse(PdfBoxOfficeActionSheetRenderer.needsAttachmentAnnex(source));
        var recipients = new DistributionRecipients(List.of(new DepartmentCode("117")), List.of());
        var plan = new OfficeDistributionPlan(2,2,recipients,List.of(),"","","","","");
        var to = List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),
                "Digital Transformation", "إدارة التحول الرقمي"));
        try (var pdf = Loader.loadPDF(new PdfBoxOfficeActionSheetRenderer().render(
                source, plan, to, List.of(), printContext()))) {
            assertEquals(1, pdf.getNumberOfPages(),
                    "Historical attachment names must not force an annex for the new round");
        }
    }

    @Test void roundThreeWithoutNewFilesPreservesPreviousFilesWithoutMislabeling() {
        var roundOne = new org.badea.correspondence.application.CorrespondenceDocument(
                "a", "old.pdf", "application/pdf", 123L, 1L);
        var source = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject", null, "117/20261105/3", LocalDate.of(2026,11,5), "IT",
                "117", "Author", null, List.of(roundOne), List.of(), null, 3L);
        assertTrue(PdfBoxOfficeActionSheetRenderer.printedRoundAttachments(source).isEmpty());
        assertEquals("لا توجد مرفقات جديدة", PdfBoxOfficeActionSheetRenderer.attachmentCoverLabel(source));
        assertFalse(PdfBoxOfficeActionSheetRenderer.needsAttachmentAnnex(source));
    }

    @Test void firstRoundStillIncludesLegacyUnclassifiedSourceFiles() {
        var legacy = new org.badea.correspondence.application.CorrespondenceDocument(
                "legacy", "initial.pdf", "application/pdf", 123L);
        var source = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject", null, "117/20261105/1", LocalDate.of(2026,11,5), "IT",
                "117", "Author", null, List.of(legacy), List.of(), null, 1L);
        assertEquals(List.of(legacy), PdfBoxOfficeActionSheetRenderer.printedRoundAttachments(source));
        assertEquals("initial.pdf", PdfBoxOfficeActionSheetRenderer.attachmentCoverLabel(source));
    }

    @Test void generationTimestampPreservesOfficialDepartmentRecipientRule() {
        var stamp = new OfficeActionSheetPrintContext(LocalDate.of(2026, 11, 3),
            java.util.Map.of(), null, java.time.LocalTime.of(14, 7, 45));
        assertEquals("03/11/2026 14:07", stamp.generatedAtLabel());
        assertEquals("03/11/2026", new OfficeActionSheetPrintContext(
            LocalDate.of(2026, 11, 3), java.util.Map.of()).generatedAtLabel());
        assertEquals("مدير إدارة التحول الرقمي", PdfBoxOfficeActionSheetRenderer.recipientHeader(
            List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),
                "Digital Transformation", "إدارة التحول الرقمي")), stamp));
    }

    @Test void instructionMarksAreConfinedToMatchingCheckboxes() throws Exception {
        var source = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject", null, "117/20261105/1", LocalDate.of(2026,11,5),
                "Digital Transformation", "117", "Author", null, List.of());
        var recipients = new DistributionRecipients(List.of(new DepartmentCode("117")),List.of());
        var to = List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),
                "Digital Transformation", "إدارة التحول الرقمي"));
        var renderer = new PdfBoxOfficeActionSheetRenderer();
        var empty = new OfficeDistributionPlan(1,1,recipients,List.of(),"","","","","");
        var checked = new OfficeDistributionPlan(1,1,recipients,
                List.of("required-action", "study-and-opinion"),"","","","","");
        try (var without = Loader.loadPDF(renderer.render(source,empty,to,List.of(),printContext()));
             var with = Loader.loadPDF(renderer.render(source,checked,to,List.of(),printContext()))) {
            assertEquals(1,with.getNumberOfPages());
            var base = new org.apache.pdfbox.rendering.PDFRenderer(without).renderImage(0);
            var selected = new org.apache.pdfbox.rendering.PDFRenderer(with).renderImage(0);
            assertTrue(differentPixels(base,selected,335,385,346,396)>8,
                    "Right-hand selected tick must be drawn inside its action box");
            assertTrue(differentPixels(base,selected,46,385,57,396)>8,
                    "Left-hand selected tick must be drawn inside its action box");
            assertEquals(0,differentPixels(base,selected,23,210,572,364),
                    "Instruction choices must not alter the department grid");
            assertEquals(0,differentPixels(base,selected,350,585,545,623),
                    "Instruction choices must not alter signature or date rendering");
        }
    }

    @Test void threePrintedCaptionsAreLighterWithoutChangingCoverPageLayout() throws Exception {
        var to = List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),
                "Digital Transformation", "إدارة التحول الرقمي"));
        var copy = List.of(new DepartmentDirectoryEntry(new DepartmentCode("118"),
                "Human Resources", "إدارة الموارد البشرية"));
        var source = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject", null, "117/20261110/1", LocalDate.of(2026,11,10),
                "IT", "117", "Author", null, List.of());
        var plan = new OfficeDistributionPlan(1,1,
                new DistributionRecipients(List.of(new DepartmentCode("117")),
                        List.of(new DepartmentCode("118"))),
                List.of(), "", "", "", "", "");
        try (var resource = PdfBoxOfficeActionSheetRenderer.class.getResourceAsStream(
                    "/org/badea/correspondence/templates/action-sheet.pdf")) {
            assertNotNull(resource);
            try (var original = Loader.loadPDF(resource.readAllBytes());
                 var updated = Loader.loadPDF(new PdfBoxOfficeActionSheetRenderer().render(
                         source,plan,to,copy,printContext()))) {
                assertEquals(1,updated.getNumberOfPages());
                assertEquals(original.getPage(0).getMediaBox().getWidth(),
                        updated.getPage(0).getMediaBox().getWidth(),0.01);
                assertEquals(original.getPage(0).getMediaBox().getHeight(),
                        updated.getPage(0).getMediaBox().getHeight(),0.01);
                var before = new org.apache.pdfbox.rendering.PDFRenderer(original).renderImage(0);
                var after = new org.apache.pdfbox.rendering.PDFRenderer(updated).renderImage(0);
                assertCaptionLighter(before,after,494,143,569,163,"الموضوع");
                assertCaptionLighter(before,after,473,176,568,194,"تاريخ الوثيقة");
                assertCaptionLighter(before,after,455,657,558,675,"صورة مع التحية");
            }
        }
    }

    private static void assertCaptionLighter(java.awt.image.BufferedImage before,
            java.awt.image.BufferedImage after, int x1, int y1, int x2, int y2, String label) {
        int originalInk = darkPixels(before,x1,y1,x2,y2);
        int updatedInk = darkPixels(after,x1,y1,x2,y2);
        assertTrue(originalInk > 0 && updatedInk > 0 && updatedInk < originalInk,
                "The " + label + " caption should remain visible with lighter typography");
    }

    private static int darkPixels(java.awt.image.BufferedImage image,
            int x1, int y1, int x2, int y2) {
        int count=0;
        for (int y=y1; y<y2; y++) {
            for (int x=x1; x<x2; x++) {
                int pixel=image.getRGB(x,y);
                int red=(pixel >> 16) & 255;
                int green=(pixel >> 8) & 255;
                int blue=pixel & 255;
                if (red+green+blue < 600) count++;
            }
        }
        return count;
    }

    private static int differentPixels(java.awt.image.BufferedImage left,
            java.awt.image.BufferedImage right, int x1,int y1,int x2,int y2) {
        int diff=0;
        for (int y=y1; y<y2; y++) {
            for (int x=x1; x<x2; x++) {
                if (left.getRGB(x,y)!=right.getRGB(x,y)) diff++;
            }
        }
        return diff;
    }

    @Test void longAttachmentFilenamesUseAnnexWithoutChangingOfficialSources() throws Exception {
        var source = new CorrespondenceDetails("id", CorrespondenceState.WITH_PRESIDENT_OFFICE,
                "Subject", null, "117/20261105/1", LocalDate.of(2026,11,5),
                "Digital Transformation", "117", "Author", null,
                List.of(new CorrespondenceDocument("1",
                        "regional-governance-report-long-name.pdf", "application/pdf", 100),
                        new CorrespondenceDocument("2",
                        "presidential-departmental-communication-reference.pdf", "application/pdf", 100)));
        assertTrue(PdfBoxOfficeActionSheetRenderer.needsAttachmentAnnex(source));
        assertTrue(PdfBoxOfficeActionSheetRenderer.attachmentCoverLabel(source).contains("2"));
        var recipients = new DistributionRecipients(List.of(new DepartmentCode("117")),List.of());
        var to = List.of(new DepartmentDirectoryEntry(new DepartmentCode("117"),
                "Digital Transformation", "إدارة التحول الرقمي"));
        var result = new PdfBoxOfficeActionSheetRenderer().render(source,
                new OfficeDistributionPlan(1,1,recipients,List.of(),"","","","",""),
                to,List.of(),printContext());
        try (var pdf=Loader.loadPDF(result)) {
            assertEquals(2,pdf.getNumberOfPages(),
                    "Long attachment names belong in a separate annex, not over the signature");
        }
        assertEquals("regional-governance-report-long-name.pdf",source.documents().get(0).filename());
    }


}
