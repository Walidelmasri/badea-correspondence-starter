package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;

import org.badea.correspondence.application.CorrespondenceDetails;
import org.badea.correspondence.application.CorrespondenceDocument;
import org.badea.correspondence.workflow.CorrespondenceState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlfrescoCorrespondenceDetailsRepositoryTest {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private NodeService nodeService;
    private CorrespondenceNodeLocator nodeLocator;
    private AlfrescoCorrespondenceDetailsRepository repository;

    private NodeRef correspondence;

    @BeforeEach
    void setUp() {

        nodeService = mock(NodeService.class);
        nodeLocator = mock(CorrespondenceNodeLocator.class);

        correspondence = nodeRef(
                "11111111-1111-1111-1111-111111111111");

        when(nodeLocator.locate(TECHNICAL_ID))
                .thenReturn(correspondence);

        repository =
                new AlfrescoCorrespondenceDetailsRepository(
                        nodeService,
                        nodeLocator);
    }

    @Test
    void readsRealCorrespondenceMetadataWithoutDocuments() {

        when(nodeService.getProperties(correspondence))
                .thenReturn(correspondenceProperties());

        when(nodeService.getChildAssocs(correspondence))
                .thenReturn(List.of());

        CorrespondenceDetails result =
                repository.findByTechnicalId(TECHNICAL_ID);

        assertEquals(TECHNICAL_ID, result.technicalId());

        assertEquals(
                CorrespondenceState.WITH_PRESIDENT,
                result.state());

        assertEquals(
                "Quarterly Strategy Review",
                result.subject());

        assertEquals(
                "Please review the report.",
                result.bodyText());

        assertEquals(
                "DOC-2026-001",
                result.documentNumber());

        assertEquals(
                LocalDate.of(2026, 10, 8),
                result.documentDate());

        assertEquals("117", result.originDepartmentCode());

        assertEquals(
                "Test Employee",
                result.createdByNameEnglish());

        assertTrue(result.documents().isEmpty());
    }

    @Test
    void includesSourcePdfButExcludesWorkflowHistory() {

        NodeRef pdf = nodeRef(
                "22222222-2222-2222-2222-222222222222");

        NodeRef history = nodeRef(
                "33333333-3333-3333-3333-333333333333");

        when(nodeService.getProperties(correspondence))
                .thenReturn(correspondenceProperties());

        ChildAssociationRef pdfAssociation =
                childAssociation(pdf);

        ChildAssociationRef historyAssociation =
                childAssociation(history);

        when(nodeService.getChildAssocs(correspondence))
                .thenReturn(List.of(
                        pdfAssociation,
                        historyAssociation));

        when(nodeService.getType(pdf))
                .thenReturn(ContentModel.TYPE_CONTENT);

        when(nodeService.getType(history))
                .thenReturn(qname("workflowAction"));

        ContentData content = mock(ContentData.class);

        when(content.getMimetype())
                .thenReturn("application/pdf");

        when(content.getSize())
                .thenReturn(4096L);

        Map<QName, Serializable> pdfProperties =
                new HashMap<>();

        pdfProperties.put(
                ContentModel.PROP_NAME,
                "main-letter.pdf");

        pdfProperties.put(
                ContentModel.PROP_CONTENT,
                content);

        when(nodeService.getProperties(pdf))
                .thenReturn(pdfProperties);

        CorrespondenceDetails result =
                repository.findByTechnicalId(TECHNICAL_ID);

        assertEquals(1, result.documents().size());

        CorrespondenceDocument document =
                result.documents().getFirst();

        assertEquals(pdf.getId(), document.nodeId());

        assertEquals(
                "main-letter.pdf",
                document.filename());

        assertEquals(
                "application/pdf",
                document.mimeType());

        assertEquals(
                4096L,
                document.sizeBytes());

        verify(nodeService, never())
                .getProperties(history);
    }

    @Test
    void rejectsCorrespondenceWithMismatchedTechnicalId() {

        Map<QName, Serializable> properties =
                correspondenceProperties();

        properties.put(
                qname("technicalId"),
                "different-id");

        when(nodeService.getProperties(correspondence))
                .thenReturn(properties);

        assertThrows(
                CorrespondenceRepositoryException.class,
                () -> repository.findByTechnicalId(
                        TECHNICAL_ID));
    }

    @Test
    void rejectsSourceDocumentWithoutContentMetadata() {

        NodeRef pdf = nodeRef(
                "44444444-4444-4444-4444-444444444444");

        when(nodeService.getProperties(correspondence))
                .thenReturn(correspondenceProperties());

        ChildAssociationRef pdfAssociation =
            childAssociation(pdf);

        when(nodeService.getChildAssocs(correspondence))
            .thenReturn(List.of(pdfAssociation));

        when(nodeService.getType(pdf))
                .thenReturn(ContentModel.TYPE_CONTENT);

        when(nodeService.getProperties(pdf))
                .thenReturn(Map.of(
                        ContentModel.PROP_NAME,
                        "missing-content.pdf"));

        assertThrows(
                CorrespondenceRepositoryException.class,
                () -> repository.findByTechnicalId(
                        TECHNICAL_ID));
    }

    private Map<QName, Serializable> correspondenceProperties() {

        Map<QName, Serializable> properties =
                new HashMap<>();

        properties.put(qname("technicalId"), TECHNICAL_ID);
        properties.put(qname("state"), "WITH_PRESIDENT");
        properties.put(qname("subject"), "Quarterly Strategy Review");

        properties.put(
                qname("bodyText"),
                "Please review the report.");

        properties.put(
                qname("documentNumber"),
                "DOC-2026-001");

        properties.put(
                qname("documentDate"),
                java.sql.Date.valueOf(
                        LocalDate.of(2026, 10, 8)));

        properties.put(qname("originDepartmentCode"), "117");

        properties.put(
                qname("createdByNameEnglish"),
                "Test Employee");

        return properties;
    }

    private static ChildAssociationRef childAssociation(
            NodeRef child) {

        ChildAssociationRef association =
                mock(ChildAssociationRef.class);

        when(association.getTypeQName())
                .thenReturn(ContentModel.ASSOC_CONTAINS);

        when(association.getChildRef())
                .thenReturn(child);

        return association;
    }

    private static NodeRef nodeRef(String id) {
        return new NodeRef(
                "workspace://SpacesStore/" + id);
    }

    private static QName qname(String name) {
        return QName.createQName(
                NAMESPACE_URI,
                name);
    }
}