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
}
