package org.badea.correspondence.infrastructure.mail;

import java.util.Objects;

/** Editable HTML and plain-text BADEA-branded email content. No user HTML is trusted. */
public final class CorrespondenceShareMailTemplate {
    private CorrespondenceShareMailTemplate() {}

    public record Rendered(String subject, String plainText, String html) {}
    public record Item(String filename, String url) {}

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

    public static Rendered renderMultiple(String sender, java.util.List<Item> documents,
            String reference, String message) {
        if (documents == null || documents.isEmpty() || documents.size() > 10) {
            throw new IllegalArgumentException("One to ten documents required.");
        }
        String displaySender = normalize(sender, "BADEA colleague");
        String ref = normalize(reference, "Not assigned");
        String note = message == null ? "" : message.strip();
        StringBuilder plain = new StringBuilder("BADEA Correspondence\n")
                .append(displaySender).append(" has shared documents with you.\n")
                .append("Reference: ").append(ref).append("\n\n");
        StringBuilder htmlList = new StringBuilder();
        for (Item item : documents) {
            if (item == null || item.url() == null || !item.url().startsWith("https://")) {
                throw new IllegalArgumentException("Invalid document link.");
            }
            String filename = normalize(item.filename(), "Document");
            plain.append(filename).append("\n").append(item.url()).append("\n\n");
            htmlList.append("<li style=\"margin:14px 0\"><strong>")
                    .append(escape(filename))
                    .append("</strong><div style=\"margin-top:10px\"><a href=\"")
                    .append(escape(item.url()))
                    .append("\" style=\"display:inline-block;background:#145749;color:white;padding:10px 16px;")
                    .append("text-decoration:none;border-radius:5px\">View document</a></div></li>");
        }
        if (!note.isEmpty()) plain.append("Message: ").append(note).append("\n\n");
        plain.append("Anyone with these links who can reach BADEA's server can open the documents.\n");
        String noteHtml = note.isEmpty() ? "" :
                "<div style=\"padding:14px;background:#f3f8f6;white-space:pre-wrap\">"
                + escape(note) + "</div>";
        String html = "<!doctype html><html><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"margin:0;background:#f3f6f5;font-family:Arial,Tahoma,sans-serif;color:#19352f\">"
                + "<main style=\"max-width:600px;margin:24px auto;background:white;border:1px solid #e0e7e4\">"
                + "<header style=\"padding:22px;background:#145749;color:white;font-size:20px\">BADEA Correspondence</header>"
                + "<section style=\"padding:24px\"><h1 style=\"font-size:20px\">Documents shared with you</h1>"
                + "<p><strong>" + escape(displaySender) + "</strong> shared " + documents.size() + " document(s).</p>"
                + "<p>Reference: " + escape(ref) + "</p>" + noteHtml
                + "<ul style=\"list-style:none;padding:0\">" + htmlList + "</ul></section>"
                + "<footer style=\"padding:16px 24px;background:#f3f6f5;font-size:12px\">"
                + "BADEA internal correspondence. These are public bearer links within the reachable network; do not forward."
                + "</footer></main></body></html>";
        return new Rendered("BADEA Correspondence | Shared documents", plain.toString(), html);
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
