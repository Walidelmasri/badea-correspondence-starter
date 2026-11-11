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
    public record BatchSendResult(String recipient, String recipientName, java.util.List<Link> links) {}
    public record RecipientOutcome(String recipient, String recipientName, boolean smtpAccepted) {}
    public record MultiBatchSendResult(java.util.List<RecipientOutcome> recipients,
            java.util.List<Link> links, boolean uncertain) {}

    /** Only call with a case returned by ShareCorrespondenceCaseAccess or an equally
     * authoritative server-side read service; never with client-submitted case data.
     */
    public Link createLink(CorrespondenceDetails authorizedCase, String nodeId, String employeeId) {
        ApprovedDocument document = authorizedDocument(authorizedCase, nodeId);
        String sharedId = links.getOrCreate(nodeId);
        audit.append(new CorrespondenceShareAuditRepository.Entry(
                authorizedCase.technicalId(), nodeId, "LINK_READY", employeeId, null, sharedId, null));
        return makeLink(sharedId, document.filename());
    }

    public java.util.Optional<Link> findExistingLink(CorrespondenceDetails authorizedCase,
            String nodeId) {
        ApprovedDocument document = authorizedDocument(authorizedCase, nodeId);
        return links.findExisting(nodeId).map(id -> makeLink(id, document.filename()));
    }

    public SendResult send(CorrespondenceDetails authorizedCase, String nodeId,
            String employeeId, String senderName, String recipientUsername, String comment) {
        ApprovedDocument document = authorizedDocument(authorizedCase, nodeId);
        return sendApproved(authorizedCase.technicalId(), nodeId, employeeId, senderName,
                recipientUsername, comment, document);
    }

    public void revoke(CorrespondenceDetails authorizedCase, String nodeId, String employeeId) {
        authorizedDocument(authorizedCase, nodeId);
        String sharedId = links.requireExisting(nodeId);
        links.revoke(nodeId); // Keep native revocation rules, no elevation.
        audit.append(new CorrespondenceShareAuditRepository.Entry(
                authorizedCase.technicalId(), nodeId, "LINK_REVOKED", employeeId, null, sharedId, null));
    }

    /** One privately addressed SMTP message per active employee; 1-10 PDFs and
     * 1-10 distinct recipients. Individual attempts remain auditable.
     * Unknown SMTP delivery is never automatically retried.
     */
    public MultiBatchSendResult sendBatchMultiple(CorrespondenceDetails authorizedCase,
            java.util.List<String> nodeIds, String employeeId, String senderName,
            java.util.List<String> recipientUsernames, String comment) {
        if (nodeIds == null || nodeIds.isEmpty() || nodeIds.size() > 10
                || new java.util.HashSet<>(nodeIds).size() != nodeIds.size()) {
            throw new IllegalArgumentException("Select one to ten distinct PDFs.");
        }
        if (recipientUsernames == null || recipientUsernames.isEmpty()
                || recipientUsernames.size() > 10
                || new java.util.HashSet<>(recipientUsernames).size() != recipientUsernames.size()
                || recipientUsernames.stream().anyMatch(name ->
                        name == null || !USERNAME.matcher(name).matches())) {
            throw new IllegalArgumentException("Select one to ten distinct BADEA employees.");
        }
        if (comment != null && comment.length() > 2000) {
            throw new IllegalArgumentException("Message must be 2000 characters or fewer.");
        }
        java.util.List<ApprovedDocument> approved = new java.util.ArrayList<>();
        for (String nodeId : nodeIds) approved.add(authorizedDocument(authorizedCase, nodeId));

        // Validate every recipient before creating a QuickShare link or audit entry.
        java.util.List<EmployeeDirectoryEntry> recipients = new java.util.ArrayList<>();
        for (String username : recipientUsernames) {
            recipients.add(directory.findActiveByUsername(new DirectoryUsername(username))
                    .orElseThrow(() -> new IllegalArgumentException("No active BADEA employee found.")));
        }
        java.util.List<Link> shared = new java.util.ArrayList<>();
        for (int i = 0; i < nodeIds.size(); i++) {
            Link link = makeLink(links.getOrCreate(nodeIds.get(i)), approved.get(i).filename());
            shared.add(link);
            audit.append(new CorrespondenceShareAuditRepository.Entry(
                    authorizedCase.technicalId(), nodeIds.get(i), "LINK_READY",
                    employeeId, null, link.sharedId(), null));
        }
        var items = shared.stream().map(link ->
                new CorrespondenceShareMailTemplate.Item(link.filename(), link.url())).toList();
        var rendered = CorrespondenceShareMailTemplate.renderMultiple(
                senderName, items, approved.get(0).reference(), comment);
        java.util.List<RecipientOutcome> outcomes = new java.util.ArrayList<>();
        boolean uncertain = false;
        for (EmployeeDirectoryEntry recipient : recipients) {
            String address = recipient.username().value() + "@badea.org";
            for (int i = 0; i < nodeIds.size(); i++) {
                audit.append(new CorrespondenceShareAuditRepository.Entry(
                        authorizedCase.technicalId(), nodeIds.get(i), "EMAIL_ATTEMPTED",
                        employeeId, address, shared.get(i).sharedId(), comment));
            }
            boolean accepted;
            try {
                mail.send(address, rendered.subject(), rendered.plainText(), rendered.html());
                accepted = true;
            } catch (MailSubmissionException exception) {
                accepted = false;
                uncertain = true;
            }
            for (int i = 0; i < nodeIds.size(); i++) {
                audit.append(new CorrespondenceShareAuditRepository.Entry(
                        authorizedCase.technicalId(), nodeIds.get(i),
                        accepted ? "EMAIL_ACCEPTED_BY_SMTP" : "EMAIL_FAILED_OR_UNCERTAIN",
                        employeeId, address, shared.get(i).sharedId(), comment));
            }
            outcomes.add(new RecipientOutcome(address, recipient.nameEnglish(), accepted));
        }
        return new MultiBatchSendResult(java.util.List.copyOf(outcomes),
                java.util.List.copyOf(shared), uncertain);
    }

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

    /** Absence of a public link is a normal state, not a conflict. */
    public java.util.Optional<Link> findExistingLink(String technicalId, String nodeId,
            CorrespondenceActorRole role, DepartmentCode department, String employeeId) {
        ApprovedDocument doc = authorizedDocument(technicalId, nodeId, role, department, employeeId, true);
        return links.findExisting(nodeId).map(id -> makeLink(id, doc.filename()));
    }

    /** One SMTP message with 1-10 separately authorized PDF links. Never retries SMTP. */
    public BatchSendResult sendBatch(String technicalId, java.util.List<String> nodeIds,
            CorrespondenceActorRole role, DepartmentCode department, String employeeId,
            String senderName, String recipientUsername, String comment) {
        if (nodeIds == null || nodeIds.isEmpty() || nodeIds.size() > 10
                || new java.util.HashSet<>(nodeIds).size() != nodeIds.size()) {
            throw new IllegalArgumentException("Select between one and ten distinct documents.");
        }
        if (recipientUsername == null || !USERNAME.matcher(recipientUsername).matches()) {
            throw new IllegalArgumentException("Invalid BADEA employee username.");
        }
        if (comment != null && comment.length() > 2000) {
            throw new IllegalArgumentException("Message must be 2000 characters or fewer.");
        }
        // Validate ALL document ownership/authorization before publishing ANY public link.
        java.util.List<ApprovedDocument> approved = new java.util.ArrayList<>();
        for (String nodeId : nodeIds) {
            approved.add(authorizedDocument(technicalId, nodeId, role, department, employeeId, false));
        }
        EmployeeDirectoryEntry recipient = directory.findActiveByUsername(
                new DirectoryUsername(recipientUsername))
                .orElseThrow(() -> new IllegalArgumentException("No active BADEA employee found."));
        String address = recipient.username().value() + "@badea.org";
        java.util.List<Link> shared = new java.util.ArrayList<>();
        for (int i = 0; i < nodeIds.size(); i++) {
            Link link = makeLink(links.getOrCreate(nodeIds.get(i)), approved.get(i).filename());
            shared.add(link);
            audit.append(new CorrespondenceShareAuditRepository.Entry(
                    technicalId, nodeIds.get(i), "LINK_READY", employeeId, null, link.sharedId(), null));
        }
        var documentItems = shared.stream().map(link ->
                new CorrespondenceShareMailTemplate.Item(link.filename(), link.url())).toList();
        var rendered = CorrespondenceShareMailTemplate.renderMultiple(
                senderName, documentItems, approved.get(0).reference(), comment);
        for (int i = 0; i < nodeIds.size(); i++) {
            audit.append(new CorrespondenceShareAuditRepository.Entry(
                    technicalId, nodeIds.get(i), "EMAIL_ATTEMPTED", employeeId,
                    address, shared.get(i).sharedId(), comment));
        }
        try {
            mail.send(address, rendered.subject(), rendered.plainText(), rendered.html());
        } catch (MailSubmissionException exception) {
            for (int i = 0; i < nodeIds.size(); i++) {
                audit.append(new CorrespondenceShareAuditRepository.Entry(
                        technicalId, nodeIds.get(i), "EMAIL_FAILED_OR_UNCERTAIN", employeeId,
                        address, shared.get(i).sharedId(), comment));
            }
            throw exception;
        }
        for (int i = 0; i < nodeIds.size(); i++) {
            audit.append(new CorrespondenceShareAuditRepository.Entry(
                    technicalId, nodeIds.get(i), "EMAIL_ACCEPTED_BY_SMTP", employeeId,
                    address, shared.get(i).sharedId(), comment));
        }
        return new BatchSendResult(address, recipient.nameEnglish(), java.util.List.copyOf(shared));
    }

    public SendResult send(String technicalId, String nodeId, CorrespondenceActorRole role,
            DepartmentCode department, String employeeId, String senderName,
            String recipientUsername, String comment) {
        // Authorization is repeated, regardless of whether an existing link is public.
        ApprovedDocument document = authorizedDocument(technicalId, nodeId, role, department, employeeId, false);
        return sendApproved(technicalId, nodeId, employeeId, senderName, recipientUsername,
                comment, document);
    }

    private SendResult sendApproved(String technicalId, String nodeId, String employeeId,
            String senderName, String recipientUsername, String comment, ApprovedDocument document) {
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
            boolean unusedAllowArchived) {
        return authorizedDocument(details.getDetails(technicalId, role, department, employeeId), nodeId);
    }

    private ApprovedDocument authorizedDocument(CorrespondenceDetails correspondence, String nodeId) {
        Objects.requireNonNull(correspondence, "Authorized case required");
        requireCanonicalUuid(correspondence.technicalId());
        requireCanonicalUuid(nodeId);
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
