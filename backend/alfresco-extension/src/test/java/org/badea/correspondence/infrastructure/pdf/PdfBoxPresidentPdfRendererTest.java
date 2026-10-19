package org.badea.correspondence.infrastructure.pdf;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.badea.correspondence.application.PresidentInkReview;
import org.junit.jupiter.api.Test;

class PdfBoxPresidentPdfRendererTest {
    private final PdfBoxPresidentPdfRenderer renderer = new PdfBoxPresidentPdfRenderer();

    private static PresidentInkReview.Stroke stroke(int page, String tool) {
        return new PresidentInkReview.Stroke(page, tool, List.of(
                new PresidentInkReview.Point(0.1f, 0.2f),
                new PresidentInkReview.Point(0.5f, 0.6f),
                new PresidentInkReview.Point(0.8f, 0.3f)));
    }

    @Test void preservesPageCountAndRotation() throws Exception {
        byte[] source;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            PDPage rotated = new PDPage(PDRectangle.LETTER);
            rotated.setRotation(90);
            doc.addPage(rotated);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            doc.save(output);
            source = output.toByteArray();
        }
        byte[] result = renderer.annotate(source, List.of(stroke(1,"pen"), stroke(2,"highlighter")));
        try (PDDocument updated = Loader.loadPDF(result)) {
            assertEquals(2, updated.getNumberOfPages());
            assertEquals(90, updated.getPage(1).getRotation());
            assertEquals(PDRectangle.A4.getWidth(), updated.getPage(0).getCropBox().getWidth());
            assertNotNull(updated.getPage(0).getContents());
            assertNotNull(updated.getPage(1).getContents());
        }
        assertThrows(IllegalArgumentException.class,
                () -> renderer.annotate(source, List.of(stroke(3,"pen"))));
    }

    @Test void explanationKeepsThreeToOneAspectRatioAndMultiplePages() throws Exception {
        byte[] result = renderer.explanation(List.of(List.of(stroke(1,"pen")),
                List.of(stroke(1,"highlighter"))));
        try (PDDocument doc = Loader.loadPDF(result)) {
            assertEquals(2, doc.getNumberOfPages());
            assertEquals(3f,
                    doc.getPage(0).getCropBox().getWidth() /
                    doc.getPage(0).getCropBox().getHeight());
        }
    }
}
