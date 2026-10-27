package org.badea.correspondence.infrastructure.mail;

import java.net.URI;
import java.util.Objects;

/**
 * Safe, Outlook-compatible incoming-workflow alert. No attachments, raw
 * document text or bearer share links. The recipient must still log in.
 * An HTTPS link is optional until IT provisions the dedicated site address.
 */
public final class CorrespondenceIncomingMailTemplate {
    private CorrespondenceIncomingMailTemplate() { }

    public record Rendered(String subject, String plainText, String html) { }

    public static Rendered render(String reference, String correspondenceSubject,
            String secureInboxUrl) {
        String ref = shorten(reference, 100, "Pending reference");
        String topic = shorten(correspondenceSubject, 180, "New correspondence");
        String url = safeInternalHttps(secureInboxUrl);
        String emailSubject = "BADEA Correspondence | Action required";
        String plain = "مراسلات الرئاسة - المصرف العربي للتنمية الاقتصادية في أفريقيا\n"
                + "لديك مراسلة واردة تتطلب الاطلاع أو الإجراء.\n"
                + "الرقم المرجعي: " + ref + "\nالموضوع: " + topic + "\n\n"
                + "BADEA Presidency Correspondence\n"
                + "You have received correspondence for review or action.\n"
                + "Reference: " + ref + "\nSubject: " + topic + "\n"
                + (url == null ? "Open the BADEA Correspondence application.\n"
                        : "Open Inbox: " + url + "\n")
                + "Sign in with your BADEA account. Do not forward this message.\n";
        String link = url == null
                ? "<p style=\"color:#52675f\">افتح منصة المراسلات الداخلية / Open the internal correspondence application.</p>"
                : "<p style=\"margin:22px 0\"><a href=\"" + escape(url)
                    + "\" style=\"background:#145749;color:white;display:inline-block;padding:12px 22px;"
                    + "text-decoration:none;border-radius:5px\">فتح الوارد / Open Inbox</a></p>";
        String html = "<!doctype html><html><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"background:#f3f6f5;font-family:Arial,Tahoma,sans-serif;color:#19352f\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"600\" style=\"max-width:100%;background:white;border:1px solid #dbe6e1\">"
                + "<tr><td style=\"padding:23px 30px;background:#145749;color:white;font-size:20px;font-weight:bold\">"
                + "BADEA | مراسلات الرئاسة</td></tr>"
                + "<tr><td style=\"padding:28px 30px\">"
                + "<div dir=\"rtl\" style=\"text-align:right;line-height:1.8\">"
                + "<h2>مراسلة واردة جديدة</h2><p>لديك مراسلة تتطلب الاطلاع أو الإجراء.</p>"
                + "<p><b>الرقم المرجعي:</b> " + escape(ref) + "</p>"
                + "<p><b>الموضوع:</b> " + escape(topic) + "</p></div>"
                + "<hr style=\"border:0;border-top:1px solid #dbe6e1;margin:22px 0\">"
                + "<div dir=\"ltr\"><h2>New incoming correspondence</h2>"
                + "<p>A correspondence requires your review or action.</p>"
                + "<p><b>Reference:</b> " + escape(ref) + "</p>"
                + "<p><b>Subject:</b> " + escape(topic) + "</p></div>"
                + link + "</td></tr><tr><td style=\"background:#f3f6f5;padding:18px 30px;color:#53645f;font-size:12px\">"
                + "BADEA internal correspondence. Sign in to view the document. Do not forward."
                + "</td></tr></table></td></tr></table></body></html>";
        return new Rendered(emailSubject, plain, html);
    }

    private static String shorten(String value, int maximum, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String cleaned = value.strip().replaceAll("[\\p{Cntrl}]", " ");
        return cleaned.length() > maximum ? cleaned.substring(0, maximum) + "…" : cleaned;
    }

    private static String safeInternalHttps(String address) {
        if (address == null || address.isBlank()) return null;
        URI uri;
        try { uri = URI.create(address.strip()); }
        catch (IllegalArgumentException badUrl) { throw new IllegalArgumentException("Invalid inbox URL"); }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !(host.equalsIgnoreCase("badea.org") || host.toLowerCase(java.util.Locale.ROOT).endsWith(".badea.org"))
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Inbox URL must be BADEA HTTPS");
        }
        if (uri.getRawQuery() != null || !uri.getPath().endsWith("/inbox")) {
            throw new IllegalArgumentException("Inbox URL must point to the Inbox page");
        }
        return uri.toASCIIString();
    }

    private static String escape(String text) {
        return Objects.requireNonNull(text).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
