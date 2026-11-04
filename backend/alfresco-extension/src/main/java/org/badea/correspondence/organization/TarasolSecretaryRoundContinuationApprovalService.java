package org.badea.correspondence.organization;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceMailboxLister;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.badea.correspondence.application.OfficeCycleContinuationService;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoSecretaryRoundTwoRequestRepository;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoSecretaryContinuationPdfStore;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/** Manager-only decision for any pending secretary continuation cycle. */
public final class TarasolSecretaryRoundContinuationApprovalService {
    private final TarasolOrganizationReader organization;
    private final CorrespondenceActorRoleResolver roles;
    private final AlfrescoSecretaryRoundTwoRequestRepository requests;
    private final OfficeCycleContinuationService cycles;
    private final AlfrescoSecretaryContinuationPdfStore pdfs;
    private final AlfrescoCorrespondenceMailboxLister mailbox;
    private static final int SCAN_PAGE_SIZE = 100;
    private static final int MAX_SCAN_ITEMS = 10000;

    public TarasolSecretaryRoundContinuationApprovalService(TarasolOrganizationReader organization,
            CorrespondenceActorRoleResolver roles,
            AlfrescoSecretaryRoundTwoRequestRepository requests,
            OfficeCycleContinuationService cycles,
            AlfrescoCorrespondenceMailboxLister mailbox,
            AlfrescoSecretaryContinuationPdfStore pdfs) {
        this.organization = Objects.requireNonNull(organization);
        this.roles = Objects.requireNonNull(roles);
        this.requests = Objects.requireNonNull(requests);
        this.cycles = Objects.requireNonNull(cycles);
        this.mailbox = Objects.requireNonNull(mailbox);
        this.pdfs = Objects.requireNonNull(pdfs);
    }

    public record PendingItem(String technicalId, String requestId, String unitCode,
            String ownerEmployeeId, String subject, String documentNumber,
            String reason, long expectedCycle, java.time.Instant requestedAt) { }
    public record PendingPage(List<PendingItem> items, int total, int offset, int limit) {
        public PendingPage { items = List.copyOf(items); }
    }

    /** Manager-only discovery. Never rely on the client to provide correspondence IDs.
     * This uses the already-authorized unit Inbox; an indexed query is needed for large datasets.
     */
    public PendingPage pendingQueue(EmployeeDirectoryEntry manager, DepartmentCode unit,
            int offset, int limit) {
        managerRole(manager, unit);
        if (offset < 0 || limit < 1 || limit > 100)
            throw new IllegalArgumentException("Invalid queue pagination");
        return AuthenticationUtil.runAsSystem(() -> {
            List<PendingItem> found = new ArrayList<>();
            int scanned = 0;
            while (true) {
                var page = mailbox.list("inbox", CorrespondenceActorRole.DIRECTOR, unit,
                    manager.employeeId().value(), "all", scanned, SCAN_PAGE_SIZE);
                for (var item : page.items()) {
                    AlfrescoSecretaryRoundTwoRequestRepository.Request request;
                    try {
                        request = requests.pending(item.technicalId(), unit);
                    } catch (org.badea.correspondence.application.OfficeCycleConflictException staleOrOtherUnit) {
                        // A correspondence may be visible to more than one TO unit;
                        // only the unit owning the pending request may see it here.
                        continue;
                    }
                    if (request != null && "PENDING".equals(request.status())) {
                        found.add(new PendingItem(item.technicalId(), request.requestId(),
                            request.unitCode(), request.ownerEmployeeId(), item.subject(),
                            item.documentNumber(), request.reason(), request.expectedCycle(),
                            request.requestedAt()));
                    }
                }
                scanned += page.items().size();
                if (scanned >= page.total()) break;
                if (page.items().isEmpty() || scanned >= MAX_SCAN_ITEMS)
                    throw new IllegalStateException("Continuation queue needs an indexed search");
            }
            // The source mailbox is ordered newest-first. Always page AFTER filtering.
            int from = Math.min(offset, found.size());
            return new PendingPage(found.subList(from, Math.min(found.size(), from + limit)),
                found.size(), offset, limit);
        });
    }

    public AlfrescoSecretaryRoundTwoRequestRepository.Request pending(
            EmployeeDirectoryEntry manager, DepartmentCode unit, String technicalId) {
        managerRole(manager, unit);
        return AuthenticationUtil.runAsSystem(() -> requests.pending(technicalId, unit));
    }

    public AlfrescoSecretaryRoundTwoRequestRepository.Decision approve(
            EmployeeDirectoryEntry manager, DepartmentCode unit,
            String technicalId, String requestId) {
        CorrespondenceActorRole role = managerRole(manager, unit);
        return AuthenticationUtil.runAsSystem(() -> {
            var pending = requests.pending(technicalId, unit);
            if (pending == null || !pending.requestId().equals(requestId))
                throw new org.badea.correspondence.application.OfficeCycleConflictException("Pending request changed");
            if (manager.employeeId().value().equals(pending.ownerEmployeeId()))
                throw new CorrespondenceAuthorizationException("Request creator cannot approve own continuation");
            CorrespondenceAction action = role == CorrespondenceActorRole.DIRECTOR
                ? CorrespondenceAction.DIRECTOR_CONTINUE_CORRESPONDENCE
                : CorrespondenceAction.CONTINUE_CORRESPONDENCE;
            var command = new TransitionCorrespondenceCommand(technicalId, role, action,
                manager.employeeId(), unit, manager.nameEnglish(), manager.nameArabic(),
                null, pending.reason(), null);
            return requests.approve(technicalId, unit, requestId,
                manager.employeeId().value(), () -> {
                    // The request's private PDFs become source documents for the
                    // NEXT cycle only inside this same Alfresco write transaction.
                    // A failed cycle transition rolls back every moved document.
                    long next = Math.addExact(pending.expectedCycle(), 1);
                    pdfs.promote(technicalId, unit, requestId, next);
                    return cycles.continueCycle(command);
                });
        });
    }

    public AlfrescoSecretaryRoundTwoRequestRepository.Decision returnForRevision(
            EmployeeDirectoryEntry manager, DepartmentCode unit, String technicalId,
            String requestId, String reason) {
        managerRole(manager, unit);
        return AuthenticationUtil.runAsSystem(() -> requests.returnForRevision(technicalId, unit,
            requestId, manager.employeeId().value(), reason));
    }

    private CorrespondenceActorRole managerRole(EmployeeDirectoryEntry manager, DepartmentCode unit) {
        Objects.requireNonNull(manager);
        Objects.requireNonNull(unit);
        var workspace = roles.resolveWorkspace(unit.value(), manager);
        if (!unit.equals(workspace.unit())
                || (workspace.role() != CorrespondenceActorRole.DIRECTOR
                    && workspace.role() != CorrespondenceActorRole.PRESIDENT_OFFICE)
                || organization.findCurrentManagerWorkspaces(manager.employeeId()).stream()
                    .noneMatch(appointment -> unit.equals(appointment.code())))
            throw new CorrespondenceAuthorizationException("Active manager authority required");
        if (workspace.role() == CorrespondenceActorRole.PRESIDENT_OFFICE
                && !"101".equals(unit.value()))
            throw new CorrespondenceAuthorizationException("Office manager can act only for unit 101");
        return workspace.role();
    }
}
