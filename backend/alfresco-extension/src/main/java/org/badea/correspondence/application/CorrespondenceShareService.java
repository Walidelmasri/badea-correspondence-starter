package org.badea.correspondence.application;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.mail.CorrespondenceShareMailTemplate;
import org.badea.correspondence.infrastructure.mail.SmtpShareMailSender;
import org.badea.correspondence.infrastructure.mail.SmtpShareMailSender.MailSubmissionException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

/** Authorizes each operation before calling Alfresco QuickShare or SMTP. */
public final class CorrespondenceShareService {
    private static final Pattern USERNAME =
            Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}");
    private static final Pattern SHARED_ID = Pattern.compile("[A-Za-z0-9_-]{6,256}");
    private static final String SHARE_BASE =
            "https://alfresco.internal.badea.org/#/preview/s/";

    private final CorrespondenceDetailsService details;
    private final CorrespondenceSharedLinkRepository links;
    private final EmployeeDirectory directory;
    private final SmtpShareMailSender mail;
    private final CorrespondenceShareAuditRepository audit;

    public CorrespondenceShareService(CorrespondenceDetailsService details,
            CorrespondenceSharedLinkRepository links,
            EmployeeDirectory directory, SmtpShareMailSender mail,
            CorrespondenceShareAuditRepository audit) {
        this.details = Objects.requireNonNull(details);
        this.links = Objects.requireNonNull(links);
        this.directory = Objects.requireNonNull(directory);
        this.mail = Objects.requireNonNull(mail);
        this.audit = Objects.requireNonNull(audit);
    }

    public record Link(String sharedId, String url, String filename) {}
    public record SendResult(String recipient, String recipientName, Link link) {}

    public Link createLink(String technicalId, String nodeId, CorrespondenceActorRole role,
            DepartmentCode department, String employeeId) {
        ApprovedDocument document = authorizedDocument(technicalId, nodeId, role, department, employeeId, false);
        String sharedId = links.getOrCreate(nodeId);
        audit.append(new CorrespondenceShareAuditRepository.Entry(
                technicalId, nodeId, "LINK_READY", employeeId, null, sharedId, null));
        return makeLink(sharedId, document.filename());
    }

    /** Read an already-issued link, including on archived records; never creates a link. */
    public Link existingLink(String technicalId, String nodeId, CorrespondenceActorRole role,
            DepartmentCode department, String employeeId) {
        ApprovedDocument document = authorizedDocument(technicalId, nodeId, role, department, employeeId, true);
        return makeLink(links.requireExisting(nodeId), document.filename());
    }

    public SendResult send(String technicalId, String nodeId, CorrespondenceActorRole role,
            DepartmentCode department, String employeeId, String senderName,
            String recipientUsername, String comment) {
        // Authorization is repeated, regardless of whether an existing link is public.
        ApprovedDocument document = authorizedDocument(technicalId, nodeId, role, department, employeeId, false);
        if (recipientUsername == null || !USERNAME.matcher(recipientUsername).matches()) {
            throw new IllegalArgumentException("Enter a valid BADEA username (without @domain).");
        }
        if (comment != null && comment.length() > 2000) {
            throw new IllegalArgumentException("Message must be 2000 characters or fewer.");
        }
        EmployeeDirectoryEntry recipient = directory.findActiveByUsername(
                new DirectoryUsername(recipientUsername))
                .orElseThrow(() -> new IllegalArgumentException("No active BADEA employee found."));
        Link link = makeLink(links.requireExisting(nodeId), document.filename());
        String address = recipient.username().value() + "@badea.org";
        var rendered = CorrespondenceShareMailTemplate.render(
                senderName, document.filename(), document.reference(), link.url(), comment);
        // No automatic retry: SMTP timeouts after DATA can mean unknown delivery state.
        audit.append(new CorrespondenceShareAuditRepository.Entry(
                technicalId, nodeId, "EMAIL_ATTEMPTED", employeeId, address, link.sharedId(), comment));
        try {
            // Never retry automatically: a timeout after DATA makes the outcome uncertain.
            mail.send(address, rendered.subject(), rendered.plainText(), rendered.html());
        } catch (MailSubmissionException exception) {
            audit.append(new CorrespondenceShareAuditRepository.Entry(
                    technicalId, nodeId, "EMAIL_FAILED_OR_UNCERTAIN", employeeId,
                    address, link.sharedId(), comment));
            throw exception;
        }
        audit.append(new CorrespondenceShareAuditRepository.Entry(
                technicalId, nodeId, "EMAIL_ACCEPTED_BY_SMTP", employeeId,
                address, link.sharedId(), comment));
        return new SendResult(address, recipient.nameEnglish(), link);
    }

    public void revoke(String technicalId, String nodeId, CorrespondenceActorRole role,
            DepartmentCode department, String employeeId) {
        authorizedDocument(technicalId, nodeId, role, department, employeeId, true);
        String sharedId = links.requireExisting(nodeId);
        links.revoke(nodeId);
        audit.append(new CorrespondenceShareAuditRepository.Entry(
                technicalId, nodeId, "LINK_REVOKED", employeeId, null, sharedId, null));
    }

    private ApprovedDocument authorizedDocument(String technicalId, String nodeId,
            CorrespondenceActorRole role, DepartmentCode department, String employeeId,
            boolean allowArchived) {
        requireCanonicalUuid(technicalId);
        requireCanonicalUuid(nodeId);
        Objects.requireNonNull(role, "Correspondence role required");
        CorrespondenceDetails correspondence = details.getDetails(
                technicalId, role, department, employeeId);
        if (!allowArchived && correspondence.state().isTerminal()) {
            throw new CorrespondenceAuthorizationException(
                    "Archived correspondence cannot be shared again.");
        }
        return java.util.stream.Stream.concat(
                correspondence.documents().stream()
                        .map(d -> new Document(d.nodeId(), d.filename(), d.mimeType())),
                correspondence.generatedDocuments().stream()
                        .map(d -> new Document(d.nodeId(), d.filename(), d.mimeType())))
                .filter(d -> d.nodeId().equals(nodeId) &&
                        "application/pdf".equalsIgnoreCase(d.mimeType()))
                .map(d -> new ApprovedDocument(d.filename(),
                        correspondence.documentNumber() == null || correspondence.documentNumber().isBlank()
                                ? "Not assigned" : correspondence.documentNumber()))
                .findFirst().orElseThrow(CorrespondenceDocumentNotFoundException::new);
    }

    private static Link makeLink(String sharedId, String filename) {
        if (sharedId == null || !SHARED_ID.matcher(sharedId).matches()) {
            throw new IllegalStateException("Alfresco returned an invalid shared link identifier.");
        }
        return new Link(sharedId, SHARE_BASE + sharedId, filename);
    }

    private static void requireCanonicalUuid(String input) {
        if (input == null || !UUID.fromString(input).toString().equals(input)) {
            throw new IllegalArgumentException("A canonical UUID is required.");
        }
    }

    private record Document(String nodeId, String filename, String mimeType) {}
    private record ApprovedDocument(String filename, String reference) {}
}
