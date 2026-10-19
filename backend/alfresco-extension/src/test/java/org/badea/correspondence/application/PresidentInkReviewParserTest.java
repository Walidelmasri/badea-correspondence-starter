package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PresidentInkReviewParserTest {
    private final PresidentInkReviewParser parser = new PresidentInkReviewParser();

    @Test void acceptsOptionalEmptyReview() {
        var review = parser.parse("{\"schemaVersion\":1}");
        assertTrue(review.documentStrokes().isEmpty());
        assertTrue(review.explanationPages().isEmpty());
    }

    @Test void recognizesSeparatePdfAndExplanationGeometry() {
        var review = parser.parse("""
          {"schemaVersion":1,
           "pdfInkDocuments":[{"documentKey":"node-A", "strokes":[
             {"id":"stroke-1","pageNumber":2,"tool":"highlighter", "points":[
               {"x":0.25,"y":0.75,"pressure":0.5}]}]}],
           "explanationInkDocument":{"schemaVersion":1,"pages":[
             {"id":"page1", "strokes":[]},
             {"id":"page2", "strokes":[{"id":"hand1","tool":"pen",
               "points":[{"x":0.3,"y":0.6,"pressure":0.5}]}]}]}}
          """);
        assertEquals(2, review.documentStrokes().get("node-A").getFirst().page());
        assertEquals("highlighter", review.documentStrokes().get("node-A").getFirst().tool());
        assertEquals(2, review.explanationPages().size());
        assertTrue(review.explanationPages().getFirst().isEmpty());
        assertEquals(1, review.explanationPages().get(1).size());
    }

    @Test void rejectsOutOfRangePointsAndDuplicateDocumentKeys() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
          {"schemaVersion":1,"pdfInkDocuments":[
             {"documentKey":"node-A", "strokes":[
               {"pageNumber":1,"tool":"pen", "points":[{"x":1.5,"y":0.2,"pressure":0.5}]}]}]}
          """));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
          {"schemaVersion":1,"pdfInkDocuments":[
             {"documentKey":"node-A","strokes":[]},
             {"documentKey":"node-A","strokes":[]}]}
          """));
    }
}
