package org.badea.correspondence.organization;

import java.io.InputStream;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.badea.correspondence.application.CorrespondenceDetailsRepository;
import org.badea.correspondence.application.CorrespondenceDocument;
import org.badea.correspondence.application.CorrespondenceDocumentContentRepository;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceNodeLocator;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceState;

/**
 * A deliberately separate read-only route for secretary draft attachments.
 * It cannot grant generic correspondence read access or manager permissions.
 * Both the case and the document must be persisted children in Alfresco.
 */
public final class TarasolSecretaryDocumentService {
    private final TarasolSecretaryWorkflowService workflow;
    private final CorrespondenceDetailsRepository details;
    private final CorrespondenceDocumentContentRepository content;
    private final CorrespondenceNodeLocator locator;
    private final PermissionService permissions;

    public TarasolSecretaryDocumentService(TarasolSecretaryWorkflowService workflow,
            CorrespondenceDetailsRepository details,
            CorrespondenceDocumentContentRepository content,
            CorrespondenceNodeLocator locator, PermissionService permissions) {
        this.workflow = Objects.requireNonNull(workflow);
        this.details = Objects.requireNonNull(details);
        this.content = Objects.requireNonNull(content);
        this.locator = Objects.requireNonNull(locator);
        this.permissions = Objects.requireNonNull(permissions);
    }

    public List<CorrespondenceDocument> list(EmployeeDirectoryEntry actor,
            DepartmentCode unit, String technicalId, boolean managerView) {
        checkId(technicalId);
        workflow.authorizePreview(actor, unit, technicalId, managerView);
        // Oracle secretary/manager appointment, unit, owner and approval status
        // have been rechecked. Read only this verified draft; the underlying
        // department folder ACL may not include the secretary/acting manager.
        var saved = AuthenticationUtil.runAsSystem(() -> details.findByTechnicalId(technicalId));
        if ((!unit.value().equals(saved.originDepartmentCode()))
                || (saved.state() != CorrespondenceState.DRAFT
                    && (managerView || !workflow.isApprovedOwnedCase(actor, unit, technicalId)))) {
            throw new CorrespondenceAuthorizationException("Draft no longer available for preview");
        }
        return saved.documents().stream()
            .filter(d -> "application/pdf".equalsIgnoreCase(d.mimeType()))
            .toList();
    }

    public record Preview(CorrespondenceDocument document, InputStream stream) { }

    public Preview open(EmployeeDirectoryEntry actor, DepartmentCode unit,
            String technicalId, String documentNodeId, boolean managerView) {
        checkId(documentNodeId);
        CorrespondenceDocument document = list(actor, unit, technicalId, managerView).stream()
            .filter(item -> item.nodeId().equals(documentNodeId))
            .findFirst().orElseThrow(CorrespondenceDocumentNotFoundException::new);
        // This repository also checks the document's native Alfresco READ ACL.
        return new Preview(document, AuthenticationUtil.runAsSystem(
            () -> content.openByNodeId(document.nodeId())));
    }

    private static void checkId(String id) {
        try {
            if (id == null || !UUID.fromString(id).toString().equals(id)) {
                throw new IllegalArgumentException("Canonical UUID required");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Canonical UUID required", exception);
        }
    }
}
