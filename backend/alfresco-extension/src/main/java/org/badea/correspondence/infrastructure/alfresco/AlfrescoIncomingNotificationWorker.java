package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.infrastructure.mail.CorrespondenceIncomingMailTemplate;
import org.badea.correspondence.infrastructure.mail.SmtpShareMailSender;
import org.badea.correspondence.infrastructure.mail.WorkflowEmailDeliveryPolicy;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Opt-in, post-commit notification worker. Existing immutable workflow actions
 * and department assignments are the durable event source. Mail never runs in
 * the transaction that changes a correspondence, and existing actions are
 * excluded using the administrator-provided activation instant.
 *
 * A durable ATTEMPTING marker is committed before SMTP is contacted. This
 * deliberately avoids automatic resubmission after an ambiguous SMTP result:
 * at-most-once submission is preferred to duplicate official notifications.
 * ATTEMPTING/UNCERTAIN records require operator investigation when necessary.
 */
public final class AlfrescoIncomingNotificationWorker implements ApplicationContextAware {
    private static final Logger LOG = Logger.getLogger(AlfrescoIncomingNotificationWorker.class.getName());
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final QName TYPE_CORRESPONDENCE = q("correspondence");
    private static final QName TYPE_ACTION = q("workflowAction");
    private static final QName TYPE_ASSIGNMENT = q("departmentAssignment");
    private static final QName TYPE_DELIVERY = q("mailDeliveryReceipt");
    private static final QName TYPE_ROUND_REQUEST = q("secretaryRoundTwoRequest");
    private static final QName TYPE_ROUND_EVENT = q("secretaryRoundTwoDecisionEvent");
    private static final QName PROP_KEY = q("mailDeliveryKey");
    private static final QName PROP_EMPLOYEE = q("mailRecipientEmployeeId");
    private static final QName PROP_STATUS = q("mailDeliveryStatus");
    private static final QName PROP_UPDATED = q("mailDeliveryUpdatedAt");
    private static final int MAX_NODES_PER_SWEEP = 10000;
    private static final int MAX_SENDS_PER_SWEEP = 100;

    private final NodeService nodes;
    private final CorrespondenceFolderLocator folders;
    private final TransactionService transactions;
    private ApplicationContext spring;
    private final boolean enabled;
    private final Instant activatedAt;
    private final String inboxUrl;
    private final WorkflowEmailDeliveryPolicy deliveryPolicy;
    private ScheduledExecutorService scheduler;

    public AlfrescoIncomingNotificationWorker(NodeService nodes, CorrespondenceFolderLocator folders,
            TransactionService transactions, String enabledFlag, String activation, String inboxUrl) {
        this(nodes, folders, transactions, enabledFlag, activation, inboxUrl, null, null);
    }

    public AlfrescoIncomingNotificationWorker(NodeService nodes, CorrespondenceFolderLocator folders,
            TransactionService transactions, String enabledFlag, String activation, String inboxUrl,
            String deliveryScope, String allowedRecipients) {
        this.nodes = Objects.requireNonNull(nodes);
        this.folders = Objects.requireNonNull(folders);
        this.transactions = Objects.requireNonNull(transactions);
        boolean requested = "true".equalsIgnoreCase(enabledFlag);
        if (requested && (activation == null || activation.isBlank())) {
            throw new IllegalArgumentException("Notification activation instant is required when enabled");
        }
        this.activatedAt = requested ? Instant.parse(activation) : Instant.MAX;
        this.inboxUrl = inboxUrl;
        WorkflowEmailDeliveryPolicy policy = WorkflowEmailDeliveryPolicy.disabled();
        if (requested) {
            try {
                policy = WorkflowEmailDeliveryPolicy.parse(deliveryScope, allowedRecipients);
            } catch (IllegalArgumentException unsafe) {
                // An omitted/wrong allowlist must not email the President or prevent Alfresco startup.
                LOG.log(Level.SEVERE, "Automatic workflow email is OFF: recipient scope is not safely configured", unsafe);
            }
        }
        this.deliveryPolicy = policy;
        this.enabled = requested && policy.scope() != WorkflowEmailDeliveryPolicy.Scope.DISABLED;
    }

