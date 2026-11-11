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


    @Test void returnedWorkflowEmailIncludesReasonWithoutLeakingHtmlOrChangingBranding() {
        var email = CorrespondenceIncomingMailTemplate.renderWorkflow("SECRETARY_RETURN",
            "117/20261111/1", "<script>alert(1)</script>", "Needs <work> & review",
            "https://tarasol.internal.badea.org/inbox");
        assertTrue(email.html().contains("BADEA | تراسل · Tarasol"));
        assertTrue(email.html().contains("أُعيدت مراسلتك للتعديل"));
        assertTrue(email.html().contains("Needs &lt;work&gt; &amp; review"));
        assertTrue(email.plainText().contains("Needs <work> & review"));
        assertFalse(email.html().contains("<script>"));
        assertTrue(email.html().contains("href=\"https://tarasol.internal.badea.org/inbox\""));
        assertFalse(email.html().contains("quickshare"));
    }

    @Test void roundApprovalAndOfficeReturnUseExistingBilingualLayout() {
        var request = CorrespondenceIncomingMailTemplate.renderWorkflow("ROUND_REQUEST",
            null, "Cycle 3", null, null);
        assertTrue(request.html().contains("بانتظار موافقتك"));
        assertTrue(request.plainText().contains("Approval requested"));
        assertFalse(request.html().contains("href=\""));
        var office = CorrespondenceIncomingMailTemplate.renderWorkflow("DEPARTMENT_SECRETARIES",
            "117/20261111/2", "Subject", "Office instruction", null);
        assertTrue(office.html().contains("Office instruction"));
        assertThrows(IllegalArgumentException.class, () ->
            CorrespondenceIncomingMailTemplate.renderWorkflow("UNKNOWN", null, null, null, null));
    }

}
