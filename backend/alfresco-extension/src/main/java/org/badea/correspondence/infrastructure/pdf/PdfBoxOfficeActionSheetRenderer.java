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
            List<DepartmentDirectoryEntry> to, List<DepartmentDirectoryEntry> copy) {
        Objects.requireNonNull(source); Objects.requireNonNull(plan);
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
                    // The supplied Word template has a hard-coded 2026 date: remove it without
                    // changing the original source, then fill the correspondence's actual date.
                    erase(canvas,h,397,176,79,17);
                    String date = source.documentDate() == null ? "" : source.documentDate().format(DATE);
                    outline(canvas,date,471,186,11,h,true);
                    outline(canvas,source.subject(),485,158,10,h,true);
                    outline(canvas,source.documentNumber(),471,173,9,h,true);
                    outline(canvas,source.sourceText(),380,172,9,h,true);
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
                    // Write directions and the President's actual vector handwriting
                    // within the original form's الشرح area. Only genuine overflow
                    // is handled by additional official-document pages.
                    fillExplanation(canvas,h,plan);
                }
                // The official form is a single cover page when its actual fields fit.
                // Additional pages are produced only for real overflow or material
                // that is not represented on the printed cover, never unconditionally.
                if (requiresContinuation(plan, to, copy)) {
                    appendAnnex(document,source,plan,to,copy);
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
    private static void fillExplanation(PDPageContentStream canvas, float pageHeight,
            OfficeDistributionPlan plan) throws IOException {
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
                outline(canvas,"شرح إضافي مكتوب في الملحق",484,579,8,pageHeight,true);
            }
        } else {
            // Respect the limited first-page field; the annex contains every
            // character of the typed text, with no silent truncation.
            List<String> lines = wrap(typed,56);
            for (int i = 0; i < Math.min(3,lines.size()); i++) {
                outline(canvas,lines.get(i),483,533+19*i,10,pageHeight,true);
            }
            if (lines.size()>3) outline(canvas,"التتمة في الملحق",480,583,8,pageHeight,true);
        }
    }

    private static boolean requiresContinuation(OfficeDistributionPlan plan,
            List<DepartmentDirectoryEntry> to, List<DepartmentDirectoryEntry> copy) {
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
        return ink ? !typed.isBlank() : wrap(typed,56).size() > 3;
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
            List<DepartmentDirectoryEntry> copy) throws IOException {
        var lines = new java.util.ArrayList<String>();
        lines.add("المصرف العربي للتنمية الاقتصادية في أفريقيا - مكتب الرئيس");
        lines.add("ملحق ورقة الإجراء الرسمية");
        lines.add("الموضوع: " + source.subject());
        lines.add("رقم الوثيقة: " + Objects.toString(source.documentNumber(),""));
        lines.add("إلى:");
        for (var recipient : to) lines.add("• " + recipientName(recipient));
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
        canvas.moveTo(x+3,y);canvas.lineTo(x+8,y-5);
        canvas.moveTo(x+8,y-5);canvas.lineTo(x+15,y+4);
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
