package org.badea.correspondence.infrastructure.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.blend.BlendMode;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.badea.correspondence.application.PresidentInkReview;
import org.badea.correspondence.application.PresidentPdfRenderer;

/** Vector strokes in unrotated PDF crop-box coordinates; source pages stay vector PDFs. */
public final class PdfBoxPresidentPdfRenderer implements PresidentPdfRenderer {
    // The handwritten explanation canvas is 3:1; retain that aspect ratio.
    private static final PDRectangle EXPLANATION_SIZE = new PDRectangle(900, 300);

    @Override
    public byte[] annotate(byte[] originalPdf, List<PresidentInkReview.Stroke> strokes) {
        try (PDDocument document = Loader.loadPDF(originalPdf)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Encrypted PDF cannot be finalized");
            }
            for (PresidentInkReview.Stroke stroke : strokes) {
                if (stroke.page() > document.getNumberOfPages()) {
                    throw new IllegalArgumentException("Ink stroke references a nonexistent PDF page");
                }
            }
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                List<PresidentInkReview.Stroke> pageStrokes = new ArrayList<>();
                for (PresidentInkReview.Stroke stroke : strokes) {
                    if (stroke.page() == i + 1) pageStrokes.add(stroke);
                }
                if (!pageStrokes.isEmpty()) {
                    PDPage page = document.getPage(i);
                    try (PDPageContentStream canvas = new PDPageContentStream(document, page,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        draw(canvas, page.getCropBox(), pageStrokes);
                    }
                }
            }
            return save(document);
        } catch (IOException exception) {
            throw new IllegalStateException("PDF finalization failed", exception);
        }
    }

    @Override
    public byte[] explanation(List<List<PresidentInkReview.Stroke>> pages) {
        try (PDDocument document = new PDDocument()) {
            for (List<PresidentInkReview.Stroke> strokes : pages) {
                PDPage page = new PDPage(EXPLANATION_SIZE);
                document.addPage(page);
                try (PDPageContentStream canvas = new PDPageContentStream(document, page)) {
                    draw(canvas, page.getCropBox(), strokes);
                }
            }
            return save(document);
        } catch (IOException exception) {
            throw new IllegalStateException("Handwritten explanation finalization failed", exception);
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output);
        return output.toByteArray();
    }

    private static void draw(PDPageContentStream canvas, PDRectangle crop,
            List<PresidentInkReview.Stroke> strokes) throws IOException {
        float width = crop.getWidth();
        float height = crop.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid PDF crop box");
        }
        float scale = Math.min(width, height);
        for (PresidentInkReview.Stroke stroke : strokes) {
            canvas.saveGraphicsState();
            try {
                canvas.setLineCapStyle(1);
                canvas.setLineJoinStyle(1);
                if ("highlighter".equals(stroke.tool())) {
                    canvas.setStrokingColor(248f / 255f, 204f / 255f, 70f / 255f);
                    canvas.setLineWidth(Math.max(8f, scale * 0.018f));
                    PDExtendedGraphicsState state = new PDExtendedGraphicsState();
                    state.setStrokingAlphaConstant(0.28f);
                    state.setBlendMode(BlendMode.MULTIPLY);
                    canvas.setGraphicsStateParameters(state);
                } else {
                    canvas.setStrokingColor(24f / 255f, 54f / 255f, 48f / 255f);
                    canvas.setLineWidth(Math.max(1.5f, scale * 0.0028f));
                }
                List<PresidentInkReview.Point> points = stroke.points();
                PresidentInkReview.Point first = points.getFirst();
                move(canvas, crop, first);
                if (points.size() == 1) {
                    canvas.lineTo(crop.getLowerLeftX() + first.x() * width + 0.01f,
                            crop.getUpperRightY() - first.y() * height + 0.01f);
                } else {
                    float x = crop.getLowerLeftX() + first.x() * width;
                    float y = crop.getUpperRightY() - first.y() * height;
                    for (int i = 1; i < points.size() - 1; i++) {
                        PresidentInkReview.Point control = points.get(i);
                        PresidentInkReview.Point next = points.get(i + 1);
                        float cx = crop.getLowerLeftX() + control.x() * width;
                        float cy = crop.getUpperRightY() - control.y() * height;
                        float nx = crop.getLowerLeftX() + (control.x() + next.x()) * width / 2f;
                        float ny = crop.getUpperRightY() - (control.y() + next.y()) * height / 2f;
                        // Canvas quadraticCurveTo is converted exactly to a PDF cubic Bezier.
                        canvas.curveTo(x + 2f * (cx - x) / 3f, y + 2f * (cy - y) / 3f,
                                nx + 2f * (cx - nx) / 3f, ny + 2f * (cy - ny) / 3f, nx, ny);
                        x = nx;
                        y = ny;
                    }
                    PresidentInkReview.Point last = points.getLast();
                    canvas.lineTo(crop.getLowerLeftX() + last.x() * width,
                            crop.getUpperRightY() - last.y() * height);
                }
                canvas.stroke();
            } finally {
                canvas.restoreGraphicsState();
            }
        }
    }

    private static void move(PDPageContentStream canvas, PDRectangle crop,
            PresidentInkReview.Point point) throws IOException {
        canvas.moveTo(crop.getLowerLeftX() + point.x() * crop.getWidth(),
                crop.getUpperRightY() - point.y() * crop.getHeight());
    }
}
