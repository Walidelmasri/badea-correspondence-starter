package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;

import org.badea.correspondence.application.CorrespondenceDetails;
import org.badea.correspondence.application.CorrespondenceDetailsRepository;
import org.badea.correspondence.application.CorrespondenceDocument;
import org.badea.correspondence.application.CorrespondenceGeneratedDocument;
import org.badea.correspondence.workflow.CorrespondenceState;

public final class AlfrescoCorrespondenceDetailsRepository
        implements CorrespondenceDetailsRepository {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final QName PROP_TECHNICAL_ID =
            qname("technicalId");

    private static final QName PROP_STATE =
            qname("state");

    private static final QName PROP_SUBJECT =
            qname("subject");

    private static final QName PROP_BODY_TEXT =
            qname("bodyText");

    private static final QName PROP_DOCUMENT_NUMBER =
            qname("documentNumber");

    private static final QName PROP_DOCUMENT_DATE =
            qname("documentDate");

    private static final QName PROP_SOURCE_TEXT =
            qname("sourceText");

    private static final QName PROP_ORIGIN_DEPARTMENT_CODE =
            qname("originDepartmentCode");

    private static final QName PROP_CREATED_BY_NAME_ENGLISH =
            qname("createdByNameEnglish");

    private static final QName PROP_CREATED_BY_NAME_ARABIC =
            qname("createdByNameArabic");

    private static final QName PROP_EXECUTIVE_COMPOSE =
            qname("executiveComposeJson");

    private final NodeService nodeService;
    private final CorrespondenceNodeLocator nodeLocator;

    public AlfrescoCorrespondenceDetailsRepository(
            NodeService nodeService,
            CorrespondenceNodeLocator nodeLocator) {

        this.nodeService = Objects.requireNonNull(
                nodeService,
                "nodeService must not be null");

        this.nodeLocator = Objects.requireNonNull(
                nodeLocator,
                "nodeLocator must not be null");
    }

    @Override
    public CorrespondenceDetails findByTechnicalId(
            String technicalId) {

        NodeRef correspondence =
                nodeLocator.locate(technicalId);

        Map<QName, Serializable> properties =
                readProperties(correspondence);

        String storedTechnicalId =
                requiredText(
                        properties,
                        PROP_TECHNICAL_ID,
                        "technicalId");

        if (!storedTechnicalId.equals(technicalId)) {
            throw new CorrespondenceRepositoryException(
                    "Correspondence technical identity mismatch.");
        }

        return new CorrespondenceDetails(
                storedTechnicalId,
                readState(properties),
                requiredText(
                        properties,
                        PROP_SUBJECT,
                        "subject"),
                optionalText(
                        properties,
                        PROP_BODY_TEXT,
                        "bodyText"),
                optionalText(
                        properties,
                        PROP_DOCUMENT_NUMBER,
                        "documentNumber"),
                optionalDate(properties),
                optionalText(
                        properties,
                        PROP_SOURCE_TEXT,
                        "sourceText"),
                requiredText(
                        properties,
                        PROP_ORIGIN_DEPARTMENT_CODE,
                        "originDepartmentCode"),
                requiredText(
                        properties,
                        PROP_CREATED_BY_NAME_ENGLISH,
                        "createdByNameEnglish"),
                optionalText(
                        properties,
                        PROP_CREATED_BY_NAME_ARABIC,
                        "createdByNameArabic"),
                readSourceDocuments(correspondence),
                readGeneratedDocuments(correspondence),
                optionalText(properties, PROP_EXECUTIVE_COMPOSE, "executiveComposeJson"));
    }

    /**
     * Source documents are direct cm:content children.
     *
     * This deliberately excludes:
     * - bc:workflowAction history records;
     * - folders containing generated output;
     * - other repository objects.
     *
     * Future uploads must follow this source-document convention.
     */
    private List<CorrespondenceDocument> readSourceDocuments(
            NodeRef correspondence) {

        List<CorrespondenceDocument> documents =
                new ArrayList<>();

        for (ChildAssociationRef association :
                nodeService.getChildAssocs(correspondence)) {

            if (!ContentModel.ASSOC_CONTAINS.equals(
                    association.getTypeQName())) {
                continue;
            }

            NodeRef child = association.getChildRef();

            /*
             * Exact type comparison is intentional.
             * bc:workflowAction extends cm:content, but it
             * must never appear as an original attachment.
             */
            if (!ContentModel.TYPE_CONTENT.equals(
                    nodeService.getType(child))) {
                continue;
            }

            Map<QName, Serializable> properties =
                    readProperties(child);

            String filename =
                    requiredText(
                            properties,
                            ContentModel.PROP_NAME,
                            "document filename");

            Serializable contentProperty =
                    properties.get(ContentModel.PROP_CONTENT);

            if (!(contentProperty instanceof ContentData content)) {
                throw new CorrespondenceRepositoryException(
                        "Correspondence document has no valid content metadata.");
            }

            String mimeType = content.getMimetype();

            if (mimeType == null || mimeType.isBlank()) {
                throw new CorrespondenceRepositoryException(
                        "Correspondence document has no MIME type.");
            }

            long sizeBytes = content.getSize();

            documents.add(
                    new CorrespondenceDocument(
                            child.getId(),
                            filename,
                            mimeType,
                            sizeBytes));
        }

        documents.sort(
                Comparator
                        .comparing(
                                CorrespondenceDocument::filename,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                CorrespondenceDocument::nodeId));

        return List.copyOf(documents);
    }

    private List<CorrespondenceGeneratedDocument> readGeneratedDocuments(NodeRef correspondence) {
        List<CorrespondenceGeneratedDocument> documents = new ArrayList<>();
        for (ChildAssociationRef association : nodeService.getChildAssocs(correspondence)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef child = association.getChildRef();
            if (AlfrescoPresidentGeneratedDocumentStore.TYPE.equals(nodeService.getType(child))) {
                documents.add(AlfrescoPresidentGeneratedDocumentStore.describe(child, nodeService));
            }
        }
        documents.sort(Comparator.comparing(CorrespondenceGeneratedDocument::reviewRevision)
                .thenComparing(CorrespondenceGeneratedDocument::artifactType)
                .thenComparing(CorrespondenceGeneratedDocument::nodeId));
        return List.copyOf(documents);
    }

    private Map<QName, Serializable> readProperties(
            NodeRef node) {

        Map<QName, Serializable> properties =
                nodeService.getProperties(node);

        if (properties == null) {
            throw new CorrespondenceRepositoryException(
                    "Alfresco returned no properties for a correspondence node.");
        }

        return properties;
    }

    private static CorrespondenceState readState(
            Map<QName, Serializable> properties) {

        String value =
                requiredText(
                        properties,
                        PROP_STATE,
                        "state");

        try {
            return CorrespondenceState.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new CorrespondenceRepositoryException(
                    "Correspondence has an unknown workflow state.",
                    exception);
        }
    }

    private static LocalDate optionalDate(
            Map<QName, Serializable> properties) {

        Serializable value =
                properties.get(PROP_DOCUMENT_DATE);

        if (value == null) {
            return null;
        }

        /*
         * java.sql.Date.toInstant() is unsupported.
         * Handle SQL dates before java.util.Date.
         */
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        if (value instanceof java.util.Date date) {
            return date.toInstant()
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();
        }

        throw new CorrespondenceRepositoryException(
                "Correspondence has an invalid document date.");
    }

    private static String requiredText(
            Map<QName, Serializable> properties,
            QName property,
            String fieldName) {

        Serializable value = properties.get(property);

        if (!(value instanceof String text)
                || text.isBlank()) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence has no valid " + fieldName + ".");
        }

        return text;
    }

    private static String optionalText(
            Map<QName, Serializable> properties,
            QName property,
            String fieldName) {

        Serializable value = properties.get(property);

        if (value == null) {
            return null;
        }

        if (!(value instanceof String text)) {
            throw new CorrespondenceRepositoryException(
                    "Correspondence has an invalid " + fieldName + ".");
        }

        return text;
    }

    private static QName qname(String localName) {
        return QName.createQName(
                NAMESPACE_URI,
                localName);
    }
}