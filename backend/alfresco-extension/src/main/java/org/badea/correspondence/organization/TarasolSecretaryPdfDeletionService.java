package org.badea.correspondence.organization;

import java.util.Objects;
import java.util.UUID;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceNodeLocator;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceState;

/** Deletes only a source PDF from the authenticated secretary's editable personal draft. */
public final class TarasolSecretaryPdfDeletionService {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final QName TYPE_CORRESPONDENCE = QName.createQName(NS, "correspondence");
    private static final QName STATE = QName.createQName(NS, "state");
    private static final QName OWNER = QName.createQName(NS, "secretaryOwnerEmployeeId");
    private static final QName STATUS = QName.createQName(NS, "secretaryApprovalStatus");
    private static final QName UNIT = QName.createQName(NS, "originDepartmentCode");

    private final TarasolSecretaryWorkflowService workflow;
    private final CorrespondenceNodeLocator locator;
    private final NodeService nodes;

    public TarasolSecretaryPdfDeletionService(TarasolSecretaryWorkflowService workflow,
            CorrespondenceNodeLocator locator, NodeService nodes) {
        this.workflow = Objects.requireNonNull(workflow);
        this.locator = Objects.requireNonNull(locator);
        this.nodes = Objects.requireNonNull(nodes);
    }

    public void delete(EmployeeDirectoryEntry secretary, DepartmentCode unit,
            String technicalId, String documentId) {
        checkUuid(technicalId);
        checkUuid(documentId);
        // Oracle grant and persisted author/status, checked before elevating Alfresco ACLs.
        workflow.authorizePdfUpload(secretary, unit, technicalId);
        AuthenticationUtil.runAsSystem(() -> {
            var correspondence = locator.locate(technicalId);
            if (!TYPE_CORRESPONDENCE.equals(nodes.getType(correspondence))
                    || !CorrespondenceState.DRAFT.name().equals(nodes.getProperty(correspondence, STATE))
                    || !secretary.employeeId().value().equals(nodes.getProperty(correspondence, OWNER))
                    || !unit.value().equals(nodes.getProperty(correspondence, UNIT))) {
                throw denied();
            }
            Object approval = nodes.getProperty(correspondence, STATUS);
            if (!"DRAFT".equals(approval) && !"RETURNED".equals(approval)) throw denied();
            for (var link : nodes.getChildAssocs(correspondence)) {
                var name = link.getQName();
                if (!ContentModel.ASSOC_CONTAINS.equals(link.getTypeQName())
                        || name == null || !NS.equals(name.getNamespaceURI())
                        || !name.getLocalName().startsWith("source-document-")) continue;
                var child = link.getChildRef();
                if (child != null && documentId.equals(child.getId())) {
                    if (!nodes.exists(child) || !ContentModel.TYPE_CONTENT.equals(nodes.getType(child))) {
                        throw new CorrespondenceDocumentNotFoundException();
                    }
                    Object storedContent = nodes.getProperty(child, ContentModel.PROP_CONTENT);
                    if (!(storedContent instanceof ContentData pdf)
                            || !"application/pdf".equalsIgnoreCase(pdf.getMimetype())) {
                        throw new CorrespondenceDocumentNotFoundException();
                    }
                    nodes.deleteNode(child);
                    return null;
                }
            }
            throw new CorrespondenceDocumentNotFoundException();
        });
    }

    private static void checkUuid(String value) {
        try {
            if (value == null || !UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException("Canonical UUID required");
            }
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Canonical UUID required", invalid);
        }
    }

    private static CorrespondenceAuthorizationException denied() {
        return new CorrespondenceAuthorizationException("Secretary draft PDF deletion not authorized");
    }
}
