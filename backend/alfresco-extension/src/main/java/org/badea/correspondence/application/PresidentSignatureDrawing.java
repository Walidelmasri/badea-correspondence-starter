package org.badea.correspondence.application;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Validated, normalized pen strokes; never accepts arbitrary PDF commands or scripts. */
public record PresidentSignatureDrawing(List<List<Point>> strokes) {
    public record Point(float x, float y) {
        public Point {
            if (!Float.isFinite(x) || !Float.isFinite(y)
                    || x < 0 || x > 1 || y < 0 || y > 1) {
                throw new IllegalArgumentException("Signature coordinates must be normalized");
            }
        }
    }

    public PresidentSignatureDrawing {
        if (strokes == null || strokes.isEmpty() || strokes.size() > 40) {
            throw new IllegalArgumentException("Signature must have 1 to 40 strokes");
        }
        strokes = strokes.stream().map(List::copyOf).toList();
        int points = 0;
        float left = 1, right = 0, top = 1, bottom = 0;
        for (List<Point> stroke : strokes) {
            if (stroke.size() < 2) throw new IllegalArgumentException("Signature stroke is too short");
            points += stroke.size();
            if (points > 2500) throw new IllegalArgumentException("Signature contains too many points");
            for (Point p : stroke) {
                left = Math.min(left, p.x()); right = Math.max(right, p.x());
                top = Math.min(top, p.y()); bottom = Math.max(bottom, p.y());
            }
        }
        if (points < 4 || right-left < 0.07f || bottom-top < 0.05f) {
            throw new IllegalArgumentException("Signature drawing is too small");
        }
    }

    public static PresidentSignatureDrawing fromJson(String raw) {
        if (raw == null || raw.length() > 75000) {
            throw new IllegalArgumentException("Signature payload is too large");
        }
        try {
            JSONObject json = new JSONObject(raw);
            if (json.getInt("schemaVersion") != 1) throw new IllegalArgumentException("Unknown signature schema");
            JSONArray input = json.getJSONArray("strokes");
            if (input.length() < 1 || input.length() > 40) throw new IllegalArgumentException("Too many signature strokes");
            List<List<Point>> strokes = new ArrayList<>();
            for (int i = 0; i < input.length(); i++) {
                JSONArray points = input.getJSONArray(i);
                if (points.length() < 2 || points.length() > 2500) throw new IllegalArgumentException("Invalid stroke length");
                List<Point> line = new ArrayList<>();
                for (int j = 0; j < points.length(); j++) {
                    JSONArray pair = points.getJSONArray(j);
                    if (pair.length() != 2) throw new IllegalArgumentException("Invalid signature point");
                    line.add(new Point((float) pair.getDouble(0), (float) pair.getDouble(1)));
                }
                strokes.add(List.copyOf(line));
            }
            return new PresidentSignatureDrawing(strokes);
        } catch (org.json.JSONException invalid) {
            throw new IllegalArgumentException("Invalid signature drawing", invalid);
        }
    }

    public String toJson() {
        JSONArray lines = new JSONArray();
        for (List<Point> stroke : strokes) {
            JSONArray points = new JSONArray();
            for (Point p : stroke) points.put(new JSONArray().put(p.x()).put(p.y()));
            lines.put(points);
        }
        return new JSONObject().put("schemaVersion", 1).put("strokes", lines).toString();
    }
}
