package org.badea.correspondence.infrastructure.mail;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Sends correspondence-link notifications using Microsoft Graph app-only OAuth.
 * No tokens, passwords, HTML, or mail credentials are logged or persisted.
 *
 * The caller MUST resolve the recipient against BADEA's active employee directory
 * and authorize the selected document before invoking this transport.
 */
public final class GraphMailSender {
    private static final Pattern EMAIL = Pattern.compile("[a-z0-9][a-z0-9._%+\\-]*@badea\\.org");
    private static final Pattern HTTPS_LINK = Pattern.compile(
            "https://alfresco\\.internal\\.badea\\.org/#/preview/s/[a-zA-Z0-9_-]+(?:[/?#][^\\s]*)?");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String TOKEN_SCOPE = "https://graph.microsoft.com/.default";

    private final HttpPoster poster;
    private final URI tokenEndpoint;
    private final URI sendEndpoint;
    private final String clientId;
    private final String clientSecret;

    public GraphMailSender(String tenantId, String clientId, String clientSecret, String sender) {
        this(tenantId, clientId, clientSecret, sender, new JdkHttpPoster());
    }

    GraphMailSender(String tenantId, String clientId, String clientSecret,
                    String sender, HttpPoster poster) {
        String tenant = requireUuid(tenantId, "Tenant ID");
        this.clientId = requireUuid(clientId, "Client ID");
        this.clientSecret = requireNonEmpty(clientSecret, "Client secret");
        String from = normalizeEmail(sender);
        this.poster = Objects.requireNonNull(poster, "HTTP transport is required");
        this.tokenEndpoint = URI.create(
                "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token");
        this.sendEndpoint = URI.create(
                "https://graph.microsoft.com/v1.0/users/" + from + "/sendMail");
    }

    /** Build from environment at runtime, not from a source-controlled password file. */
    public static GraphMailSender fromEnvironment() {
        return new GraphMailSender(
                System.getenv("BADEA_GRAPH_TENANT_ID"),
                System.getenv("BADEA_GRAPH_CLIENT_ID"),
                System.getenv("BADEA_GRAPH_CLIENT_SECRET"),
                System.getenv("BADEA_GRAPH_SENDER_ADDRESS"));
    }

    /**
     * On success Graph has ACCEPTED the message (HTTP 202). This does not
     * establish mailbox delivery or read receipt. Failed/uncertain calls are
     * never automatically retried, preventing duplicate messages.
     */
    public void sendSharedDocumentLink(String recipientEmail, String documentName, String shareUrl) {
        String to = normalizeEmail(recipientEmail);
        String name = requireNonEmpty(documentName, "Document name");
        String url = requireNonEmpty(shareUrl, "Share URL");
        if (!HTTPS_LINK.matcher(url).matches()) {
            throw new IllegalArgumentException("Expected approved Alfresco HTTPS share URL");
        }
        if (name.length() > 180 || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid document name");
        }

        String tokenForm = "client_id=" + encode(clientId)
                + "&scope=" + encode(TOKEN_SCOPE)
                + "&client_secret=" + encode(clientSecret)
                + "&grant_type=client_credentials";
        String accessToken;
        try {
            PostResponse oauth = poster.post(tokenEndpoint,
                    "application/x-www-form-urlencoded", null, tokenForm);
            if (oauth.status() != 200) {
                throw new GraphMailException("Microsoft identity rejected Graph credentials", oauth.status());
            }
            accessToken = new JSONObject(oauth.body()).optString("access_token", "").trim();
            if (accessToken.isEmpty()) {
                throw new GraphMailException("Microsoft identity returned no access token", 502);
            }
        } catch (GraphMailException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GraphMailException("Graph authentication could not be completed", ex);
        }

        String body = "A BADEA correspondence document has been shared with you.\n\n"
                + "Document: " + name + "\n\n"
                + "Open document: " + url + "\n\n"
                + "This link is a public Alfresco share link. Do not forward it to unauthorized people.";
        JSONObject mail = new JSONObject()
                .put("message", new JSONObject()
                        .put("subject", "BADEA Correspondence — Shared Document")
                        .put("body", new JSONObject()
                                .put("contentType", "Text")
                                .put("content", body))
                        .put("toRecipients", new JSONArray().put(new JSONObject()
                                .put("emailAddress", new JSONObject().put("address", to)))))
                .put("saveToSentItems", true);
        try {
            PostResponse sent = poster.post(sendEndpoint, "application/json", accessToken,
                    mail.toString());
            if (sent.status() != 202) {
                throw new GraphMailException("Microsoft Graph did not accept the message", sent.status());
            }
        } catch (GraphMailException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GraphMailException("Graph submission result is uncertain; do not auto-retry", ex);
        }
    }

    private static String normalizeEmail(String input) {
        String value = requireNonEmpty(input, "Email address").toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(value).matches()) {
            throw new IllegalArgumentException("Email must be a BADEA mailbox address");
        }
        return value;
    }

    private static String requireUuid(String input, String field) {
        String value = requireNonEmpty(input, field);
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(field + " must be a UUID", ex);
        }
    }

    private static String requireNonEmpty(String input, String field) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return input.trim();
    }

    private static String encode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }

    record PostResponse(int status, String body) { }

    @FunctionalInterface
    interface HttpPoster {
        PostResponse post(URI target, String contentType, String bearer, String payload)
                throws IOException, InterruptedException;
    }

    private static final class JdkHttpPoster implements HttpPoster {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8)).build();

        @Override
        public PostResponse post(URI target, String contentType, String bearer,
                                 String payload) throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            if (bearer != null) {
                builder.header("Authorization", "Bearer " + bearer);
            }
            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // Never echo remote bodies: OAuth responses may contain sensitive information.
            return new PostResponse(response.statusCode(),
                    bearer == null && response.statusCode() == 200 ? response.body() : "");
        }
    }
}
