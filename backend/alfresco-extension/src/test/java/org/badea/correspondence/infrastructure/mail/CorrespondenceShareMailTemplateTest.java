package org.badea.correspondence.infrastructure.mail;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class CorrespondenceShareMailTemplateTest {
    @Test void escapesUntrustedMessageAndDocumentNamesInHtml() {
        var email = CorrespondenceShareMailTemplate.render(
                "A <B>", "& bad\"file.pdf", "117/20261025/1",
                "https://alfresco.internal.badea.org/#/preview/s/abc123", "<script>alert('x')</script>");
        assertFalse(email.html().contains("<script>"));
        assertTrue(email.html().contains("&lt;script&gt;"));
        assertTrue(email.html().contains("&amp; bad&quot;file.pdf"));
        assertTrue(email.plainText().contains("<script>alert('x')</script>"));
        assertTrue(email.html().contains("117/20261025/1"));
        assertTrue(email.html().contains("https://alfresco.internal.badea.org/#/preview/s/abc123"));
    }

    @Test void absentMessageDoesNotShowMessageCard() {
        var email = CorrespondenceShareMailTemplate.render(
                "A", "document.pdf", "reference", "https://example.test", "   ");
        assertFalse(email.html().contains("MESSAGE FROM SENDER"));
    }

    @Test void batchEmailUsesOneOutlookFriendlyCardWithLinkedFilenames() {
        var email = CorrespondenceShareMailTemplate.renderMultiple(
                "A <B>", java.util.List.of(
                        new CorrespondenceShareMailTemplate.Item(
                                "first & file.pdf", "https://example.test/s/one"),
                        new CorrespondenceShareMailTemplate.Item(
                                "second.pdf", "https://example.test/s/two")),
                "117/20261025/1", "Please check <script>alert(1)</script>");
        assertTrue(email.html().contains("role=\"presentation\""));
        assertTrue(email.html().contains("background-color:#145749"));
        assertTrue(email.html().contains("Documents shared with you"));
        assertTrue(email.html().contains("2 documents"));
        assertTrue(email.html().contains("first &amp; file.pdf"));
        assertTrue(email.html().contains("https://example.test/s/one"));
        assertTrue(email.html().contains("https://example.test/s/two"));
        assertTrue(email.html().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(email.html().contains("<script>alert(1)</script>"));
        assertFalse(email.html().contains("View document</a>"));
        assertFalse(email.html().contains("<main"));
        assertTrue(email.plainText().contains("https://example.test/s/one"));
        assertTrue(email.plainText().contains("https://example.test/s/two"));
    }

    @Test void batchEmailOmittingMessageRemovesMessageCard() {
        var email = CorrespondenceShareMailTemplate.renderMultiple(
                "Someone", java.util.List.of(new CorrespondenceShareMailTemplate.Item(
                        "doc.pdf", "https://example.test/s/one")), "ref", "  ");
        assertFalse(email.html().contains("MESSAGE FROM SENDER"));
        assertTrue(email.html().contains("1 document"));
    }
}
