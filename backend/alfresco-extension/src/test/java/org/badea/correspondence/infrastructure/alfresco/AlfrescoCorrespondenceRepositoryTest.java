package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CreateCorrespondenceCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlfrescoCorrespondenceRepositoryTest {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private NodeService nodeService;
    private CorrespondenceFolderLocator folderLocator;

    private AlfrescoCorrespondenceRepository repository;

    @BeforeEach
    void setUp() {
        nodeService = mock(NodeService.class);
        folderLocator = mock(CorrespondenceFolderLocator.class);

        repository =
                new AlfrescoCorrespondenceRepository(
                        nodeService,
                        folderLocator);
    }

    @Test
    void createsDraftUnderOriginDepartmentFolder() {

        DepartmentCode departmentCode =
                new DepartmentCode("111");

        NodeRef departmentFolder =
                nodeRef(
                        "00000000-0000-0000-0000-000000000111");

        NodeRef createdNode =
                nodeRef(
                        "00000000-0000-0000-0000-000000000999");

        ChildAssociationRef createdAssociation =
                mock(ChildAssociationRef.class);

        when(folderLocator.locateDepartmentFolder(
                departmentCode))
                .thenReturn(departmentFolder);

        when(createdAssociation.getChildRef())
                .thenReturn(createdNode);

        when(nodeService.createNode(
                eq(departmentFolder),
                eq(ContentModel.ASSOC_CONTAINS),
                any(QName.class),
                eq(qname("correspondence")),
                any()))
                .thenReturn(createdAssociation);

        CreateCorrespondenceCommand command =
                new CreateCorrespondenceCommand(
                        departmentCode,
                        "Quarterly Strategy Review",
                        "Please review the attached correspondence.",
                        "DOC-2026-001",
                        LocalDate.of(2026, 10, 8),
                        "Strategy Department",
                        new EmployeeId("18280"),
                        "Almunder Salih Melod Sahboun",
                        "المنذر صالح ميلود سحبون");

        String technicalId =
                repository.createDraft(command);

        assertDoesNotThrow(
                () -> UUID.fromString(technicalId));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<QName, Serializable>> propertiesCaptor =
                ArgumentCaptor.forClass(Map.class);

        verify(nodeService).createNode(
                eq(departmentFolder),
                eq(ContentModel.ASSOC_CONTAINS),
                any(QName.class),
                eq(qname("correspondence")),
                propertiesCaptor.capture());

        Map<QName, Serializable> properties =
                propertiesCaptor.getValue();

        assertEquals(
                technicalId,
                properties.get(qname("technicalId")));

        assertEquals(
                "Quarterly Strategy Review",
                properties.get(qname("subject")));

        assertEquals(
                "111",
                properties.get(qname("originDepartmentCode")));

        assertEquals(
                CorrespondenceState.DRAFT.name(),
                properties.get(qname("state")));

        assertEquals(
                "18280",
                properties.get(qname("createdByEmployeeId")));

        assertEquals(
                "Almunder Salih Melod Sahboun",
                properties.get(qname("createdByNameEnglish")));

        assertEquals(
                "المنذر صالح ميلود سحبون",
                properties.get(qname("createdByNameArabic")));

        assertEquals(
                0L,
                properties.get(qname("lastActionSequence")));
    }

    private static QName qname(String localName) {
        return QName.createQName(
                NAMESPACE_URI,
                localName);
    }

    private static NodeRef nodeRef(String id) {
        return new NodeRef(
                "workspace://SpacesStore/" + id);
    }
}