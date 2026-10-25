package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.workflow.CorrespondenceState;

/**
 * Reads Director drafts from the authenticated department's
 * correspondence folder.
 *
 * Read-only. Does not create, modify, or repair Alfresco nodes.
 */
public final class AlfrescoCorrespondenceDraftLister {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final QName TYPE_CORRESPONDENCE =
            qname("correspondence");

    private static final QName PROP_TECHNICAL_ID =
            qname("technicalId");

    private static final QName PROP_STATE =
            qname("state");

    private static final QName PROP_ORIGIN_DEPARTMENT =
            qname("originDepartmentCode");

    private static final QName PROP_CREATION_ROLE =
            qname("createdByCorrespondenceRole");

    private static final QName PROP_SUBJECT =
            qname("subject");

    private static final QName PROP_DOCUMENT_NUMBER =
            qname("documentNumber");

    private final NodeService nodeService;
    private final PermissionService permissionService;
    private final CorrespondenceFolderLocator folderLocator;

    public AlfrescoCorrespondenceDraftLister(
            NodeService nodeService,
            PermissionService permissionService,
            CorrespondenceFolderLocator folderLocator) {

        this.nodeService = Objects.requireNonNull(nodeService);
        this.permissionService =
                Objects.requireNonNull(permissionService);

        this.folderLocator =
                Objects.requireNonNull(folderLocator);
    }

    public DraftPage list(
            DepartmentCode departmentCode,
            int offset,
            int limit) {

        Objects.requireNonNull(
                departmentCode,
                "departmentCode must not be null");

        if (offset < 0) {
            throw new IllegalArgumentException(
                    "offset must not be negative");
        }

        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and 100");
        }

        NodeRef departmentFolder =
                folderLocator.locateDepartmentFolder(
                        departmentCode);

        List<DraftSummary> drafts = new ArrayList<>();

        for (ChildAssociationRef association :
                nodeService.getChildAssocs(departmentFolder)) {

            if (!ContentModel.ASSOC_CONTAINS.equals(
                    association.getTypeQName())) {
                continue;
            }

            NodeRef correspondence =
                    association.getChildRef();

            if (!TYPE_CORRESPONDENCE.equals(
                    nodeService.getType(correspondence))) {
                continue;
            }

            Serializable state =
                    nodeService.getProperty(
                            correspondence,
                            PROP_STATE);

            if (!CorrespondenceState.DRAFT.name()
                    .equals(state)) {
                continue;
            }

            // Executive drafts are private to their creators and must never
            // appear in the department-scoped Director Drafts list.
            if (nodeService.getProperty(correspondence, PROP_CREATION_ROLE) != null) {
                continue;
            }

            Serializable originDepartment =
                    nodeService.getProperty(
                            correspondence,
                            PROP_ORIGIN_DEPARTMENT);

            if (!departmentCode.value()
                    .equals(originDepartment)) {
                continue;
            }

            if (permissionService.hasPermission(
                    correspondence,
                    PermissionService.READ
            ) != AccessStatus.ALLOWED) {
                continue;
            }

            drafts.add(
                    readSummary(correspondence));
        }

        drafts.sort(
                Comparator
                        .comparing(
                                DraftSummary::modifiedAt)
                        .reversed()
                        .thenComparing(
                                DraftSummary::technicalId));

        int total = drafts.size();

        int from = Math.min(offset, total);

        int to = (int) Math.min(
                (long) from + limit,
                total);

        return new DraftPage(
                drafts.subList(from, to),
                offset,
                limit,
                total);
    }

    private DraftSummary readSummary(
            NodeRef correspondence) {

        String technicalId =
                requiredText(
                        correspondence,
                        PROP_TECHNICAL_ID);

        String subject =
                requiredText(
                        correspondence,
                        PROP_SUBJECT);

        String documentNumber =
                optionalText(
                        correspondence,
                        PROP_DOCUMENT_NUMBER);

        Instant createdAt =
                requiredInstant(
                        correspondence,
                        ContentModel.PROP_CREATED);

        Instant modifiedAt =
                requiredInstant(
                        correspondence,
                        ContentModel.PROP_MODIFIED);

        return new DraftSummary(
                technicalId,
                subject,
                documentNumber,
                createdAt,
                modifiedAt);
    }

    private String requiredText(
            NodeRef correspondence,
            QName property) {

        Serializable value =
                nodeService.getProperty(
                        correspondence,
                        property);

        if (!(value instanceof String text)
                || text.isBlank()) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence has invalid required metadata: "
                            + property.getLocalName());
        }

        return text;
    }

    private String optionalText(
            NodeRef correspondence,
            QName property) {

        Serializable value =
                nodeService.getProperty(
                        correspondence,
                        property);

        if (value == null) {
            return null;
        }

        if (!(value instanceof String text)) {
            throw new CorrespondenceRepositoryException(
                    "Correspondence has invalid metadata: "
                            + property.getLocalName());
        }

        return text;
    }

    private Instant requiredInstant(
            NodeRef correspondence,
            QName property) {

        Serializable value =
                nodeService.getProperty(
                        correspondence,
                        property);

        if (!(value instanceof Date date)) {
            throw new CorrespondenceRepositoryException(
                    "Correspondence has invalid timestamp metadata: "
                            + property.getLocalName());
        }

        return date.toInstant();
    }

    private static QName qname(String localName) {
        return QName.createQName(
                NAMESPACE_URI,
                localName);
    }

    public record DraftSummary(
            String technicalId,
            String subject,
            String documentNumber,
            Instant createdAt,
            Instant modifiedAt) {
    }

    public record DraftPage(
            List<DraftSummary> items,
            int offset,
            int limit,
            int total) {

        public DraftPage {
            items = List.copyOf(items);
        }
    }
}