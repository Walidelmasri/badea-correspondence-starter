package org.badea.correspondence.infrastructure.mail;

import java.util.Objects;

/** Editable HTML and plain-text BADEA-branded email content. No user HTML is trusted. */
public final class CorrespondenceShareMailTemplate {
    private CorrespondenceShareMailTemplate() {}

    public record Rendered(String subject, String plainText, String html) {}

    public static Rendered render(String sender, String filename, String technicalId,
            String url, String message) {
        Objects.requireNonNull(url);
        String displaySender = normalize(sender, "BADEA colleague");
        String displayFile = normalize(filename, "Correspondence document");
        String displayReference = normalize(technicalId, "");
        String displayMessage = message == null ? "" : message.trim();
        String subject = "BADEA Correspondence | Shared document";
        String plain = "A correspondence document has been shared with you.\n\n"
                + "Shared by: " + displaySender + "\nDocument: " + displayFile
                + "\nReference: " + displayReference + "\n"
                + (displayMessage.isEmpty() ? "" : "\nMessage from sender:\n" + displayMessage + "\n")
                + "\nView document: " + url + "\n\n"
                + "This is an internal BADEA document. The link is accessible to anyone "
                + "who has it and can reach the BADEA server. Do not forward it.\n";
        String note = displayMessage.isEmpty() ? "" :
                "<div style=\"padding:16px;border-left:3px solid #28776a;background:#f3f8f6;" 
                + "margin:18px 0\"><div style=\"font-size:12px;color:#53645f\">"
                + "MESSAGE FROM SENDER</div><p style=\"white-space:pre-wrap\">"
                + escape(displayMessage) + "</p></div>";
        String html = "<!doctype html><html><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"margin:0;background:#f3f6f5;font-family:Arial,Tahoma,sans-serif;color:#19352f\">"
                + "<div style=\"max-width:600px;margin:24px auto;background:white;border:1px solid #e0e7e4\">"
                + "<div style=\"padding:22px 30px;background:#145749;color:white;font-size:19px;font-weight:bold\">"
                + "BADEA <span style=\"font-weight:normal\">Correspondence</span></div>"
                + "<div style=\"padding:28px 30px\"><h1 style=\"font-size:20px\">A document has been shared with you</h1>"
                + "<p><b>" + escape(displaySender) + "</b> shared the following correspondence document:</p>"
                + "<p><b>" + escape(displayFile) + "</b></p>"
                + "<p style=\"color:#53645f;font-size:13px\">Reference: " + escape(displayReference) + "</p>"
                + note
                + "<p style=\"margin:28px 0\"><a href=\"" + escape(url)
                + "\" style=\"background:#145749;color:white;padding:13px 22px;display:inline-block;"
                + "text-decoration:none;border-radius:5px\">View Shared Document</a></p>"
                + "<p style=\"font-size:12px;color:#53645f;overflow-wrap:anywhere\">"
                + "If the button does not work, copy this link:<br><a href=\"" + escape(url)
                + "\">" + escape(url) + "</a></p></div>"
                + "<div style=\"padding:16px 30px;background:#f3f6f5;color:#53645f;font-size:11px\">"
                + "BADEA internal correspondence. Anyone with this link who can reach the server may open it."
                + " Do not forward this email.</div></div></body></html>";
        return new Rendered(subject, plain, html);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
