package org.badea.correspondence.infrastructure.pdf;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfBoxCorrespondencePacketBuilderTest {
    private static byte[] blankPdf() throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            pdf.save(buffer);
            return buffer.toByteArray();
        }
    }

    @Test void combinesCoverAndSourcesAndStampsEveryPageWithoutModifyingInputs() throws Exception {
        byte[] cover = blankPdf();
        byte[] annotated = blankPdf();
        byte[] original = blankPdf();
        byte[] before = Arrays.copyOf(annotated, annotated.length);
        byte[] packet = new PdfBoxCorrespondencePacketBuilder().build(
                "117/20261025/1", cover, List.of(annotated, original));
        assertArrayEquals(before, annotated, "Input PDFs must remain byte-for-byte untouched");
        try (PDDocument pdf = Loader.loadPDF(packet)) {
            assertEquals(3, pdf.getNumberOfPages());
            PDFTextStripper text = new PDFTextStripper();
            for (int i = 1; i <= 3; i++) {
                text.setStartPage(i);
                text.setEndPage(i);
                assertTrue(text.getText(pdf).contains("117/20261025/1"), "Missing footer on page " + i);
            }
        }
    }

    @Test void referencesAllRotatedPagesAsWell() throws Exception {
        try (PDDocument rotated = new PDDocument()) {
            for (int degrees : new int[] { 0, 90, 180, 270 }) {
                PDPage page = new PDPage(PDRectangle.A4);
                page.setRotation(degrees);
                rotated.addPage(page);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            rotated.save(buffer);
            byte[] merged = new PdfBoxCorrespondencePacketBuilder().build(
                    "117/20261025/1", buffer.toByteArray(), List.of());
            try (PDDocument actual = Loader.loadPDF(merged)) {
                PDFTextStripper stripper = new PDFTextStripper();
                assertEquals(4, actual.getNumberOfPages());
                for (int page = 1; page <= 4; page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    assertTrue(stripper.getText(actual).contains("117/20261025/1"));
                }
            }
        }
    }

    @Test void rejectsMissingOrMalformedReferenceAndSources() throws Exception {
        byte[] cover = blankPdf();
        var builder = new PdfBoxCorrespondencePacketBuilder();
        assertThrows(IllegalArgumentException.class, () -> builder.build("117/20261025/00001", cover, List.of()));
        assertThrows(IllegalArgumentException.class, () -> builder.build("117/20261025/1", cover, List.of("text".getBytes())));
    }
}
