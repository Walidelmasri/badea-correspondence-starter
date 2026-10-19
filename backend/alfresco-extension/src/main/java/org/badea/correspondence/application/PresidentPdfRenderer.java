package org.badea.correspondence.application;

import java.util.List;

/** PDF geometry adapter; no repository, HTTP, or workflow dependencies. */
public interface PresidentPdfRenderer {
    byte[] annotate(byte[] originalPdf, List<PresidentInkReview.Stroke> strokes);
    byte[] explanation(List<List<PresidentInkReview.Stroke>> pages);
}
