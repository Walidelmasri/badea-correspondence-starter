package org.badea.correspondence.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validated immutable geometry from the saved, revisioned President review. */
public record PresidentInkReview(
        Map<String, List<Stroke>> documentStrokes,
        List<List<Stroke>> explanationPages) {

    public record Point(float x, float y) {}

    public record Stroke(int page, String tool, List<Point> points) {
        public Stroke {
            if (page < 1 || !("pen".equals(tool) || "highlighter".equals(tool))) {
                throw new IllegalArgumentException("Invalid ink stroke");
            }
            points = List.copyOf(Objects.requireNonNull(points));
            if (points.isEmpty()) throw new IllegalArgumentException("Empty ink stroke");
        }
    }

    public PresidentInkReview {
        documentStrokes = Map.copyOf(documentStrokes);
        explanationPages = List.copyOf(explanationPages);
    }
}
