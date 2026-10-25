package org.badea.correspondence.infrastructure.mail;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class GraphMailSenderTest {
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";
    private static final String CLIENT = "22222222-2222-4222-8222-222222222222";
    private static final String SHARE = "https://alfresco.internal.badea.org/#/preview/s/Ab123xyz";

    @Test void acceptedEmailUsesScopedGraphApplicationFlowAndBadeaRecipient() {
        List<String> requests = new ArrayList<>();
        GraphMailSender mailer = new GraphMailSender(TENANT, CLIENT, "not-a-real-password",
                "noreply@badea.org", (uri, type, bearer, payload) -> {
            requests.add(uri.toString());
            if (bearer == null) {
                assertTrue(payload.contains("client_credentials"));
                assertTrue(payload.contains("graph.microsoft.com"));
                assertFalse(payload.contains("noreply@badea.org"));
                return new GraphMailSender.PostResponse(200, "{\"access_token\":\"sample-token\"}");
            }
            assertEquals("sample-token", bearer);
            JSONObject json = new JSONObject(payload);
            assertEquals("walid.salem@badea.org", json.getJSONObject("message")
                    .getJSONArray("toRecipients").getJSONObject(0)
                    .getJSONObject("emailAddress").getString("address"));
            assertTrue(json.getJSONObject("message").getJSONObject("body")
                    .getString("content").contains(SHARE));
            return new GraphMailSender.PostResponse(202, "");
        });
        mailer.sendSharedDocumentLink("Walid.Salem@BADEA.ORG", "action-sheet.pdf", SHARE);
        assertEquals(2, requests.size());
        assertTrue(requests.get(1).endsWith("/users/noreply@badea.org/sendMail"));
    }

    @Test void rejectsNonBadeaRecipientWithoutNetworkCall() {
        GraphMailSender mailer = new GraphMailSender(TENANT, CLIENT, "secret",
                "noreply@badea.org", (uri, type, bearer, payload) -> {
            fail("Unexpected network call"); return null;
        });
        assertThrows(IllegalArgumentException.class,
                () -> mailer.sendSharedDocumentLink("external@example.com", "file.pdf", SHARE));
    }

    @Test void rejectsUnapprovedShareUrlWithoutNetworkCall() {
        GraphMailSender mailer = new GraphMailSender(TENANT, CLIENT, "secret",
                "noreply@badea.org", (uri, type, bearer, payload) -> {
            fail("Unexpected network call"); return null;
        });
        assertThrows(IllegalArgumentException.class,
                () -> mailer.sendSharedDocumentLink("walid.salem@badea.org", "file.pdf",
                        "http://attacker.com/document"));
    }

    @Test void failsClosedOnOauthRejection() {
        GraphMailSender mailer = new GraphMailSender(TENANT, CLIENT, "secret",
                "noreply@badea.org", (uri, type, bearer, payload) ->
                        new GraphMailSender.PostResponse(401, "credentials rejected"));
        GraphMailException error = assertThrows(GraphMailException.class,
                () -> mailer.sendSharedDocumentLink("walid.salem@badea.org", "file.pdf", SHARE));
        assertEquals(401, error.status());
        assertFalse(error.getMessage().contains("secret"));
    }

    @Test void requiresGraphAcceptedNotGenericSuccess() {
        GraphMailSender mailer = new GraphMailSender(TENANT, CLIENT, "secret",
                "noreply@badea.org", (uri, type, bearer, payload) -> bearer == null
                ? new GraphMailSender.PostResponse(200, "{\"access_token\":\"some-token\"}")
                : new GraphMailSender.PostResponse(200, ""));
        GraphMailException error = assertThrows(GraphMailException.class,
                () -> mailer.sendSharedDocumentLink("walid.salem@badea.org", "file.pdf", SHARE));
        assertEquals(200, error.status());
    }
}
