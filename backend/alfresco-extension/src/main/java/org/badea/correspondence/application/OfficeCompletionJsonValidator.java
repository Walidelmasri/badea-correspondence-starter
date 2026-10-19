package org.badea.correspondence.application;

import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Whitelists Office-owned supplemental fields; cannot carry President ink or state. */
public final class OfficeCompletionJsonValidator {
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion", "selectedRecipientIds", "selectedCopyRecipientIds",
            "selectedActionCodes", "otherRecipient", "otherAction",
            "typedExplanation", "officeNotes");
    private static final Set<String> ACTION_CODES = Set.of(
            "required-action", "study-and-opinion", "review-and-advise",
            "discuss-directly", "direct-response", "prepare-summary",
            "information-and-action", "follow-up", "information-and-file",
            "act-per-phone-call", "prepare-response", "apology",
            "return-later", "other");
    private OfficeCompletionJsonValidator() {}

    public static String validate(String raw) {
        if (raw == null || raw.length() > 32768) {
            throw new IllegalArgumentException("Office completion exceeds limit");
        }
        final JSONObject state;
        try {
            state = new JSONObject(raw);
            if (state.optInt("schemaVersion", -1) != 1
                    || !state.has("schemaVersion")
                    || state.length() != FIELDS.size()) {
                throw new IllegalArgumentException("Unsupported Office completion schema");
            }
            for (String key : state.keySet()) {
                if (!FIELDS.contains(key)) {
                    throw new IllegalArgumentException("Unrecognized Office completion field");
                }
            }
            for (String key : new String[]{"selectedRecipientIds", "selectedCopyRecipientIds", "selectedActionCodes"}) {
                JSONArray array = state.getJSONArray(key);
                if (array.length() > 100) throw new IllegalArgumentException("Too many selections");
                Set<String> unique = new java.util.HashSet<>();
                for (int index=0; index<array.length(); index++) {
                    Object entry = array.get(index);
                    if (!(entry instanceof String text) || text.isBlank()
                            || text.length() > 100 || !unique.add(text)
                            || (key.equals("selectedActionCodes") && !ACTION_CODES.contains(text))) {
                        throw new IllegalArgumentException("Invalid or repeated selection: " + key);
                    }
                }
            }
            for (String key : new String[]{"otherRecipient", "otherAction", "typedExplanation", "officeNotes"}) {
                Object value = state.get(key);
                if (!(value instanceof String text)
                        || text.length() > (key.equals("officeNotes") ? 4000 : 2000)) {
                    throw new IllegalArgumentException("Invalid Office text: " + key);
                }
            }
            return state.toString();
        } catch (JSONException ex) {
            throw new IllegalArgumentException("Invalid Office completion JSON",ex);
        }
    }
}
