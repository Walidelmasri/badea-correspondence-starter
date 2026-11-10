package org.badea.correspondence.infrastructure.pdf;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.PathIterator;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.badea.correspondence.application.CorrespondenceDetails;
import org.badea.correspondence.application.CorrespondenceDocument;
import org.json.JSONException;
import org.json.JSONObject;
import org.badea.correspondence.application.OfficeActionSheetRenderer;
import org.badea.correspondence.application.OfficeActionSheetPrintContext;
import org.badea.correspondence.application.OfficeDistributionPlan;
import org.badea.correspondence.application.PresidentInkReview;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;

/**
 * Renders the supplied BADEA Word action-sheet layout as a preserved PDF background.
 * Arabic text is vector-outlined using Java TextLayout: ligatures, bidi and shaping
 * remain visually correct and no rasterized screenshots/font files are embedded.
 */
public final class PdfBoxOfficeActionSheetRenderer implements OfficeActionSheetRenderer {
    private static final String TEMPLATE = "/org/badea/correspondence/templates/action-sheet.pdf";
    private static final Font FONT = new Font("Dialog", Font.PLAIN, 11);
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<List<String>> DEPARTMENT_COLUMNS = List.of(
        List.of("نائب رئيس المصرف لقطاع الموارد المؤسسية", "إدارة الشؤون القانونية",
                "إدارة الشؤون الإدارية والخدمات اللوجستية", "إدارة التحول الرقمي",
                "إدارة العمليات المالية والمصرفية", "إدارة الموارد البشرية",
                "مكتب تقييم فعالية التنمية"),
        List.of("مكتب الشراكات التنموية", "مستشارو رئيس المصرف",
                "المكاتب الإقليمية", "الاتصال والإعلام", "إدارة الاستراتيجية",
                "إدارة المخاطر", "مكتب الامتثال والنزاهة"),
        List.of("نائب رئيس المصرف لقطاع العمليات", "إدارة العمليات قطاع عام",
                "إدارة العمليات قطاع خاص وتمويل التجارة", "إدارة الخزانة والاستثمار",
                "إدارة التدقيق الداخلي", "أمانة مجلسي المحافظين والإدارة واللجان"));
    private static final float DEPARTMENT_TABLE_LEFT = 22f;
    private static final float DEPARTMENT_TABLE_RIGHT = 573f;
    private static final float DEPARTMENT_TABLE_TOP = 197f;
    private static final float DEPARTMENT_TABLE_HEADING_HEIGHT = 19f;
    private static final float DEPARTMENT_TABLE_ROW_HEIGHT = 22f;
    private static final List<String> RIGHT_ACTION_CODES = List.of(
            "required-action", "review-and-advise", "direct-response",
            "information-and-action", "information-and-file", "prepare-response", "return-later");
    private static final List<String> LEFT_ACTION_CODES = List.of(
            "study-and-opinion", "discuss-directly", "prepare-summary", "follow-up",
            "act-per-phone-call", "apology");
    private static final Map<String,String> ACTION_NAMES = Map.ofEntries(
        Map.entry("required-action","لإجراء اللازم وفق النظام"),
        Map.entry("review-and-advise","للمراجعة والإفادة"),
        Map.entry("direct-response","للإجابة المباشرة"),
        Map.entry("information-and-action","للعلم وإجراء اللازم"),
        Map.entry("information-and-file","للعلم والحفظ"),
        Map.entry("prepare-response","لإعداد رد مناسب"),
        Map.entry("return-later","لإعادتها لي لاحقاً"),
        Map.entry("study-and-opinion","للدراسة وإبداء الرأي"),
        Map.entry("discuss-directly","للتفاهم هاتفياً أو شخصياً"),
        Map.entry("prepare-summary","لإعداد خلاصة"),
        Map.entry("follow-up","للمتابعة"),
        Map.entry("act-per-phone-call","للعمل حسب المكالمة الهاتفية"),
        Map.entry("apology","للاعتذار"), Map.entry("other","إجراء آخر"));

