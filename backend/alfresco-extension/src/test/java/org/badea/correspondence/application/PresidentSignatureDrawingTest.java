package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresidentSignatureDrawingTest {
    private static final String GOOD = """
        {"schemaVersion":1,"strokes":[[[0.05,0.25],[0.23,0.72],[0.55,0.19],[0.90,0.75]]]}""";

    @Test void validatedStrokesRoundTripWithoutChangingGeometry() {
        var drawing = PresidentSignatureDrawing.fromJson(GOOD);
        assertEquals(drawing, PresidentSignatureDrawing.fromJson(drawing.toJson()));
        assertEquals(4, drawing.strokes().getFirst().size());
    }

    @Test void rejectsInvalidOrOutOfRangeCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> PresidentSignatureDrawing.fromJson(
                "{\"schemaVersion\":1,\"strokes\":[[[0,0],[2,0],[0.5,0.5],[0.6,0.8]]]}"));
        assertThrows(IllegalArgumentException.class, () -> PresidentSignatureDrawing.fromJson(
                "{\"schemaVersion\":1,\"strokes\":[[[0,0],[0,0]]]}"));
    }

    @Test void rejectsUnknownSchemaAndLargePayload() {
        assertThrows(IllegalArgumentException.class, () -> PresidentSignatureDrawing.fromJson(
                GOOD.replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
        assertThrows(IllegalArgumentException.class, () -> PresidentSignatureDrawing.fromJson("x".repeat(75001)));
    }

    @Test void defensiveCopiesPreventEditingARegisteredDrawingInMemory() {
        var source = new java.util.ArrayList<PresidentSignatureDrawing.Point>(List.of(
                new PresidentSignatureDrawing.Point(.1f,.2f),
                new PresidentSignatureDrawing.Point(.2f,.4f),
                new PresidentSignatureDrawing.Point(.6f,.3f),
                new PresidentSignatureDrawing.Point(.8f,.7f)));
        var drawing = new PresidentSignatureDrawing(List.of(source));
        source.clear();
        assertEquals(4, drawing.strokes().getFirst().size());
        assertThrows(UnsupportedOperationException.class, () -> drawing.strokes().getFirst().clear());
    }
}
