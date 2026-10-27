package org.badea.correspondence.infrastructure.mail;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Self-contained SMTP AUTH LOGIN/STARTTLS transport: no new Alfresco runtime JARs.
 * Only connects to the configured fixed hostname using verified TLS.
 * Never emits usernames, passwords, email contents, or server responses in errors.
 */
public final class SmtpShareMailSender {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private static final int SOCKET_TIMEOUT_MS = 20000;
    private static final String FROM = "noreply@badea.org";

    public SmtpShareMailSender(String host, String port, String username, String password) {
        this.host = Objects.requireNonNull(host);
        this.port = Integer.parseInt(Objects.requireNonNull(port));
        if (this.port != 587 || !"smtp.office365.com".equalsIgnoreCase(host)) {
            throw new IllegalArgumentException("Only Microsoft 365 SMTP STARTTLS on port 587 is permitted.");
        }
        this.username = username;
        this.password = password;
    }

    public void send(String to, String subject, String plainText, String html) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new MailSubmissionException("SMTP sender credentials are not configured.");
        }
        if (!FROM.equalsIgnoreCase(username)) {
            throw new MailSubmissionException("SMTP username must be the approved noreply mailbox.");
        }
        if (to == null || !to.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}@badea\\.org")) {
            throw new IllegalArgumentException("The recipient must be a BADEA email address.");
        }
        String payload = buildMime(to, subject, plainText, html);
        // Do not retry automatically: a timeout after the DATA terminator may still
        // represent a successful Microsoft 365 submission.
        try (Socket raw = new Socket()) {
            raw.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MS);
            raw.setSoTimeout(SOCKET_TIMEOUT_MS);
            var wire = new Exchange(raw);
            wire.expect(220);
            wire.command("EHLO badea.org", 250);
            if (!wire.responseHas("STARTTLS")) {
                throw new MailSubmissionException("SMTP server did not advertise STARTTLS.");
            }
            wire.command("STARTTLS", 220);
            SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(raw, host, port, true);
            SSLParameters params = ssl.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            ssl.setSSLParameters(params);
            ssl.setSoTimeout(SOCKET_TIMEOUT_MS);
            ssl.startHandshake();
            var secure = new Exchange(ssl);
            secure.command("EHLO badea.org", 250);
            secure.command("AUTH LOGIN", 334);
            secure.command(Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)), 334);
            secure.command(Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)), 235);
            secure.command("MAIL FROM:<" + FROM + ">", 250);
            secure.command("RCPT TO:<" + to + ">", 250, 251);
            secure.command("DATA", 354);
            secure.command(payload + "\r\n.", 250);
            try { secure.command("QUIT", 221); } catch (Exception ignored) { /* message already accepted */ }
        } catch (IOException failure) {
            // Deliberately exclude the SMTP response, credentials and email contents.
            throw new MailSubmissionException("SMTP submission failed or its outcome is uncertain.", failure);
        }
    }

    private static String buildMime(String to, String subject, String plainText, String html) {
        Objects.requireNonNull(subject);
        Objects.requireNonNull(plainText);
        Objects.requireNonNull(html);
        String boundary = "badea-" + UUID.randomUUID();
        String encodedSubject = "=?UTF-8?B?" + Base64.getEncoder()
                .encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now());
        return "Date: " + date + "\r\n" +
                "From: Tarasol (BADEA) <" + FROM + ">\r\n" +
                "To: <" + to + ">\r\n" +
                "Subject: " + encodedSubject + "\r\n" +
                "Message-ID: <" + UUID.randomUUID() + "@badea.org>\r\n" +
                "MIME-Version: 1.0\r\n" +
                "Content-Type: multipart/alternative; boundary=\"" + boundary + "\"\r\n\r\n" +
                "--" + boundary + "\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: base64\r\n\r\n" + encode(plainText) + "\r\n" +
                "--" + boundary + "\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: base64\r\n\r\n" + encode(html) + "\r\n" +
                "--" + boundary + "--\r\n";
    }

    private static String encode(String value) {
        return Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class Exchange {
        private final BufferedReader reader;
        private final BufferedWriter writer;
        private String lastResponse = "";
        Exchange(Socket socket) throws IOException {
            reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.US_ASCII));
            writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.US_ASCII));
        }
        void command(String command, int... codes) throws IOException {
            writer.write(command);
            writer.write("\r\n");
            writer.flush();
            expect(codes);
        }
        void expect(int... codes) throws IOException {
            StringBuilder response = new StringBuilder();
            int status = -1;
            boolean complete = false;
            for (int i = 0; i < 100; i++) {
                String line = reader.readLine();
                if (line == null || line.length() < 4 || !line.substring(0, 3).matches("[0-9]{3}")) {
                    throw new IOException("Invalid SMTP response.");
                }
                status = Integer.parseInt(line.substring(0, 3));
                response.append(line).append('\n');
                if (line.charAt(3) == ' ') { complete = true; break; }
                if (line.charAt(3) != '-') throw new IOException("Invalid SMTP response separator.");
            }
            if (!complete) throw new IOException("SMTP response was incomplete.");
            lastResponse = response.toString();
            for (int expected : codes) if (status == expected) return;
            throw new MailSubmissionException("SMTP server rejected submission (status " + status + ").");
        }
        boolean responseHas(String token) {
            return lastResponse.lines().anyMatch(line -> line.length() >= 4
                    && line.substring(4).trim().equalsIgnoreCase(token));
        }
    }

    public static final class MailSubmissionException extends RuntimeException {
        public MailSubmissionException(String message) { super(message); }
        public MailSubmissionException(String message, Throwable cause) { super(message, cause); }
    }
}