    @Override public byte[] render(CorrespondenceDetails source, OfficeDistributionPlan plan,
            List<DepartmentDirectoryEntry> to, List<DepartmentDirectoryEntry> copy,
            OfficeActionSheetPrintContext printContext) {
        Objects.requireNonNull(source); Objects.requireNonNull(plan);
        Objects.requireNonNull(printContext);
        if (to == null || to.isEmpty() || copy == null) throw new IllegalArgumentException("Invalid recipients");
        // Headless rendering requires a font with Arabic glyph support. Fail closed on missing glyphs.
        if (FONT.canDisplayUpTo("تعليمات الرئيس وإدارة التحول الرقمي") != -1)
            throw new IllegalStateException("Arabic-capable JVM font not available for official action sheet");
        try (InputStream file = PdfBoxOfficeActionSheetRenderer.class.getResourceAsStream(TEMPLATE)) {
            if (file == null) throw new IllegalStateException("BADEA official action-sheet template missing");
            try (PDDocument document = Loader.loadPDF(file.readAllBytes())) {
                if (document.getNumberOfPages() != 1) throw new IllegalStateException("Expected one-page action sheet template");
                PDPage base = document.getPage(0);
                float h = base.getMediaBox().getHeight();
                try (PDPageContentStream canvas = new PDPageContentStream(document,base,
                        PDPageContentStream.AppendMode.APPEND,true,true)) {
                    // Remove the template's hard-coded date without covering its printed label.
                    // The printed date field is x=399..474, y=177..192 (PDF top-left points).
                    erase(canvas,h,397,176,79,18);
                    String date = source.documentDate() == null ? "" : source.documentDate().format(DATE);
                    // Keep the value fully within the date row; the department table starts below y=197.
                    outline(canvas,date,470,188,9,h,true);
                    // Replace the entire preprinted metadata row; the old source caption
                    // extends into the document-number field in the Word template.
                    erase(canvas,h,22,161.4f,552,15.5f);
                    // Keep the official "إلى السيد/" label and address department heads by title.
                    // An overlong recipient list is detailed on the annex, not written over the table.
                    String allNames = recipientHeader(to, printContext);
                    String header = allNames;
                    if (textWidth(header, 9) > 505) {
                        header = "الأسماء كاملة في الملحق";
                    }
                    // One recipient per dedicated line, with a clear vertical gap
                    // before the printed subject label. This replaces the cramped
                    // original "إلى السيد" line without covering the BADEA masthead.
                    erase(canvas,h,22,108,573,35);
                    normalizeTemplateCaptions(canvas,h);
                    outline(canvas,"إلى السيد /",570,121,10,h,true);
                    outline(canvas,header,544,139,9,h,true);
                    outline(canvas,fitHeader(source.subject(),10,420),469,158,10,h,true);
                    drawMetadataRow(canvas,h,source);
                    drawDepartmentTable(canvas,h,to);
                    drawActions(canvas,h,plan.actionCodes());
                    // The Word form already provides three "Copy with regards"
                    // lines. Use them before producing any continuation page.
                    for (int i = 0; i < Math.min(3, copy.size()); i++) {
                        outline(canvas, recipientName(copy.get(i)), 520, 687 + 17*i, 9, h, true);
                    }
                    drawAttachments(canvas,h,source);
                    // Redraw this one signing area as a coherent, right-to-left
                    // block; the Word template's old dotted labels are overlapped.
                    cleanSigningLines(canvas,h);
                    // Write directions first: this renderer clears the printed شرح area.
                    // Stamping before it would erase the upper half of a saved signature.
                    fillExplanation(canvas,h,plan,printContext.presidentSignature() != null);
                    if (printContext.presidentSignature() != null) {
                        drawRegisteredSignature(canvas,h,printContext.presidentSignature());
                    }
                    // The issuance date/time belongs under the signature, not
                    // loose at the page footer. It is NOT claimed to be the time
                    // the President manually signed the stored signature mark.
                    outline(canvas,printContext.generatedAtLabel(),496,614,8.5f,h,true);
                }
                // The official form is a single cover page when its actual fields fit.
                // Additional pages are produced only for real overflow or material
                // that is not represented on the printed cover, never unconditionally.
                if (requiresContinuation(plan, to, copy, printContext.presidentSignature() != null)
                        || textWidth(recipientHeader(to, printContext), 9) > 505
                        || textWidth(source.subject(), 10) > 420
                        || textWidth(source.documentNumber(), 9) > 90
                        || needsSourceAnnex(sourceValue(source))
                        || needsAttachmentAnnex(source)) {
                    appendAnnex(document,source,plan,to,copy,printContext);
                }
                // The first ink page is already included in الشرح on the cover.
                // Preserve any genuinely additional handwritten pages inside the
                // same signed-off action-sheet PDF, not as a separate user document.
                var additionalInk = plan.explanationInkPages().stream()
                        .filter(page -> !page.isEmpty()).skip(1).toList();
                appendHandwrittenExplanation(document,additionalInk);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                document.save(output);
                return output.toByteArray();
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Official action sheet PDF could not be generated",failure);
        }
    }

    /**
     * Place the President's signed-off directions in the actual شرح field.
     * Standalone handwriting remains stored in Alfresco as independent audit evidence.
     */
    private static void cleanSigningLines(PDPageContentStream canvas, float pageHeight)
            throws IOException {
        // The template's signature label overlaps its dotted line. Remove the
        // ENTIRE small block (not just fragments of the dotted placeholders).
        // The explanation ends above y=583, and attachments begin below y=630.
        erase(canvas,pageHeight,341,583,216,40);
        outline(canvas,"التوقيع:",543,596,10,pageHeight,true);
        outline(canvas,"التاريخ:",543,615,10,pageHeight,true);
        canvas.setStrokingColor(0.28f,0.32f,0.32f);
        canvas.setLineWidth(0.65f);
        canvas.moveTo(349,pageHeight-601);
        canvas.lineTo(499,pageHeight-601);
        canvas.moveTo(349,pageHeight-620);
        canvas.lineTo(499,pageHeight-620);
        canvas.stroke();
        canvas.setStrokingColor(0f,0f,0f);
    }

    /**
     * Match three printed Word-template captions to the existing regular-weight
     * Arabic metadata. The masks cover only the original lettering, not the
     * dynamic subject/date, copy recipients, table, annotations or signature.
     * The approved template file and all its layout coordinates stay intact.
     */
    private static void normalizeTemplateCaptions(PDPageContentStream canvas, float h)
            throws IOException {
        erase(canvas,h,494f,143f,75f,20f);
        outline(canvas,"الموضوع:",565f,158f,10f,h,true);
        erase(canvas,h,472f,176f,96f,18f);
        outline(canvas,"تاريخ الوثيقة:",565f,188f,9.5f,h,true);
        erase(canvas,h,455f,657f,103f,18.4f);
        outline(canvas,"صورة مع التحية:",553f,672f,10f,h,true);
    }

    /** Vector mark copied only into newly issued PDFs; generated artifacts remain immutable. */
    private static void drawRegisteredSignature(PDPageContentStream canvas, float pageHeight,
            org.badea.correspondence.application.PresidentSignatureDrawing signature) throws IOException {
        canvas.saveGraphicsState();
        canvas.setLineWidth(1.5f);
        canvas.setLineCapStyle(1);
        canvas.setStrokingColor(0.07f,0.13f,0.18f);
        for (var stroke : signature.strokes()) {
            boolean first = true;
            for (var p : stroke) {
                float x = 352 + p.x() * 142f;
                float y = pageHeight - (559 + p.y() * 38f);
                if (first) { canvas.moveTo(x,y); first = false; }
                else canvas.lineTo(x,y);
            }
            canvas.stroke();
        }
        canvas.restoreGraphicsState();
    }

    private static void fillExplanation(PDPageContentStream canvas, float pageHeight,
            OfficeDistributionPlan plan, boolean signed) throws IOException {
        List<List<PresidentInkReview.Stroke>> handwritten = plan.explanationInkPages();
        boolean hasInk = handwritten.stream().anyMatch(page -> !page.isEmpty());
        String typed = plan.presidentDirections();
        if (!hasInk && (typed == null || typed.isBlank())) return;

        // Preserve the printed شرح label at the right of the form and avoid its
        // signature/date and attachment fields below. This area is 433 x 66 pt.
        erase(canvas,pageHeight,58,516,489,66);
        outline(canvas,"الشرح:",542,527,11,pageHeight,true);
        if (hasInk) {
            // The Apple Pencil canvas is 3:1. Never stretch its strokes into
            // the much wider/narrower free area in the supplied Word template.
            List<PresidentInkReview.Stroke> first = handwritten.stream()
                    .filter(page -> !page.isEmpty()).findFirst().orElse(List.of());
            float height = 54f;
            float width = height * 3f;
            float x = 250f - width / 2f;
            float y = pageHeight - 524f - height;
            PdfBoxPresidentPdfRenderer.draw(canvas, new PDRectangle(x,y,width,height),first);
            // Extra ink pages, if present, follow within the same action-sheet PDF.
            if (typed != null && !typed.isBlank()) {
                outline(canvas,"شرح إضافي مكتوب في الملحق",signed ? 335 : 484,579,8,pageHeight,true);
            }
        } else {
            // Respect the limited first-page field; the annex contains every
            // character of the typed text, with no silent truncation.
            List<String> lines = wrap(typed,signed ? 33 : 56);
            for (int i = 0; i < Math.min(3,lines.size()); i++) {
                outline(canvas,lines.get(i),signed ? 335 : 483,533+19*i,10,pageHeight,true);
            }
            if (lines.size()>3) outline(canvas,"التتمة في الملحق",signed ? 335 : 480,583,8,pageHeight,true);
        }
    }

    /** The Office compose form stored this field in executiveCompose.entity in
     * legacy correspondence, while sourceText was null. Preserve those records. */
    static String sourceValue(CorrespondenceDetails source) {
        if (source.sourceText() != null && !source.sourceText().isBlank()) {
            return source.sourceText().trim();
        }
        if (source.executiveComposeJson() == null || source.executiveComposeJson().isBlank()) {
            return "";
        }
        try {
            return new JSONObject(source.executiveComposeJson()).optString("entity", "").trim();
        } catch (JSONException malformed) {
            return "";
        }
    }

    private static void drawMetadataRow(PDPageContentStream canvas, float height,
            CorrespondenceDetails source) throws IOException {
        outline(canvas,"رقم الوثيقة:",567,174,9.5f,height,true);
        outline(canvas,fitHeader(source.documentNumber(),9,90),495,174,9,height,true);
        outline(canvas,"الدولة / الجهة / الإدارة:",383,174,9,height,true);
        SourceFieldLayout field = layoutSourceField(sourceValue(source));
        outline(canvas,field.lines().get(0),260,174,field.fontSize(),height,true);
    }

    private static final float ATTACHMENT_FIELD_WIDTH = 340f;

    /**
     * On subsequent rounds the printed attachment list describes only newly
     * added source PDFs. Original documents (including legacy unclassified
     * sources) remain in Alfresco and the complete correspondence packet, but
     * must not be misrepresented as new attachments for this round.
     */
    static List<CorrespondenceDocument> printedRoundAttachments(CorrespondenceDetails source) {
        long cycle = source.currentWorkflowCycle();
        return source.documents().stream()
                .filter(document -> document.workflowCycle() == null
                        ? cycle == 1L : document.workflowCycle() == cycle)
                .toList();
    }

    private static void drawAttachments(PDPageContentStream canvas, float height,
            CorrespondenceDetails source) throws IOException {
        erase(canvas,height,248,629,303,20);
        outline(canvas,"المرفقات:",545,643,10,height,true);
        outline(canvas,attachmentCoverLabel(source),477,643,9,height,true);
    }

    static String attachmentCoverLabel(CorrespondenceDetails source) {
        var attachments = printedRoundAttachments(source);
        if (attachments.isEmpty()) return source.currentWorkflowCycle() > 1 ? "لا توجد مرفقات جديدة" : "";
        String names = attachments.stream().map(CorrespondenceDocument::filename)
                .collect(java.util.stream.Collectors.joining("، "));
        return textWidth(names,9f) <= ATTACHMENT_FIELD_WIDTH ? names
                : "عدد المرفقات: " + attachments.size() + " (الأسماء في الملحق)";
    }

    static boolean needsAttachmentAnnex(CorrespondenceDetails source) {
        String names = printedRoundAttachments(source).stream()
                .map(CorrespondenceDocument::filename)
                .collect(java.util.stream.Collectors.joining("، "));
        return textWidth(names,9f) > ATTACHMENT_FIELD_WIDTH;
    }

    private static boolean requiresContinuation(OfficeDistributionPlan plan,
            List<DepartmentDirectoryEntry> to, List<DepartmentDirectoryEntry> copy, boolean signed) {
        // Office completion and administrative notes belong to the persisted
        // workflow history; they do not justify a duplicate printable packet page.
        if (copy.size() > 3 || copy.stream().anyMatch(entry -> recipientName(entry).length() > 60)
                || !plan.otherRecipient().isBlank() || !plan.otherAction().isBlank()) return true;
        // Preserve real recipients whose names cannot be represented in the table.
        for (DepartmentDirectoryEntry recipient : to) {
            if (DEPARTMENT_COLUMNS.stream().flatMap(List::stream).noneMatch(name ->
                    normalize(name).equals(normalize(recipient.nameArabic())))) return true;
        }
        boolean ink = plan.explanationInkPages().stream().anyMatch(page -> !page.isEmpty());
        String typed = plan.presidentDirections();
        return ink ? !typed.isBlank() : wrap(typed,signed ? 33 : 56).size() > 3;
    }

    private static void appendHandwrittenExplanation(PDDocument document,
            List<List<PresidentInkReview.Stroke>> pages) throws IOException {
        for (List<PresidentInkReview.Stroke> strokes : pages) {
            if (strokes.isEmpty()) continue;
            // Append every handwritten page as original 3:1 vector ink, so
            // the thumbnail in the Word form never becomes the only copy.
            PDPage page = new PDPage(new PDRectangle(900f,300f));
            document.addPage(page);
            try (PDPageContentStream canvas = new PDPageContentStream(document,page)) {
                PdfBoxPresidentPdfRenderer.draw(canvas,page.getCropBox(),strokes);
            }
        }
    }

    private static void appendAnnex(PDDocument document, CorrespondenceDetails source,
            OfficeDistributionPlan plan, List<DepartmentDirectoryEntry> to,
            List<DepartmentDirectoryEntry> copy, OfficeActionSheetPrintContext printContext) throws IOException {
        var lines = new java.util.ArrayList<String>();
        lines.add("المصرف العربي للتنمية الاقتصادية في أفريقيا - مكتب الرئيس");
        lines.add("ملحق ورقة الإجراء الرسمية");
        lines.add("الموضوع: " + source.subject());
        lines.add("الجهة: " + sourceValue(source));
        if (!printedRoundAttachments(source).isEmpty()) {
            lines.add("مرفقات الجولة " + source.currentWorkflowCycle() + ":");
            for (CorrespondenceDocument attachment : printedRoundAttachments(source)) {
                lines.add("• " + attachment.filename());
            }
        }
        lines.add("رقم الوثيقة: " + Objects.toString(source.documentNumber(),""));
        lines.add("تاريخ إعداد ورقة الإجراء: " + printContext.generatedAtLabel());
        lines.add("إلى:");
        for (var recipient : to) {
            lines.add("• " + recipientDisplay(recipient, printContext)
                    + " - " + recipientName(recipient));
        }
        lines.add("صورة مع التحية:");
        for (var recipient : copy) lines.add("• " + recipientName(recipient));
        lines.add("جهة إضافية: " + plan.otherRecipient());
        lines.add("الإجراء المطلوب:");
        for (var action : plan.actionCodes()) lines.add("• " + ACTION_NAMES.getOrDefault(action,action));
        lines.add("إجراء إضافي: " + plan.otherAction());
        lines.add("تعليمات الرئيس:");
        lines.addAll(wrap(plan.presidentDirections(),70));
        lines.add("استكمال مكتب الرئيس:");
        lines.addAll(wrap(plan.officeDirections(),70));
        lines.add("ملاحظات إدارية:");
        lines.addAll(wrap(plan.administrativeNotes(),70));
        float pageHeight = PDRectangle.A4.getHeight();
        PDPage page = null;
        PDPageContentStream canvas = null;
        float y = 0;
        try {
            for (String line : lines) {
                if (page == null || y > pageHeight-50) {
                    if (canvas != null) canvas.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    canvas = new PDPageContentStream(document,page);
                    y = 45;
                }
                // Do not truncate fields. Extremely long tokens wrap into further lines.
                for (String fragment : wrap(line,65)) {
                    if (y > pageHeight-50) {
                        canvas.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        canvas = new PDPageContentStream(document,page);
                        y=45;
                    }
                    outline(canvas,fragment,550,y,11,pageHeight,true);
                    y+=20;
                }
            }
        } finally { if (canvas != null) canvas.close(); }
    }

    static String recipientHeader(List<DepartmentDirectoryEntry> to,
            OfficeActionSheetPrintContext printContext) {
        return to.stream().map(recipient -> recipientDisplay(recipient, printContext))
                .collect(java.util.stream.Collectors.joining("، "));
    }

    private static String recipientDisplay(DepartmentDirectoryEntry recipient,
            OfficeActionSheetPrintContext context) {
        // The official action sheet addresses the head of the department by title.
        // Personal manager names are deliberately never printed here.
        String department = recipientName(recipient);
        // Not every addressed unit is formally a department headed by a Director.
        return department.startsWith("إدارة ") ? "مدير " + department : "المسؤول عن " + department;
    }

    // The metadata row has separate, non-overlapping spaces for source and
    // document number. Long values move to the existing annex instead of spilling.
    private static final float SOURCE_FIELD_WIDTH = 210f;
    private static final float SOURCE_FIELD_FONT_MAX = 9f;
    private static final float SOURCE_FIELD_FONT_MIN = 8f;
    private static final String SOURCE_FIELD_ANNEX = "في الملحق";

    private record SourceFieldLayout(List<String> lines, float fontSize, boolean annex) {}

    private static SourceFieldLayout layoutSourceField(String text) {
        String value = text == null ? "" : text.strip();
        if (value.isEmpty()) return new SourceFieldLayout(List.of(""),SOURCE_FIELD_FONT_MAX,false);
        float width = textWidth(value,SOURCE_FIELD_FONT_MAX);
        if (width <= SOURCE_FIELD_WIDTH)
            return new SourceFieldLayout(List.of(value),SOURCE_FIELD_FONT_MAX,false);
        float size = SOURCE_FIELD_FONT_MAX * (SOURCE_FIELD_WIDTH - 2f) / width;
        if (size >= SOURCE_FIELD_FONT_MIN)
            return new SourceFieldLayout(List.of(value),size,false);
        return new SourceFieldLayout(List.of(SOURCE_FIELD_ANNEX),SOURCE_FIELD_FONT_MAX,true);
    }

    static boolean needsSourceAnnex(String text) {
        return layoutSourceField(text).annex();
    }

    static String fitSourceField(String text) {
        return needsSourceAnnex(text) ? SOURCE_FIELD_ANNEX : text;
    }

    static float sourceFieldFontSize(String text) {
        return layoutSourceField(text).fontSize();
    }

    static List<String> sourceFieldLines(String text) {
        return layoutSourceField(text).lines();
    }

    private static String fitHeader(String text, float size, float maxWidth) {
        return textWidth(text, size) > maxWidth ? "التفاصيل في الملحق" : text;
    }

    private static float textWidth(String text, float size) {
        if (text == null || text.isBlank()) return 0f;
        return new TextLayout(text, FONT.deriveFont(size), FRC).getAdvance();
    }

    private static String recipientName(DepartmentDirectoryEntry entry) {
        return entry.nameArabic() == null ? entry.nameEnglish() : entry.nameArabic();
    }
    private static List<String> wrap(String text,int length) {
        if (text == null || text.isBlank()) return List.of("");
        String normalized = text.replace("\r","");
        var rows = new java.util.ArrayList<String>();
        for (String paragraph : normalized.split("\n",-1)) {
            if (paragraph.isBlank()) {rows.add("");continue;}
            String[] words=paragraph.split("\\s+");
            StringBuilder row = new StringBuilder();
            for (String word : words) {
                if (word.length() > length) {
                    if (!row.isEmpty()) { rows.add(row.toString()); row.setLength(0); }
                    for (int i=0;i<word.length();i+=length) rows.add(word.substring(i,Math.min(word.length(),i+length)));
                } else if (row.length()+word.length()+1>length) {
                    rows.add(row.toString()); row = new StringBuilder(word);
                } else { if (!row.isEmpty()) row.append(' ');row.append(word); }
            }
            if (!row.isEmpty()) rows.add(row.toString());
        }
        return List.copyOf(rows);
    }
    private static String normalize(String name) {
        if (name == null) return "";
        return Normalizer.normalize(name, Normalizer.Form.NFKD)
                .replaceAll("[\\u064B-\\u065F\\u0670\\u0640]","")
                .replaceAll("[اأإآ]","ا")
                .replaceAll("\\s+","").replace("–","").replace("-","").trim();
    }
    private static void drawDepartmentTable(PDPageContentStream canvas, float pageHeight,
            List<DepartmentDirectoryEntry> selected) throws IOException {
        float width = DEPARTMENT_TABLE_RIGHT - DEPARTMENT_TABLE_LEFT;
        float columnWidth = width / DEPARTMENT_COLUMNS.size();
        float bottom = DEPARTMENT_TABLE_TOP + DEPARTMENT_TABLE_HEADING_HEIGHT
                + 7f * DEPARTMENT_TABLE_ROW_HEIGHT;
        // The source template already has overprinted labels at the upper edge,
        // and a right border outside the useful grid. Clear the entire region,
        // not only the interior of the replacement table.
        erase(canvas,pageHeight,DEPARTMENT_TABLE_LEFT-2,192.2f,
                575f,bottom-192.2f+2f);
        canvas.setStrokingColor(0.30f,0.34f,0.34f);
        canvas.setLineWidth(0.55f);
        canvas.addRect(DEPARTMENT_TABLE_LEFT,pageHeight-bottom,width,bottom-DEPARTMENT_TABLE_TOP);
        for (int col = 1; col < DEPARTMENT_COLUMNS.size(); col++) {
            float x = DEPARTMENT_TABLE_LEFT + col * columnWidth;
            canvas.moveTo(x,pageHeight-(DEPARTMENT_TABLE_TOP+DEPARTMENT_TABLE_HEADING_HEIGHT));
            canvas.lineTo(x,pageHeight-bottom);
        }
        for (int row = 0; row <= 7; row++) {
            float y = DEPARTMENT_TABLE_TOP + DEPARTMENT_TABLE_HEADING_HEIGHT
                    + row * DEPARTMENT_TABLE_ROW_HEIGHT;
            canvas.moveTo(DEPARTMENT_TABLE_LEFT,pageHeight-y);
            canvas.lineTo(DEPARTMENT_TABLE_RIGHT,pageHeight-y);
        }
        canvas.stroke();
        canvas.setStrokingColor(0f,0f,0f);
        String heading = "مكتب الرئيس";
        outline(canvas,heading,(DEPARTMENT_TABLE_LEFT+DEPARTMENT_TABLE_RIGHT
                + textWidth(heading,10))/2f,DEPARTMENT_TABLE_TOP+13,10,pageHeight,true);
        for (int col = 0; col < DEPARTMENT_COLUMNS.size(); col++) {
            List<String> names = DEPARTMENT_COLUMNS.get(col);
            float left = DEPARTMENT_TABLE_LEFT + col*columnWidth;
            float right = left+columnWidth;
            for (int row = 0; row < names.size(); row++) {
                String name = names.get(row);
                float top = DEPARTMENT_TABLE_TOP+DEPARTMENT_TABLE_HEADING_HEIGHT
                        + row*DEPARTMENT_TABLE_ROW_HEIGHT;
                List<String> lines = List.of();
                float size = 0;
                for (float candidate : new float[]{8.2f,7.6f,7f,6.4f,5.8f}) {
                    lines = wrapCell(name,candidate,columnWidth-35f);
                    if (!lines.isEmpty()) { size=candidate; break; }
                }
                if (lines.isEmpty()) throw new IllegalStateException("Action-sheet cell cannot fit: " + name);
                float baseline = top+(lines.size()==1 ? 14f : 9.5f);
                for (String line : lines) {
                    outline(canvas,line,right-7f,baseline,size,pageHeight,true);
                    baseline += 9.5f;
                }
                float boxX=left+6f, boxTop=top+5.5f;
                canvas.setStrokingColor(0.19f,0.31f,0.44f);
                canvas.setLineWidth(0.8f);
                canvas.addRect(boxX,pageHeight-boxTop-11f,11f,11f);
                canvas.stroke();
                if (selected.stream().anyMatch(recipient ->
                        normalize(name).equals(normalize(recipient.nameArabic())))) {
                    canvas.setLineWidth(1.4f);
                    canvas.moveTo(boxX+2,pageHeight-(boxTop+6));
                    canvas.lineTo(boxX+5,pageHeight-(boxTop+9));
                    canvas.lineTo(boxX+10,pageHeight-(boxTop+2));
                    canvas.stroke();
                }
            }
        }
        canvas.setStrokingColor(0f,0f,0f);
    }

    private static List<String> wrapCell(String text,float size,float maxWidth) {
        var lines = new java.util.ArrayList<String>();
        String current="";
        for (String word : text.split("\\s+")) {
            if (textWidth(word,size)>maxWidth) return List.of();
            String next=current.isEmpty() ? word : current+" "+word;
            if (textWidth(next,size)>maxWidth) {
                if (current.isEmpty()) return List.of();
                lines.add(current);
                current=word;
            } else current=next;
        }
        if (!current.isEmpty()) lines.add(current);
        return lines.size()>2 ? List.of() : List.copyOf(lines);
    }

    private static void drawActions(PDPageContentStream canvas, float height,
            List<String> selectedCodes) throws IOException {
        // The preprinted checkboxes and labels are irregularly spaced. Replace
        // only that section, preserving the department grid and explanation below.
        erase(canvas,height,33,375,529,137);
        canvas.saveGraphicsState();
        canvas.setStrokingColor(0.83f,0.86f,0.89f);
        canvas.setLineWidth(0.5f);
        canvas.moveTo(294,height-382);
        canvas.lineTo(294,height-507);
        canvas.stroke();
        canvas.restoreGraphicsState();
        drawActionColumn(canvas,height,RIGHT_ACTION_CODES,selectedCodes,335,542);
        drawActionColumn(canvas,height,LEFT_ACTION_CODES,selectedCodes,46,277);
    }

    private static void drawActionColumn(PDPageContentStream canvas, float height,
            List<String> codes, List<String> selectedCodes, float checkboxX,
            float textRight) throws IOException {
        for (int row=0; row<codes.size(); row++) {
            String code = codes.get(row);
            float baseline = 394f + row*18f;
            String label = ACTION_NAMES.get(code);
            float fontSize = textWidth(label,10f)>196f ? 9f : 10f;
            outline(canvas,label,textRight,baseline,fontSize,height,true);
            drawActionCheckbox(canvas,height,checkboxX,baseline-9f,selectedCodes.contains(code));
        }
    }

    private static void drawActionCheckbox(PDPageContentStream canvas, float height,
            float x, float top, boolean checked) throws IOException {
        float bottom = height-top-11f;
        canvas.saveGraphicsState();
        if (checked) {
            canvas.setNonStrokingColor(0.92f,0.96f,0.98f);
            canvas.addRect(x,bottom,11f,11f);
            canvas.fill();
        }
        canvas.setStrokingColor(0.18f,0.30f,0.39f);
        canvas.setLineWidth(0.85f);
        canvas.addRect(x,bottom,11f,11f);
        canvas.stroke();
        if (checked) {
            canvas.setLineWidth(1.45f);
            canvas.setLineCapStyle(1);
            canvas.setLineJoinStyle(1);
            canvas.moveTo(x+2.0f,bottom+5.2f);
            canvas.lineTo(x+4.6f,bottom+2.6f);
            canvas.lineTo(x+9.0f,bottom+8.6f);
            canvas.stroke();
        }
        canvas.restoreGraphicsState();
    }

    private static void erase(PDPageContentStream canvas,float h,float x,float y,float w,float height) throws IOException {
        canvas.setNonStrokingColor(1f,1f,1f);
        canvas.addRect(x,h-y-height,w,height);
        canvas.fill();
        canvas.setNonStrokingColor(0f,0f,0f);
    }

    private static void outline(PDPageContentStream canvas,String text,float right,float top,float size,
            float pageHeight,boolean alignRight) throws IOException {
        if (text == null || text.isBlank()) return;
        Font font = FONT.deriveFont(size);
        if (font.canDisplayUpTo(text) != -1)
            throw new IllegalStateException("Required Arabic/text glyph unavailable in JVM font");
        TextLayout layout = new TextLayout(text,font,FRC);
        float x = alignRight ? right-layout.getAdvance() : right;
        Shape shape = layout.getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x,top));
        PathIterator it = shape.getPathIterator(null);
        float[] c = new float[6];float prevX=0,prevY=0;
        while (!it.isDone()) {
            int kind=it.currentSegment(c);
            switch (kind) {
                case PathIterator.SEG_MOVETO -> {canvas.moveTo(c[0],pageHeight-c[1]);prevX=c[0];prevY=c[1];}
                case PathIterator.SEG_LINETO -> {canvas.lineTo(c[0],pageHeight-c[1]);prevX=c[0];prevY=c[1];}
                case PathIterator.SEG_QUADTO -> {
                    float sx=prevX,sy=prevY;
                    canvas.curveTo(sx+2f*(c[0]-sx)/3f,pageHeight-(sy+2f*(c[1]-sy)/3f),
                            c[2]+2f*(c[0]-c[2])/3f,pageHeight-(c[3]+2f*(c[1]-c[3])/3f),
                            c[2],pageHeight-c[3]);prevX=c[2];prevY=c[3];
                }
                case PathIterator.SEG_CUBICTO -> {
                    canvas.curveTo(c[0],pageHeight-c[1],c[2],pageHeight-c[3],c[4],pageHeight-c[5]);
                    prevX=c[4];prevY=c[5];
                }
                case PathIterator.SEG_CLOSE -> canvas.closePath();
                default -> throw new IllegalStateException("Invalid glyph geometry");
            }
            it.next();
        }
        canvas.fill();
    }
}
