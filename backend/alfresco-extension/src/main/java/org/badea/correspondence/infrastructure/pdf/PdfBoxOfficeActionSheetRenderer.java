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
import java.util.HashMap;
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
    private static final Map<String, float[]> DEPARTMENTS = Map.ofEntries(
        entry("إدارة الشؤون القانونية",28,249), entry("إدارة الشؤون الإدارية والخدمات اللوجستية",28,268),
        entry("إدارة التحول الرقمي",28,291), entry("إدارة العمليات المالية والمصرفية",28,305),
        entry("إدارة الموارد البشرية",28,327), entry("نائب رئيس المصرف لقطاع الموارد المؤسسية",28,239),
        entry("مكتب الشراكات التنموية",215,223), entry("مستشارو رئيس المصرف",215,251),
        entry("المكاتب الإقليمية",215,267), entry("الاتصال والإعلام",215,292),
        entry("إدارة الاستراتيجية",215,307), entry("إدارة المخاطر",217,341),
        entry("نائب رئيس المصرف لقطاع العمليات",354,231),
        entry("إدارة العمليات قطاع عام",354,250),
        entry("إدارة العمليات قطاع خاص وتمويل التجارة",354,269),
        entry("إدارة الخزانة والاستثمار",354,293),
        entry("مكتب تقييم فعالية التنمية",34,359), entry("مكتب الامتثال والنزاهة",132,359),
        entry("إدارة التدقيق الداخلي",354,358), entry("أمانة مجلسي المحافظين والإدارة واللجان",458,358));
    private static final Map<String,float[]> ACTIONS = Map.ofEntries(
        entry("required-action",335,395), entry("review-and-advise",335,412),
        entry("direct-response",335,429), entry("information-and-action",335,448),
        entry("information-and-file",335,467), entry("prepare-response",335,484),
        entry("return-later",335,503), entry("study-and-opinion",45,395),
        entry("discuss-directly",45,413), entry("prepare-summary",45,430),
        entry("follow-up",45,448), entry("act-per-phone-call",45,465),
        entry("apology",45,484));
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

    private static Map.Entry<String,float[]> entry(String name,float x,float y) {
        return Map.entry(name,new float[]{x,y});
    }

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
                    // The printed department/country dotted field is only x=301..384.
                    // Clear these dots before writing the value, otherwise vector glyphs
                    // overlay the template dots/labels and spill into adjacent fields.
                    erase(canvas,h,301,161,85,16);
                    // Keep the official "إلى السيد/" label and address department heads by title.
                    // An overlong recipient list is detailed on the annex, not written over the table.
                    String allNames = recipientHeader(to, printContext);
                    String header = allNames;
                    if (textWidth(header, 10) > 395) {
                        header = "الأسماء كاملة في الملحق";
                    }
                    outline(canvas,header,475,131,10,h,true);
                    outline(canvas,fitHeader(source.subject(),10,430),485,158,10,h,true);
                    outline(canvas,fitHeader(source.documentNumber(),9,120),471,173,9,h,true);
                    // Source/country is on the RIGHT of its printed caption, not
                    // across the caption or into the date/number columns.
                    String visibleSource = fitSourceField(source.sourceText());
                    outline(canvas,visibleSource,382,173,sourceFieldFontSize(visibleSource),h,true);
                    for (DepartmentDirectoryEntry recipient : to) {
                        String key = normalize(recipient.nameArabic());
                        for (var marker : DEPARTMENTS.entrySet()) {
                            if (normalize(marker.getKey()).equals(key)) mark(canvas,marker.getValue(),h);
                        }
                    }
                    for (String code : plan.actionCodes()) {
                        float[] box = ACTIONS.get(code);
                        if (box != null) mark(canvas,box,h);
                    }
                    // The Word form already provides three "Copy with regards"
                    // lines. Use them before producing any continuation page.
                    for (int i = 0; i < Math.min(3, copy.size()); i++) {
                        outline(canvas, recipientName(copy.get(i)), 520, 687 + 17*i, 9, h, true);
                    }
                    // Replace the template's dotted signature/date placeholders with
                    // clean blank signing lines. A real signature and its actual signing
                    // timestamp require an authenticated signing event; never fabricate
                    // a signature or reuse the PDF generation date as the signing date.
                    cleanSigningLines(canvas,h);
                    // Write directions first: this renderer clears the printed شرح area.
                    // Stamping before it would erase the upper half of a saved signature.
                    fillExplanation(canvas,h,plan,printContext.presidentSignature() != null);
                    if (printContext.presidentSignature() != null) {
                        drawRegisteredSignature(canvas,h,printContext.presidentSignature());
                        // This is the issuance date of the automatically stamped action sheet,
                        // not a claim that the President manually signed it on this date.
                        outline(canvas,printContext.generatedOn().format(DATE),540,615,9,h,true);
                    }
                    // Distinct from the document's original date above; this is when
                    // the Office generated the immutable action-sheet artifact.
                    outline(canvas,"تاريخ إعداد ورقة الإجراء: " + printContext.generatedOn().format(DATE),
                            530,786,9,h,true);
                }
                // The official form is a single cover page when its actual fields fit.
                // Additional pages are produced only for real overflow or material
                // that is not represented on the printed cover, never unconditionally.
                if (requiresContinuation(plan, to, copy, printContext.presidentSignature() != null)
                        || textWidth(recipientHeader(to, printContext), 10) > 395
                        || textWidth(source.subject(), 10) > 430
                        || textWidth(source.documentNumber(), 9) > 120
                        || needsSourceAnnex(source.sourceText())) {
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
        // Preserve the original labels: التوقيع at x=509..544 (y=586..601),
        // التاريخ at x=380..418 (y=602..618).
        erase(canvas,pageHeight,341,583,167,19);
        erase(canvas,pageHeight,420,600,126,19);
        canvas.setStrokingColor(0.28f,0.32f,0.32f);
        canvas.setLineWidth(0.65f);
        canvas.moveTo(349,pageHeight-600);
        canvas.lineTo(496,pageHeight-600);
        canvas.moveTo(423,pageHeight-617);
        canvas.lineTo(541,pageHeight-617);
        canvas.stroke();
        canvas.setStrokingColor(0f,0f,0f);
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

    private static boolean requiresContinuation(OfficeDistributionPlan plan,
            List<DepartmentDirectoryEntry> to, List<DepartmentDirectoryEntry> copy, boolean signed) {
        if (copy.size() > 3 || copy.stream().anyMatch(entry -> recipientName(entry).length() > 60)
                || !plan.officeDirections().isBlank()
                || !plan.administrativeNotes().isBlank()
                || !plan.otherRecipient().isBlank() || !plan.otherAction().isBlank()) return true;
        // The old annex preserved names not represented in the printed checkboxes.
        for (DepartmentDirectoryEntry recipient : to) {
            if (!DEPARTMENTS.keySet().stream().anyMatch(name ->
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
        lines.add("الجهة: " + Objects.toString(source.sourceText(), ""));
        lines.add("رقم الوثيقة: " + Objects.toString(source.documentNumber(),""));
        lines.add("تاريخ إعداد ورقة الإجراء: " + printContext.generatedOn().format(DATE));
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

    // The original form allocates just 85pt to the source/country value.
    // A slightly smaller font keeps normal department names on the cover,
    // without extending into the neighbouring captions or date/table.
    private static final float SOURCE_FIELD_WIDTH = 78f;
    private static final float SOURCE_FIELD_FONT_MAX = 9f;
    private static final float SOURCE_FIELD_FONT_MIN = 7f;

    static boolean needsSourceAnnex(String text) {
        return textWidth(text, SOURCE_FIELD_FONT_MIN) > SOURCE_FIELD_WIDTH;
    }

    static String fitSourceField(String text) {
        return needsSourceAnnex(text) ? "في الملحق" : text;
    }

    static float sourceFieldFontSize(String text) {
        float width = textWidth(text, SOURCE_FIELD_FONT_MAX);
        if (width <= SOURCE_FIELD_WIDTH) return SOURCE_FIELD_FONT_MAX;
        return Math.max(SOURCE_FIELD_FONT_MIN,
                SOURCE_FIELD_FONT_MAX * SOURCE_FIELD_WIDTH / width);
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
    private static void mark(PDPageContentStream canvas,float[] p,float pageHeight) throws IOException {
        float x=p[0],y=pageHeight-p[1];
        canvas.setLineWidth(1.5f);
        // All three vertices sit within the printed checkbox boundaries.
        // Coordinates in DEPARTMENTS/ACTIONS are at the printed checkbox's
        // middle-left. Keep all three check vertices inside that 11-14pt box;
        // the previous final vertex extended above some original blue borders.
        canvas.moveTo(x+3,y-1);canvas.lineTo(x+8,y-4);
        canvas.lineTo(x+15,y+1);
        canvas.stroke();
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