    @Override public void setApplicationContext(ApplicationContext context) throws BeansException {
        this.spring = Objects.requireNonNull(context);
    }

    public synchronized void start() {
        if (!enabled || scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "badea-correspondence-email-notifications");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::safeSweep, 60, 120, TimeUnit.SECONDS);
        LOG.info("BADEA workflow email notifications enabled (post-commit scan; scope=" + deliveryPolicy.scope() + ")");
    }

    public synchronized void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        scheduler = null;
    }

    private void safeSweep() {
        try { sweep(); }
        catch (Exception failure) {
            LOG.log(Level.WARNING, "Correspondence notification sweep failed; no workflow action was changed", failure);
        }
    }

    void sweep() {
        if (!enabled) return;
        // Resolve lazy Oracle/SMTP services only once notifications have been enabled.
        // A missing directory must never prevent Alfresco itself from starting.
        var recipients = spring.getBean("badeaCorrespondenceNotificationRecipients",
                AlfrescoNotificationRecipientResolver.class);
        var smtp = spring.getBean("badeaCorrespondenceSmtpShareMailSender", SmtpShareMailSender.class);
        List<Event> events = inTransaction(true, this::findCommittedEvents);
        int submitted = 0;
        for (Event event : events) {
            if (submitted >= MAX_SENDS_PER_SWEEP) break;
            List<AlfrescoNotificationRecipientResolver.Recipient> matching;
            try {
                matching = switch (event.kind()) {
                    case SECRETARY_RETURN, SECRETARY_APPROVED, ROUND_RETURN, ROUND_APPROVED ->
                        recipients.resolveSpecificEmployee(event.targetEmployeeId(), event.actorEmployeeId());
                    case DEPARTMENT_SECRETARIES ->
                        recipients.resolveUnitSecretaries(event.department(), event.actorEmployeeId());
                    case MANAGER_REQUEST, ROUND_REQUEST ->
                        recipients.resolveApprovalManagers(event.department(), event.role(), event.actorEmployeeId());
                    case INCOMING, DEPARTMENT_MANAGER_RETURN ->
                        recipients.resolve(event.role(), event.department(), event.actorEmployeeId());
                };
            } catch (RuntimeException unresolved) {
                LOG.log(Level.WARNING, "Could not resolve workflow notification recipients", unresolved);
                continue;
            }
            // NodeService reads must remain inside an Alfresco read transaction.
            String technicalId = inTransaction(true, () -> text(event.correspondence(), "technicalId"));
            String unitCode = event.department() == null
                ? inTransaction(true, () -> text(event.correspondence(), "originDepartmentCode"))
                : event.department().value();
            for (var recipient : matching) {
                if (submitted >= MAX_SENDS_PER_SWEEP) break;
                // Never create delivery markers or submit SMTP for blocked test recipients.
                if (!deliveryPolicy.permits(recipient.email())) continue;
                CorrespondenceIncomingMailTemplate.Rendered message;
                try {
                    // Every email points to its committed correspondence, never to a generic Inbox.
                    // The destination remains protected by the normal Tarasol route/API guards.
                    String destination = CorrespondenceIncomingMailTemplate.caseLink(
                        inboxUrl, event.kind().name(), technicalId, unitCode);
                    message = event.kind() == Kind.INCOMING
                        ? CorrespondenceIncomingMailTemplate.render(event.reference(), event.subject(), destination)
                        : CorrespondenceIncomingMailTemplate.renderWorkflow(event.kind().name(),
                            event.reference(), event.subject(), event.explanation(), destination);
                } catch (IllegalArgumentException templateError) {
                    // Invalid site configuration must not consume a durable SMTP-attempt marker.
                    LOG.warning("Workflow email template could not be rendered; check internal Inbox URL");
                    continue;
                }
                if (!inTransaction(false, () -> claim(event, recipient.employeeId()))) continue;
                submitted++;
                try {
                    smtp.send(recipient.email(), message.subject(), message.plainText(), message.html());
                    inTransaction(false, () -> { update(event, recipient.employeeId(), "SENT"); return null; });
                } catch (RuntimeException failure) {
                    // A timeout after SMTP DATA may mean successful acceptance.
                    // Do not automatically retry and risk duplicate confidential notifications.
                    LOG.log(Level.WARNING, "Correspondence mail submission uncertain; requires review", failure);
                    try { inTransaction(false, () -> { update(event, recipient.employeeId(), "UNCERTAIN"); return null; }); }
                    catch (RuntimeException markerFailure) {
                        LOG.log(Level.SEVERE, "Could not record uncertain mail submission", markerFailure);
                    }
                }
            }
        }
    }

    List<Event> findCommittedEvents() {
        NodeRef root = folders.locateCorrespondenceRoot();
        List<Event> found = new ArrayList<>();
        int scanned = 0;
        for (ChildAssociationRef departmentAssoc : nodes.getChildAssocs(root)) {
            NodeRef departmentFolder = departmentAssoc.getChildRef();
            if (!ContentModel.TYPE_FOLDER.equals(nodes.getType(departmentFolder))) continue;
            for (ChildAssociationRef correspondenceAssoc : nodes.getChildAssocs(departmentFolder)) {
                NodeRef correspondence = correspondenceAssoc.getChildRef();
                if (!TYPE_CORRESPONDENCE.equals(nodes.getType(correspondence))) continue;
                if (++scanned > MAX_NODES_PER_SWEEP) {
                    throw new IllegalStateException("Notification scan limit reached; no incomplete scan permitted");
                }
                String subject = text(correspondence, "subject");
                String reference = text(correspondence, "documentNumber");
                List<ChildAssociationRef> children = nodes.getChildAssocs(correspondence);
                Map<String, NodeRef> roundRequests = new HashMap<>();
                for (ChildAssociationRef association : children) {
                    NodeRef child = association.getChildRef();
                    if (TYPE_ROUND_REQUEST.equals(nodes.getType(child))) {
                        String id = text(child, "roundTwoRequestId");
                        if (id != null) roundRequests.put(id, child);
                    }
                }
                for (ChildAssociationRef childAssoc : children) {
                    NodeRef child = childAssoc.getChildRef();
                    QName type = nodes.getType(child);
                    if (TYPE_ACTION.equals(type)) {
                        Instant time = instant(child, "actedAt");
                        if (time == null || time.isBefore(activatedAt)) continue;
                        String action = text(child, "actionType");
                        String target = text(child, "targetRole");
                        CorrespondenceActorRole role;
                        try { role = target == null ? null : CorrespondenceActorRole.valueOf(target); }
                        catch (IllegalArgumentException invalid) { continue; }
                        if (role == null || "REVISE_SECRETARY_DRAFT".equals(action)
                                || "COMPLETE_DEPARTMENT_ASSIGNMENT".equals(action)) continue;
                        String rawDepartment = text(child, "targetDepartmentCode");
                        String actor = text(child, "actorEmployeeId");
                        String explanation = text(child, "explanation");
                        if ("SUBMIT_FOR_MANAGER_APPROVAL".equals(action)) {
                            if (role != CorrespondenceActorRole.DIRECTOR
                                    && role != CorrespondenceActorRole.PRESIDENT_OFFICE) continue;
                            if (rawDepartment == null && role == CorrespondenceActorRole.PRESIDENT_OFFICE) {
                                // Office secretary submissions use their originating unit (101).
                                rawDepartment = text(correspondence, "originDepartmentCode");
                            }
                            if (rawDepartment == null) continue;
                            found.add(new Event(correspondence, "ACTION-" + child.getId(),
                                Kind.MANAGER_REQUEST, role, new DepartmentCode(rawDepartment),
                                actor, null, reference, subject, null));
                            continue;
                        }
                        if (role == CorrespondenceActorRole.SECRETARY) {
                            if (!"RETURN_TO_SECRETARY".equals(action) || rawDepartment != null) continue;
                            String owner = text(child, "targetEmployeeId");
                            if (owner == null) continue;
                            found.add(new Event(correspondence, "ACTION-" + child.getId(),
                                Kind.SECRETARY_RETURN, role, null, actor, owner,
                                reference, subject, explanation));
                            continue;
                        }
                        if (role == CorrespondenceActorRole.DIRECTOR && rawDepartment == null) continue;
                        if (role != CorrespondenceActorRole.DIRECTOR && rawDepartment != null) continue;
                        boolean officeReturn = "SEND_TO_DIRECTOR".equals(action)
                            && role == CorrespondenceActorRole.DIRECTOR
                            && "PRESIDENT_OFFICE".equals(text(child, "actorRole"));
                        found.add(new Event(correspondence, "ACTION-" + child.getId(),
                            officeReturn ? Kind.DEPARTMENT_MANAGER_RETURN : Kind.INCOMING, role,
                            rawDepartment == null ? null : new DepartmentCode(rawDepartment),
                            actor, null, reference, subject, explanation));
                        if (officeReturn) {
                            found.add(new Event(correspondence, "OFFICE-SECRETARIES-" + child.getId(),
                                Kind.DEPARTMENT_SECRETARIES, role, new DepartmentCode(rawDepartment),
                                actor, null, reference, subject, explanation));
                        }
                        if ("DRAFT".equals(text(child, "fromState"))
                                && "APPROVED".equals(text(correspondence, "secretaryApprovalStatus"))
                                && ("SEND_TO_PRESIDENT".equals(action) || "SEND_TO_OFFICE".equals(action))) {
                            String owner = text(correspondence, "secretaryOwnerEmployeeId");
                            if (owner != null) {
                                found.add(new Event(correspondence,
                                    "SECRETARY-APPROVED-" + child.getId(), Kind.SECRETARY_APPROVED,
                                    CorrespondenceActorRole.SECRETARY, null, actor, owner,
                                    reference, subject, null));
                            }
                        }
                    } else if (TYPE_ASSIGNMENT.equals(type)) {
                        Instant time = instant(child, "assignmentAt");
                        if (time == null || time.isBefore(activatedAt)) continue;
                        String department = text(child, "assignmentDepartmentCode");
                        String typeName = text(child, "assignmentRecipientType");
                        if (department == null || !("TO".equals(typeName) || "COPY".equals(typeName))) continue;
                        DepartmentCode unit = new DepartmentCode(department);
                        String actor = text(child, "assignmentByEmployeeId");
                        found.add(new Event(correspondence, "ASSIGNMENT-" + child.getId(),
                            Kind.INCOMING, CorrespondenceActorRole.DIRECTOR, unit,
                            actor, null, reference, subject, null));
                        if ("TO".equals(typeName)) {
                            found.add(new Event(correspondence, "ASSIGNMENT-SECRETARIES-" + child.getId(),
                                Kind.DEPARTMENT_SECRETARIES, CorrespondenceActorRole.DIRECTOR, unit,
                                actor, null, reference, subject, null));
                        }
                    } else if (TYPE_ROUND_EVENT.equals(type)) {
                        Instant time = instant(child, "roundTwoDecisionAt");
                        if (time == null || time.isBefore(activatedAt)) continue;
                        String id = text(child, "roundTwoDecisionRequestId");
                        NodeRef request = roundRequests.get(id);
                        if (request == null) continue;
                        String unitText = text(request, "roundTwoRequestUnit");
                        String owner = text(request, "roundTwoRequestOwner");
                        String action = text(child, "roundTwoDecisionAction");
                        if (unitText == null || owner == null) continue;
                        Kind kind = switch (action == null ? "" : action) {
                            case "SUBMITTED", "RESUBMITTED" -> Kind.ROUND_REQUEST;
                            case "RETURNED" -> Kind.ROUND_RETURN;
                            case "APPROVED" -> Kind.ROUND_APPROVED;
                            default -> null;
                        };
                        if (kind == null) continue; // PREPARING/REVISED never generates mail.
                        String actor = text(child, "roundTwoDecisionActor");
                        DepartmentCode unit = new DepartmentCode(unitText);
                        found.add(new Event(correspondence, "ROUND-" + child.getId(),
                            kind, kind == Kind.ROUND_REQUEST
                                ? ("101".equals(unit.value()) ? CorrespondenceActorRole.PRESIDENT_OFFICE
                                    : CorrespondenceActorRole.DIRECTOR)
                                : CorrespondenceActorRole.SECRETARY,
                            kind == Kind.ROUND_REQUEST ? unit : null,
                            actor, kind == Kind.ROUND_REQUEST ? null : owner,
                            reference, subject, text(child, "roundTwoDecisionReason")));
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    private boolean claim(Event event, String employeeId) {
        String filename = filename(event, employeeId);
        if (nodes.getChildByName(event.correspondence(), ContentModel.ASSOC_CONTAINS, filename) != null) return false;
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_NAME, filename);
        properties.put(PROP_KEY, event.eventKey());
        properties.put(PROP_EMPLOYEE, employeeId);
        properties.put(PROP_STATUS, "ATTEMPTING");
        properties.put(PROP_UPDATED, Date.from(Instant.now()));
        ChildAssociationRef created = nodes.createNode(event.correspondence(), ContentModel.ASSOC_CONTAINS,
                q(filename), TYPE_DELIVERY, properties);
        if (created == null || created.getChildRef() == null) {
            throw new IllegalStateException("Notification delivery marker was not created");
        }
        return true;
    }

    private void update(Event event, String employeeId, String status) {
        NodeRef marker = nodes.getChildByName(event.correspondence(), ContentModel.ASSOC_CONTAINS,
                filename(event, employeeId));
        if (marker == null || !TYPE_DELIVERY.equals(nodes.getType(marker))) {
            throw new IllegalStateException("Notification delivery marker missing");
        }
        nodes.setProperty(marker, PROP_STATUS, status);
        nodes.setProperty(marker, PROP_UPDATED, Date.from(Instant.now()));
    }

    private <T> T inTransaction(boolean readOnly, java.util.concurrent.Callable<T> action) {
        return transactions.getRetryingTransactionHelper().doInTransaction(
                () -> AuthenticationUtil.runAsSystem(() -> {
                    try { return action.call(); }
                    catch (RuntimeException error) { throw error; }
                    catch (Exception error) { throw new IllegalStateException(error); }
                }), readOnly, true);
    }

    private String text(NodeRef node, String field) {
        Object value = nodes.getProperty(node, q(field));
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    private Instant instant(NodeRef node, String field) {
        Object value = nodes.getProperty(node, q(field));
        return value instanceof Date date ? date.toInstant() : null;
    }

    private static String filename(Event event, String employeeId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (event.eventKey() + ":" + employeeId).getBytes(StandardCharsets.UTF_8));
            return "mail-delivery-" + HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static QName q(String name) { return QName.createQName(NS, name); }

    enum Kind {
        INCOMING, MANAGER_REQUEST, SECRETARY_RETURN, SECRETARY_APPROVED,
        DEPARTMENT_SECRETARIES, DEPARTMENT_MANAGER_RETURN,
        ROUND_REQUEST, ROUND_RETURN, ROUND_APPROVED
    }

    record Event(NodeRef correspondence, String eventKey, Kind kind,
            CorrespondenceActorRole role, DepartmentCode department,
            String actorEmployeeId, String targetEmployeeId,
            String reference, String subject, String explanation) { }
}
