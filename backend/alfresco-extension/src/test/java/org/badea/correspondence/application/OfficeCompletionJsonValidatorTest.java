package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class OfficeCompletionJsonValidatorTest {
    private static final String VALID = """
            {"schemaVersion":1,"selectedRecipientIds":["117","118"],
            "selectedCopyRecipientIds":[],"selectedActionCodes":["follow-up"],
            "otherRecipient":"","otherAction":"","typedExplanation":"",
            "officeNotes":""}
            """;
    @Test void acceptsMultipleRecipientAdditions() {
        assertNotNull(OfficeCompletionJsonValidator.validate(VALID));
    }
    @Test void rejectsAttemptToReplacePresidentPdfInk() {
        JSONObject attempt=new JSONObject(VALID);
        attempt.put("pdfInkDocuments", new JSONArray());
        assertThrows(IllegalArgumentException.class,
                () -> OfficeCompletionJsonValidator.validate(attempt.toString()));
    }
    @Test void rejectsUnknownActionCode() {
        JSONObject attempt=new JSONObject(VALID);
        attempt.put("selectedActionCodes",new JSONArray().put("authorize-transfer"));
        assertThrows(IllegalArgumentException.class,
                () -> OfficeCompletionJsonValidator.validate(attempt.toString()));
    }
    @Test void rejectsDuplicateRoutingDepartments() {
        JSONObject attempt=new JSONObject(VALID);
        attempt.put("selectedRecipientIds",new JSONArray().put("117").put("117"));
        assertThrows(IllegalArgumentException.class,
                () -> OfficeCompletionJsonValidator.validate(attempt.toString()));
    }
    @Test void rejectsMissingOfficeNotesField() {
        JSONObject attempt=new JSONObject(VALID);
        attempt.remove("officeNotes");
        assertThrows(IllegalArgumentException.class,
                () -> OfficeCompletionJsonValidator.validate(attempt.toString()));
    }
}
