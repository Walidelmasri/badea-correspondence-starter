package org.badea.correspondence.webscript;

import org.json.JSONObject;

/** Validates the complete, existing executive Compose form contract. */
final class ExecutiveComposePayload {
    private ExecutiveComposePayload() { }

    /**
     * Normalize all fields in the existing executive Compose form.
     * Storage is structured, and unmapped fields are never discarded.
     * Dispatch recipients are NOT inferred from free-text addresses.
     */
    static String normalize(JSONObject request) {
        if (!request.has("executiveCompose")
                || !(request.opt("executiveCompose") instanceof JSONObject original)) {
            throw new IllegalArgumentException(
                    "executiveCompose must be a JSON object for executive drafts");
        }
        JSONObject normalized = new JSONObject();
        for (String field : new String[] {
                "recipient", "copyTo", "entity", "instruction", "explanation"
        }) {
            Object value = original.opt(field);
            if (value != null && value != JSONObject.NULL && !(value instanceof String)) {
                throw new IllegalArgumentException(
                        "executiveCompose." + field + " must be a string");
            }
            String text = value == null || value == JSONObject.NULL
                    ? "" : ((String) value).trim();
            if (text.length() > 4000) {
                throw new IllegalArgumentException(
                        "executiveCompose." + field + " is too long");
            }
            normalized.put(field, text);
        }
        // Unrecognized fields must not be silently dropped.
        for (String key : original.keySet()) {
            if (!normalized.has(key)) {
                throw new IllegalArgumentException(
                        "Unsupported executiveCompose field: " + key);
            }
        }
        return normalized.toString();
    }

}
