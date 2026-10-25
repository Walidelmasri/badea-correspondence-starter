package org.badea.correspondence.infrastructure.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

/**
 * Distribution-only derivative: never edits the source PDF bytes. The signed-off
 * action sheet precedes the best available annotated version of each source PDF.
 */
public final class PdfBoxCorrespondencePacketBuilder {
    private static final long MAX_TOTAL_INPUT_BYTES = 256L * 1024L * 1024L;
    private static final PDType1Font FOOTER_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    public byte[] build(String reference, byte[] officialActionSheet, List<byte[]> documents) {
        if (reference == null || !reference.matches("[0-9]+/[0-9]{8}/[1-9][0-9]*")) {
            throw new IllegalArgumentException("Valid assigned correspondence reference is required");
        }
        Objects.requireNonNull(officialActionSheet, "officialActionSheet");
        Objects.requireNonNull(documents, "documents");
        List<byte[]> parts = new ArrayList<>();
        parts.add(officialActionSheet);
        parts.addAll(documents);
        long total = 0L;
        for (byte[] bytes : parts) {
            if (bytes == null || bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P'
                    || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') {
                throw new IllegalArgumentException("Each source must be a PDF");
            }
            total += bytes.length;
            if (total > MAX_TOTAL_INPUT_BYTES)
                throw new IllegalArgumentException("Combined PDF source size exceeds safe generation limit");
        }
        try (PDDocument combined = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (byte[] bytes : parts) {
                try (PDDocument source = Loader.loadPDF(bytes)) {
                    if (source.getNumberOfPages() == 0)
                        throw new IllegalArgumentException("Empty PDF is not a valid packet part");
                    merger.appendDocument(combined, source);
                }
            }
            for (PDPage page : combined.getPages()) stamp(page, combined, reference);
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            combined.save(result);
            return result.toByteArray();
        } catch (IOException problem) {
            throw new IllegalStateException("Correspondence distribution packet could not be created", problem);
        }
    }

    private static void stamp(PDPage page, PDDocument pdf, String reference) throws IOException {
        float width = page.getCropBox().getWidth();
        float height = page.getCropBox().getHeight();
        float left = page.getCropBox().getLowerLeftX();
        float bottom = page.getCropBox().getLowerLeftY();
        float textWidth = FOOTER_FONT.getStringWidth(reference) / 1000f * 9f;
        int rotation = Math.floorMod(page.getRotation(), 360);
        Matrix transform = switch (rotation) {
            case 0 -> Matrix.getTranslateInstance(left + (width-textWidth)/2f, bottom + 13f);
            case 90 -> Matrix.getRotateInstance((float)(Math.PI/2),
                    left + width - 13f, bottom + (height-textWidth)/2f);
            case 180 -> Matrix.getRotateInstance((float)Math.PI,
                    left + (width+textWidth)/2f, bottom + height - 13f);
            case 270 -> Matrix.getRotateInstance((float)(3*Math.PI/2),
                    left + 13f, bottom + (height+textWidth)/2f);
            default -> throw new IllegalArgumentException("Unsupported PDF page rotation");
        };
        try (PDPageContentStream canvas = new PDPageContentStream(pdf, page,
                PDPageContentStream.AppendMode.APPEND, true, true)) {
            canvas.beginText();
            canvas.setFont(FOOTER_FONT, 9f);
            canvas.setTextMatrix(transform);
            canvas.showText(reference);
            canvas.endText();
        }
    }
}
