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
    private ScheduledExecutorService scheduler;

    public AlfrescoIncomingNotificationWorker(NodeService nodes, CorrespondenceFolderLocator folders,
            TransactionService transactions, String enabledFlag, String activation, String inboxUrl) {
        this.nodes = Objects.requireNonNull(nodes);
        this.folders = Objects.requireNonNull(folders);
        this.transactions = Objects.requireNonNull(transactions);
        this.enabled = "true".equalsIgnoreCase(enabledFlag);
        if (enabled && (activation == null || activation.isBlank())) {
            throw new IllegalArgumentException("Notification activation instant is required when enabled");
        }
        this.activatedAt = enabled ? Instant.parse(activation) : Instant.MAX;
        this.inboxUrl = inboxUrl;
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
        LOG.info("BADEA incoming email notifications enabled (post-commit scan)");
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
            try { matching = recipients.resolve(event.role(), event.department(), event.actorEmployeeId()); }
            catch (RuntimeException unresolved) {
                LOG.log(Level.WARNING, "Could not resolve incoming notification recipients", unresolved);
                continue;
            }
            for (var recipient : matching) {
                if (submitted >= MAX_SENDS_PER_SWEEP) break;
                if (!inTransaction(false, () -> claim(event, recipient.employeeId()))) continue;
                submitted++;
                try {
                    var message = CorrespondenceIncomingMailTemplate.render(event.reference(),
                            event.subject(), inboxUrl);
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

    private List<Event> findCommittedEvents() {
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
                for (ChildAssociationRef childAssoc : nodes.getChildAssocs(correspondence)) {
                    NodeRef child = childAssoc.getChildRef();
                    QName type = nodes.getType(child);
                    if (TYPE_ACTION.equals(type)) {
                        Instant time = instant(child, "actedAt");
                        if (time == null || time.isBefore(activatedAt)) continue;
                        String target = text(child, "targetRole");
                        CorrespondenceActorRole role;
                        try { role = target == null ? null : CorrespondenceActorRole.valueOf(target); }
                        catch (IllegalArgumentException invalid) { continue; }
                        if (role == null) continue;
                        String rawDepartment = text(child, "targetDepartmentCode");
                        if (role == CorrespondenceActorRole.DIRECTOR && rawDepartment == null) continue;
                        if (role != CorrespondenceActorRole.DIRECTOR && rawDepartment != null) continue;
                        found.add(new Event(correspondence, "ACTION-" + child.getId(), role,
                                rawDepartment == null ? null : new DepartmentCode(rawDepartment),
                                text(child, "actorEmployeeId"), reference, subject));
                    } else if (TYPE_ASSIGNMENT.equals(type)) {
                        Instant time = instant(child, "assignmentAt");
                        if (time == null || time.isBefore(activatedAt)) continue;
                        String department = text(child, "assignmentDepartmentCode");
                        String typeName = text(child, "assignmentRecipientType");
                        if (department == null || !("TO".equals(typeName) || "COPY".equals(typeName))) continue;
                        found.add(new Event(correspondence, "ASSIGNMENT-" + child.getId(),
                                CorrespondenceActorRole.DIRECTOR, new DepartmentCode(department),
                                text(child, "assignmentByEmployeeId"), reference, subject));
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

    private record Event(NodeRef correspondence, String eventKey, CorrespondenceActorRole role,
            DepartmentCode department, String actorEmployeeId, String reference, String subject) { }
}
