package org.badea.correspondence.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Never trust unvalidated JSON coordinates when permanently altering a PDF. */
public final class PresidentInkReviewParser {
    private static final int MAX_STROKES = 10_000;
    private static final int MAX_POINTS = 500_000;
    private int strokeCount;
    private int pointCount;

    public PresidentInkReview parse(String json) {
        try {
            strokeCount = 0;
            pointCount = 0;
            JSONObject root = new JSONObject(json);
            if (root.getInt("schemaVersion") != 1) {
                throw new IllegalArgumentException("Unknown President review schema version");
            }
            Map<String, List<PresidentInkReview.Stroke>> documents = new HashMap<>();
            JSONArray pdfs = root.optJSONArray("pdfInkDocuments");
            if (pdfs != null) {
                for (int i = 0; i < pdfs.length(); i++) {
                    JSONObject doc = pdfs.getJSONObject(i);
                    String key = doc.getString("documentKey");
                    if (key.isBlank() || documents.containsKey(key)) {
                        throw new IllegalArgumentException("Duplicate or empty PDF document key");
                    }
                    documents.put(key, strokes(doc.getJSONArray("strokes"), true));
                }
            }
            List<List<PresidentInkReview.Stroke>> explanation = new ArrayList<>();
            JSONObject ink = root.optJSONObject("explanationInkDocument");
            if (ink != null) {
                if (ink.getInt("schemaVersion") != 1) {
                    throw new IllegalArgumentException("Unknown explanation schema version");
                }
                JSONArray pages = ink.getJSONArray("pages");
                if (pages.length() > 100) {
                    throw new IllegalArgumentException("Too many handwritten explanation pages");
                }
                for (int i = 0; i < pages.length(); i++) {
                    explanation.add(strokes(pages.getJSONObject(i).getJSONArray("strokes"), false));
                }
            }
            return new PresidentInkReview(documents, explanation);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid President review ink JSON", exception);
        }
    }

    private List<PresidentInkReview.Stroke> strokes(JSONArray input, boolean numbered) {
        List<PresidentInkReview.Stroke> parsed = new ArrayList<>();
        for (int i = 0; i < input.length(); i++) {
            if (++strokeCount > MAX_STROKES) {
                throw new IllegalArgumentException("Too many annotation strokes");
            }
            JSONObject item = input.getJSONObject(i);
            String tool = item.getString("tool");
            int page = numbered ? item.getInt("pageNumber") : 1;
            JSONArray points = item.getJSONArray("points");
            List<PresidentInkReview.Point> path = new ArrayList<>(points.length());
            for (int j = 0; j < points.length(); j++) {
                if (++pointCount > MAX_POINTS) {
                    throw new IllegalArgumentException("Too many annotation points");
                }
                JSONObject point = points.getJSONObject(j);
                float x = coordinate(point, "x");
                float y = coordinate(point, "y");
                // Pressure is persisted but not used by the existing canvas renderer.
                coordinate(point, "pressure");
                path.add(new PresidentInkReview.Point(x, y));
            }
            parsed.add(new PresidentInkReview.Stroke(page, tool, path));
        }
        return List.copyOf(parsed);
    }

    private static float coordinate(JSONObject value, String name) {
        double number = value.getDouble(name);
        if (!Double.isFinite(number) || number < 0d || number > 1d) {
            throw new IllegalArgumentException("Out-of-range ink coordinate: " + name);
        }
        return (float) number;
    }
}
