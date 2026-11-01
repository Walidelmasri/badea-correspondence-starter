package org.badea.correspondence.infrastructure.mail;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CorrespondenceIncomingMailTemplateTest {
    @Test void bilingualEmailHasNoPublicShareLinksOrAttachments() {
        var mail = CorrespondenceIncomingMailTemplate.render("117/20261026/2",
                "New correspondence", null);
        assertTrue(mail.plainText().contains("الموضوع"));
        assertTrue(mail.plainText().contains("Action") || mail.plainText().contains("action"));
        assertTrue(mail.html().contains("117/20261026/2"));
        assertFalse(mail.html().contains("href=\""));
    }

    @Test void escapesUntrustedSubjectAndAvoidsHeaderInjection() {
        var mail = CorrespondenceIncomingMailTemplate.render("ref\r\nFake", "<script>bad</script>",
                "https://correspondence.internal.badea.org/inbox");
        assertFalse(mail.html().contains("<script>"));
        assertTrue(mail.html().contains("&lt;script&gt;"));
        assertFalse(mail.subject().contains("\n"));
        assertTrue(mail.html().contains("href=\"https://correspondence.internal.badea.org/inbox\""));
    }

    @Test void refusesNonBadeaAndNonHttpsLinks() {
        assertThrows(IllegalArgumentException.class, () ->
                CorrespondenceIncomingMailTemplate.render("a", "b", "http://alfresco.internal.badea.org/inbox"));
        assertThrows(IllegalArgumentException.class, () ->
                CorrespondenceIncomingMailTemplate.render("a", "b", "https://evil.example/inbox"));
        assertThrows(IllegalArgumentException.class, () ->
                CorrespondenceIncomingMailTemplate.render("a", "b", "https://badea.org@evil.example/inbox"));
    }
    @org.junit.jupiter.api.Test
    void secretaryReturnIsSpecificAndEscapesUntrustedSubject() {
        var email = CorrespondenceIncomingMailTemplate.renderSecretaryStatus(true,
            "<img src=x onerror=alert(1)>");
        org.junit.jupiter.api.Assertions.assertTrue(email.subject().contains("returned"));
        org.junit.jupiter.api.Assertions.assertTrue(email.html().contains("&lt;img"));
        org.junit.jupiter.api.Assertions.assertFalse(email.html().contains("<img"));
        org.junit.jupiter.api.Assertions.assertFalse(email.plainText().contains("@badea.org"));
    }

}
