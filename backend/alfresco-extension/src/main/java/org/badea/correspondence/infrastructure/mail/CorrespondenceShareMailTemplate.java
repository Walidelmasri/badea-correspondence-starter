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

    /**
     * Outlook-friendly batch message: one branded card and linked filenames,
     * not a row of independent call-to-action buttons.
     * Each file retains its own native Alfresco bearer URL.
     */
    public static Rendered renderMultiple(String sender, java.util.List<Item> documents,
            String reference, String message) {
        if (documents == null || documents.isEmpty() || documents.size() > 10) {
            throw new IllegalArgumentException("One to ten documents required.");
        }
        String displaySender = normalize(sender, "BADEA colleague");
        String ref = normalize(reference, "Not assigned");
        String note = message == null ? "" : message.strip();
        String count = documents.size() == 1 ? "1 document" : documents.size() + " documents";

        StringBuilder plain = new StringBuilder("BADEA Correspondence\n")
                .append(displaySender).append(" shared ").append(count).append(" with you.\n")
                .append("Reference: ").append(ref).append("\n\n");
        StringBuilder documentRows = new StringBuilder();
        int position = 0;
        for (Item item : documents) {
            if (item == null || item.url() == null || !item.url().startsWith("https://")) {
                throw new IllegalArgumentException("Invalid document link.");
            }
            String filename = normalize(item.filename(), "Document");
            plain.append(filename).append("\n").append(item.url()).append("\n\n");
            documentRows.append("""
                    <tr>
                      <td width="42" valign="top" style="padding:15px 6px 15px 16px;
                          border-bottom:1px solid #e4ece8;color:#71847e;font-size:12px;">%d.</td>
                      <td valign="top" style="padding:15px 16px 15px 0;
                          border-bottom:1px solid #e4ece8;font-size:14px;line-height:21px;
                          overflow-wrap:anywhere;word-break:break-word;">
                        <a href="%s" style="color:#145749;font-weight:700;
                            text-decoration:underline;">%s</a>
                      </td>
                    </tr>
                    """.formatted(++position, escape(item.url()), escape(filename)));
        }
        if (!note.isEmpty()) {
            plain.append("Message from sender:\n").append(note).append("\n\n");
        }
        plain.append("Anyone with these links who can reach BADEA's server can open the documents.\n");

        // Use table cells and inline styles rather than HTML5 structural tags:
        // these render substantially more consistently across Outlook clients.
        String noteHtml = note.isEmpty() ? "" : """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0"
                    width="100%%" style="border-collapse:collapse;margin:0 0 24px 0;">
                  <tr><td style="padding:15px 18px;background-color:#f1f7f4;
                      border-left:3px solid #28776a;">
                    <p style="margin:0 0 9px;color:#61746c;font-size:11px;
                        font-weight:700;letter-spacing:0.6px;">MESSAGE FROM SENDER</p>
                    <p style="margin:0;color:#19352f;font-size:14px;line-height:22px;
                        white-space:pre-wrap;overflow-wrap:anywhere;">%s</p>
                  </td></tr>
                </table>
                """.formatted(escape(note));

        String html = """
                <!doctype html>
                <html><head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background-color:#f3f6f5;
                    color:#19352f;font-family:Arial,Tahoma,sans-serif;">
                  <table role="presentation" cellpadding="0" cellspacing="0" border="0"
                      width="100%%" style="border-collapse:collapse;background-color:#f3f6f5;">
                    <tr><td align="center" style="padding:28px 12px;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0"
                          width="100%%" style="max-width:600px;border:1px solid #dbe6e1;
                          border-collapse:separate;background-color:#ffffff;">
                        <tr><td style="padding:23px 30px;background-color:#145749;
                            color:#ffffff;font-size:20px;font-weight:700;line-height:26px;">
                          BADEA <span style="font-weight:400;">Correspondence</span>
                        </td></tr>
                        <tr><td style="padding:28px 30px 26px;background-color:#ffffff;">
                          <p style="margin:0 0 16px;font-size:21px;line-height:28px;
                              font-weight:700;color:#19352f;">Documents shared with you</p>
                          <p style="margin:0 0 14px;font-size:14px;line-height:23px;color:#19352f;">
                            <strong>%s</strong> shared %s with you.
                          </p>
                          <p style="margin:0 0 23px;color:#657970;font-size:13px;">
                            Reference: <strong>%s</strong>
                          </p>
                          %s
                          <p style="margin:0 0 10px;font-size:13px;font-weight:700;
                              color:#344f45;">SHARED DOCUMENTS</p>
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0"
                              width="100%%" style="border-collapse:collapse;
                              border:1px solid #e4ece8;background-color:#fafcfb;">
                            %s
                          </table>
                          <p style="margin:17px 0 0;color:#71847e;font-size:12px;
                              line-height:18px;">Select a filename to view the document.</p>
                        </td></tr>
                        <tr><td style="padding:18px 30px;background-color:#f3f6f5;
                            color:#657970;font-size:11px;line-height:18px;">
                          BADEA internal correspondence. Anyone holding a link who can
                          reach the BADEA server may open that document. Do not forward.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(escape(displaySender), count, escape(ref), noteHtml, documentRows);
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
