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
        String emailSubject = "Tarasol | Action required";
        String plain = "تراسل | المصرف العربي للتنمية الاقتصادية في أفريقيا\n"
                + "لديك مراسلة واردة تتطلب الاطلاع أو الإجراء.\n"
                + "الرقم المرجعي: " + ref + "\nالموضوع: " + topic + "\n\n"
                + "Tarasol | BADEA Official Correspondence\n"
                + "You have received correspondence for review or action.\n"
                + "Reference: " + ref + "\nSubject: " + topic + "\n"
                + (url == null ? "Open Tarasol, the BADEA correspondence application.\n"
                        : "Open Correspondence: " + url + "\n")
                + "Sign in with your BADEA account. Do not forward this message.\n";
        String link = url == null
                ? "<p style=\"color:#52675f\">افتح منصة المراسلات الداخلية / Open the internal correspondence application.</p>"
                : "<p style=\"margin:22px 0\"><a href=\"" + escape(url)
                    + "\" style=\"background:#145749;color:white;display:inline-block;padding:12px 22px;"
                    + "text-decoration:none;border-radius:5px\">فتح المراسلة / Open Correspondence</a></p>";
        String html = "<!doctype html><html><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"background:#f3f6f5;font-family:Arial,Tahoma,sans-serif;color:#19352f\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"600\" style=\"max-width:100%;background:white;border:1px solid #dbe6e1\">"
                + "<tr><td style=\"padding:23px 30px;background:#145749;color:white;font-size:20px;font-weight:bold\">"
                + "BADEA | تراسل · Tarasol</td></tr>"
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

    /** Confidential secretary status alert; no document contents or public links. */
    public static Rendered renderSecretaryStatus(boolean returned, String correspondenceSubject) {
        String topic = shorten(correspondenceSubject, 180, "Secretary correspondence");
        String en = returned
            ? "Your draft was returned by the manager for revision. Sign in to Tarasol and review the manager's feedback."
            : "Your draft was approved and sent onward. Sign in to Tarasol to view its status.";
        String ar = returned
            ? "أعاد المدير المسودة للتعديل. يرجى مراجعة ملاحظاته في تراسل."
            : "تم اعتماد المسودة وإرسالها للجهة التالية. يمكنك مراجعة الحالة في تراسل.";
        String subject = returned ? "Tarasol | Draft returned for revision"
            : "Tarasol | Draft approved and sent";
        String plain = "تراسل | " + ar + "\nالموضوع: " + topic
            + "\n\nTarasol | " + en + "\nSubject: " + topic
            + "\nSign in with your BADEA account. Do not forward this message.\n";
        String html = "<!doctype html><html><head><meta charset=\"UTF-8\"></head>"
            + "<body style=\"font-family:Arial,Tahoma,sans-serif;background:#f3f6f5;color:#19352f\">"
            + "<table role=\"presentation\" width=\"100%\"><tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"600\" style=\"max-width:100%;background:white\">"
            + "<tr><td style=\"padding:24px;background:#145749;color:white;font-size:20px\">BADEA | تراسل · Tarasol</td></tr>"
            + "<tr><td style=\"padding:25px\"><p dir=\"rtl\">" + escape(ar) + "</p>"
            + "<p>" + escape(en) + "</p><p><b>Subject:</b> " + escape(topic) + "</p>"
            + "<p>Sign in with your BADEA account. Do not forward.</p>"
            + "</td></tr></table></td></tr></table></body></html>";
        return new Rendered(subject, plain, html);
    }

    /** Existing BADEA bilingual layout, with action-specific text from committed events. */
    public static Rendered renderWorkflow(String eventKind, String reference,
            String correspondenceSubject, String explanation, String secureInboxUrl) {
        String ref = shorten(reference, 100, "Pending reference");
        String topic = shorten(correspondenceSubject, 180, "Correspondence");
        String reason = explanation == null || explanation.isBlank()
            ? null : shorten(explanation, 1800, "");
        String url = safeInternalHttps(secureInboxUrl);
        Notice notice = switch (eventKind) {
            case "MANAGER_REQUEST", "ROUND_REQUEST" -> new Notice(
                "Approval requested", "مراسلة بانتظار موافقتك",
                "A secretary submission is awaiting your review and approval.",
                "مراسلة مقدمة من السكرتارية بانتظار مراجعتك وموافقتك.");
            case "SECRETARY_RETURN", "ROUND_RETURN" -> new Notice(
                "Returned for revision", "أُعيدت مراسلتك للتعديل",
                "The manager returned your correspondence for revision.",
                "أعاد المدير مراسلتك للتعديل. يرجى مراجعة سبب الإرجاع.");
            case "SECRETARY_APPROVED", "ROUND_APPROVED" -> new Notice(
                "Correspondence approved", "تمت الموافقة على مراسلتك",
                "The manager approved your correspondence.",
                "وافق المدير على مراسلتك.");
            case "DEPARTMENT_MANAGER_RETURN" -> new Notice(
                "Correspondence returned to your department", "مراسلة معادة إلى إدارتك",
                "President's Office returned correspondence to your department for action.",
                "أعاد مكتب الرئيس المراسلة إلى إدارتك لاتخاذ الإجراء المطلوب.");
            case "DEPARTMENT_SECRETARIES" -> new Notice(
                "Departmental correspondence received", "مراسلة واردة إلى إدارتك",
                "Correspondence was sent to your department. Review the instructions and follow up when eligible.",
                "وردت مراسلة إلى إدارتك. يرجى مراجعة التوجيهات والمتابعة عند إتاحة الإجراء.");
            default -> throw new IllegalArgumentException("Unsupported workflow notification event");
        };
        String subject = "Tarasol | " + notice.englishHeading();
        String reasonPlain = reason == null ? "" : "\nسبب الإرجاع / التفاصيل: " + reason
            + "\nReturn reason / explanation: " + reason;
        String plain = "تراسل | " + notice.arabicHeading() + "\n" + notice.arabicBody()
            + "\nالرقم المرجعي: " + ref + "\nالموضوع: " + topic + reasonPlain
            + "\n\nTarasol | " + notice.englishHeading() + "\n" + notice.englishBody()
            + "\nReference: " + ref + "\nSubject: " + topic
            + (url == null ? "\nOpen Tarasol using your BADEA account." : "\nOpen Inbox: " + url)
            + "\nDo not forward this internal notification.\n";
        String reasonHtmlAr = reason == null ? "" : "<p><b>سبب الإرجاع / التفاصيل:</b> " + escape(reason) + "</p>";
        String reasonHtmlEn = reason == null ? "" : "<p><b>Return reason / explanation:</b> " + escape(reason) + "</p>";
        String link = url == null
            ? "<p style=\"color:#52675f\">افتح منصة المراسلات الداخلية / Open Tarasol.</p>"
            : "<p style=\"margin:22px 0\"><a href=\"" + escape(url)
                + "\" style=\"background:#145749;color:white;display:inline-block;padding:12px 22px;"
                + "text-decoration:none;border-radius:5px\">فتح المراسلة / Open Correspondence</a></p>";
        String html = "<!doctype html><html><head><meta charset=\"UTF-8\"></head>"
            + "<body style=\"background:#f3f6f5;font-family:Arial,Tahoma,sans-serif;color:#19352f\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"600\" style=\"max-width:100%;background:white;border:1px solid #dbe6e1\">"
            + "<tr><td style=\"padding:23px 30px;background:#145749;color:white;font-size:20px;font-weight:bold\">"
            + "BADEA | تراسل · Tarasol</td></tr><tr><td style=\"padding:28px 30px\">"
            + "<div dir=\"rtl\" style=\"text-align:right;line-height:1.8\">"
            + "<h2>" + escape(notice.arabicHeading()) + "</h2><p>" + escape(notice.arabicBody()) + "</p>"
            + "<p><b>الرقم المرجعي:</b> " + escape(ref) + "</p><p><b>الموضوع:</b> " + escape(topic) + "</p>"
            + reasonHtmlAr + "</div><hr style=\"border:0;border-top:1px solid #dbe6e1;margin:22px 0\">"
            + "<div dir=\"ltr\"><h2>" + escape(notice.englishHeading()) + "</h2>"
            + "<p>" + escape(notice.englishBody()) + "</p><p><b>Reference:</b> " + escape(ref)
            + "</p><p><b>Subject:</b> " + escape(topic) + "</p>" + reasonHtmlEn + "</div>"
            + link + "</td></tr><tr><td style=\"background:#f3f6f5;padding:18px 30px;color:#53645f;font-size:12px\">"
            + "BADEA internal correspondence. Sign in to view the document. Do not forward."
            + "</td></tr></table></td></tr></table></body></html>";
        return new Rendered(subject, plain, html);
    }

    private record Notice(String englishHeading, String arabicHeading,
            String englishBody, String arabicBody) { }

    private static String shorten(String value, int maximum, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String cleaned = value.strip().replaceAll("[\\p{Cntrl}]", " ");
        return cleaned.length() > maximum ? cleaned.substring(0, maximum) + "…" : cleaned;
    }

    /** Link to an existing authorized case or approval queue, never to an unprotected PDF. */
    public static String caseLink(String configuredInbox, String eventKind, String technicalId) {
        return caseLink(configuredInbox, eventKind, technicalId, null);
    }

    public static String caseLink(String configuredInbox, String eventKind,
            String technicalId, String unitCode) {
        String inbox = safeInternalHttps(configuredInbox);
        if (inbox == null) return null;
        if (technicalId == null || !technicalId.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("Notification correspondence ID is invalid");
        }
        String root = inbox.substring(0, inbox.length() - "/inbox".length());
        String route = switch (eventKind) {
            case "MANAGER_REQUEST" -> "/inbox?view=needs_action&pending=" + technicalId;
            case "ROUND_REQUEST" -> "/inbox?view=needs_action&round=" + technicalId;
            case "SECRETARY_RETURN" -> "/secretary/new?draft=" + technicalId;
            case "SECRETARY_APPROVED", "ROUND_APPROVED" -> "/sent?open=" + technicalId;
            case "INCOMING", "DEPARTMENT_MANAGER_RETURN", "DEPARTMENT_SECRETARIES", "ROUND_RETURN" ->
                "/inbox?view=all&open=" + technicalId;
            default -> throw new IllegalArgumentException("Unsupported notification link kind");
        };
        if (unitCode != null && !unitCode.isBlank()) {
            if (!unitCode.matches("[0-9]{3,5}")) {
                throw new IllegalArgumentException("Notification unit code is invalid");
            }
            route += "&unitCode=" + unitCode;
        }
        return root + route;
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
        // Only the notification routes produced by caseLink are accepted.
        String path = uri.getPath();
        String query = uri.getRawQuery();
        boolean validBase = "/inbox".equals(path) && query == null;
        String id = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
        boolean inboxCase = "/inbox".equals(path) && query != null && (
            query.matches("view=all&open=" + id + "(&unitCode=[0-9]{3,5})?")
            || query.matches("view=needs_action&pending=" + id + "(&unitCode=[0-9]{3,5})?")
            || query.matches("view=needs_action&round=" + id + "(&unitCode=[0-9]{3,5})?"));
        boolean sentCase = "/sent".equals(path) && query != null
            && query.matches("open=" + id + "(&unitCode=[0-9]{3,5})?");
        boolean returnedDraft = "/secretary/new".equals(path) && query != null
            && query.matches("draft=" + id + "(&unitCode=[0-9]{3,5})?");
        if (!(validBase || inboxCase || sentCase || returnedDraft)) {
            throw new IllegalArgumentException("Notification URL must target an authorized Tarasol workspace");
        }
        return uri.toASCIIString();
    }

    private static String escape(String text) {
        return Objects.requireNonNull(text).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
