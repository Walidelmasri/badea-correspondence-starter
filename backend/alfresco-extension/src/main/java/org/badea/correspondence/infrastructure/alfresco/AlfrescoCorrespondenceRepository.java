package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.sql.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CreateCorrespondenceCommand;
import org.badea.correspondence.workflow.CorrespondenceState;

public final class AlfrescoCorrespondenceRepository {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final QName TYPE_CORRESPONDENCE =
            QName.createQName(
                    NAMESPACE_URI,
                    "correspondence");

    private static final QName PROP_TECHNICAL_ID =
            QName.createQName(
                    NAMESPACE_URI,
                    "technicalId");

    private static final QName PROP_SUBJECT =
            QName.createQName(
                    NAMESPACE_URI,
                    "subject");

    private static final QName PROP_BODY_TEXT =
            QName.createQName(
                    NAMESPACE_URI,
                    "bodyText");

    private static final QName PROP_DOCUMENT_NUMBER =
            QName.createQName(
                    NAMESPACE_URI,
                    "documentNumber");

    private static final QName PROP_DOCUMENT_DATE =
            QName.createQName(
                    NAMESPACE_URI,
                    "documentDate");

    private static final QName PROP_SOURCE_TEXT =
            QName.createQName(
                    NAMESPACE_URI,
                    "sourceText");

    private static final QName PROP_ORIGIN_DEPARTMENT_CODE =
            QName.createQName(
                    NAMESPACE_URI,
                    "originDepartmentCode");

    private static final QName PROP_STATE =
            QName.createQName(
                    NAMESPACE_URI,
                    "state");

    private static final QName PROP_CREATED_BY_EMPLOYEE_ID =
            QName.createQName(
                    NAMESPACE_URI,
                    "createdByEmployeeId");

    private static final QName PROP_CREATED_BY_NAME_ENGLISH =
            QName.createQName(
                    NAMESPACE_URI,
                    "createdByNameEnglish");

    private static final QName PROP_CREATED_BY_NAME_ARABIC =
            QName.createQName(
                    NAMESPACE_URI,
                    "createdByNameArabic");

    private static final QName PROP_LAST_ACTION_SEQUENCE =
            QName.createQName(
                    NAMESPACE_URI,
                    "lastActionSequence");

    private final NodeService nodeService;
    private final CorrespondenceFolderLocator folderLocator;

    public AlfrescoCorrespondenceRepository(
            NodeService nodeService,
            CorrespondenceFolderLocator folderLocator) {

        this.nodeService = Objects.requireNonNull(
                nodeService,
                "nodeService must not be null");

        this.folderLocator = Objects.requireNonNull(
                folderLocator,
                "folderLocator must not be null");
    }

    public String createDraft(
            CreateCorrespondenceCommand command) {

        Objects.requireNonNull(
                command,
                "command must not be null");

        NodeRef departmentFolder =
                folderLocator.locateDepartmentFolder(
                        command.originDepartmentCode());

        String technicalId =
                UUID.randomUUID().toString();

        Map<QName, Serializable> properties =
                new HashMap<>();

        properties.put(
                ContentModel.PROP_NAME,
                technicalId);

        properties.put(
                PROP_TECHNICAL_ID,
                technicalId);

        properties.put(
                PROP_SUBJECT,
                command.subject());

        putIfPresent(
                properties,
                PROP_BODY_TEXT,
                command.bodyText());

        putIfPresent(
                properties,
                PROP_DOCUMENT_NUMBER,
                command.documentNumber());

        if (command.documentDate() != null) {
            properties.put(
                    PROP_DOCUMENT_DATE,
                    Date.valueOf(command.documentDate()));
        }

        putIfPresent(
                properties,
                PROP_SOURCE_TEXT,
                command.sourceText());

        properties.put(
                PROP_ORIGIN_DEPARTMENT_CODE,
                command.originDepartmentCode().value());

        properties.put(
                PROP_STATE,
                CorrespondenceState.DRAFT.name());

        properties.put(
                PROP_CREATED_BY_EMPLOYEE_ID,
                command.createdByEmployeeId().value());

        properties.put(
                PROP_CREATED_BY_NAME_ENGLISH,
                command.createdByNameEnglish());

        putIfPresent(
                properties,
                PROP_CREATED_BY_NAME_ARABIC,
                command.createdByNameArabic());

        properties.put(
                PROP_LAST_ACTION_SEQUENCE,
                0L);

        QName associationQName =
                QName.createQName(
                        NAMESPACE_URI,
                        "correspondence-" + technicalId);

        ChildAssociationRef created =
                nodeService.createNode(
                        departmentFolder,
                        ContentModel.ASSOC_CONTAINS,
                        associationQName,
                        TYPE_CORRESPONDENCE,
                        properties);

        if (created == null
                || created.getChildRef() == null) {

            throw new CorrespondenceRepositoryException(
                    "Alfresco did not return the created correspondence node.");
        }

        return technicalId;
    }

    private static void putIfPresent(
            Map<QName, Serializable> properties,
            QName property,
            String value) {

        if (value != null && !value.isBlank()) {
            properties.put(
                    property,
                    value);
        }
    }
}