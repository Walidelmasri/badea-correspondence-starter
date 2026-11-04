package org.badea.correspondence.organization;

import java.util.List;
import java.util.Objects;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoSecretaryContinuationPdfStore;

/** Secretary owns edits; an active appointed manager has read-only review access. */
public final class TarasolSecretaryContinuationPdfService {
    private final TarasolSecretaryRoundTwoRequestService secretaryRequests;
    private final TarasolSecretaryRoundContinuationApprovalService managerApprovals;
    private final AlfrescoSecretaryContinuationPdfStore pdfs;

    public TarasolSecretaryContinuationPdfService(
            TarasolSecretaryRoundTwoRequestService secretaryRequests,
            TarasolSecretaryRoundContinuationApprovalService managerApprovals,
            AlfrescoSecretaryContinuationPdfStore pdfs) {
        this.secretaryRequests = Objects.requireNonNull(secretaryRequests);
        this.managerApprovals = Objects.requireNonNull(managerApprovals);
        this.pdfs = Objects.requireNonNull(pdfs);
    }

    public void authorizeSecretary(EmployeeDirectoryEntry actor, DepartmentCode unit,
            String technicalId, String requestId) {
        var current = secretaryRequests.current(actor, unit, technicalId);
        if (current == null || !("PREPARING".equals(current.status())
                || "RETURNED".equals(current.status()))
                || !current.requestId().equals(requestId))
            throw new OfficeCycleConflictException("No editable continuation request");
    }

    public AlfrescoSecretaryContinuationPdfStore.Pdf upload(EmployeeDirectoryEntry actor,
            DepartmentCode unit, String technicalId, String requestId, String filename, byte[] bytes) {
        authorizeSecretary(actor, unit, technicalId, requestId);
        return AuthenticationUtil.runAsSystem(() -> pdfs.upload(technicalId, unit,
            actor.employeeId().value(), requestId, filename, bytes));
    }

    public void delete(EmployeeDirectoryEntry actor, DepartmentCode unit,
            String technicalId, String requestId, String nodeId) {
        authorizeSecretary(actor, unit, technicalId, requestId);
        AuthenticationUtil.runAsSystem(() -> {
            pdfs.delete(technicalId, unit, actor.employeeId().value(), requestId, nodeId);
            return null;
        });
    }

    private void authorizeSecretaryRead(EmployeeDirectoryEntry actor, DepartmentCode unit,
            String technicalId, String requestId) {
        var current = secretaryRequests.current(actor, unit, technicalId);
        if (current == null || !("PREPARING".equals(current.status())
                || "RETURNED".equals(current.status()) || "PENDING".equals(current.status()))
                || !current.requestId().equals(requestId))
            throw new OfficeCycleConflictException("Continuation request cannot be read");
    }

    public List<AlfrescoSecretaryContinuationPdfStore.Pdf> secretaryList(
            EmployeeDirectoryEntry actor, DepartmentCode unit,
            String technicalId, String requestId) {
        authorizeSecretaryRead(actor, unit, technicalId, requestId);
        return AuthenticationUtil.runAsSystem(() -> pdfs.list(technicalId, unit, requestId,
            actor.employeeId().value()));
    }

    public AlfrescoSecretaryContinuationPdfStore.Preview secretaryOpen(
            EmployeeDirectoryEntry actor, DepartmentCode unit,
            String technicalId, String requestId, String nodeId) {
        authorizeSecretaryRead(actor, unit, technicalId, requestId);
        return AuthenticationUtil.runAsSystem(() -> pdfs.open(technicalId, unit, requestId,
            actor.employeeId().value(), nodeId));
    }

    public List<AlfrescoSecretaryContinuationPdfStore.Pdf> managerList(
            EmployeeDirectoryEntry manager, DepartmentCode unit,
            String technicalId, String requestId) {
        authorizeManager(manager, unit, technicalId, requestId);
        return AuthenticationUtil.runAsSystem(() -> pdfs.list(technicalId, unit, requestId, null));
    }

    public AlfrescoSecretaryContinuationPdfStore.Preview managerOpen(
            EmployeeDirectoryEntry manager, DepartmentCode unit,
            String technicalId, String requestId, String nodeId) {
        authorizeManager(manager, unit, technicalId, requestId);
        return AuthenticationUtil.runAsSystem(() -> pdfs.open(technicalId, unit, requestId, null, nodeId));
    }

    public AlfrescoSecretaryContinuationPdfStore.ReviewedFiles managerReviewedFiles(
            EmployeeDirectoryEntry manager, DepartmentCode unit, String technicalId, String requestId) {
        authorizeManager(manager, unit, technicalId, requestId);
        return AuthenticationUtil.runAsSystem(() ->
            pdfs.reviewedFiles(technicalId, unit, requestId));
    }

    private void authorizeManager(EmployeeDirectoryEntry manager, DepartmentCode unit,
            String technicalId, String requestId) {
        var pending = managerApprovals.pending(manager, unit, technicalId);
        if (pending == null || !"PENDING".equals(pending.status())
                || !pending.requestId().equals(requestId))
            throw new OfficeCycleConflictException("No matching pending continuation request");
    }
}
