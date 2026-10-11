package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CreateCorrespondenceCommand;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlfrescoCorrespondenceRepositoryTest {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private static final Instant FIXED_INSTANT =
            Instant.parse(
                    "2026-10-11T06:30:00Z");

    private NodeService nodeService;
    private CorrespondenceFolderLocator folderLocator;
    private CorrespondenceNodeLocator nodeLocator;

    private AlfrescoCorrespondenceRepository repository;

    @BeforeEach
    void setUp() {

        nodeService =
                mock(NodeService.class);

        folderLocator =
                mock(CorrespondenceFolderLocator.class);

        nodeLocator =
                mock(CorrespondenceNodeLocator.class);

        Clock clock =
                Clock.fixed(
                        FIXED_INSTANT,
                        ZoneOffset.UTC);

        repository =
                new AlfrescoCorrespondenceRepository(
                        nodeService,
                        folderLocator,
                        nodeLocator,
                        clock);
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
                .thenReturn(
                        departmentFolder);

        when(createdAssociation.getChildRef())
                .thenReturn(
                        createdNode);

        when(nodeService.createNode(
                eq(departmentFolder),
                eq(ContentModel.ASSOC_CONTAINS),
                any(QName.class),
                eq(qname("correspondence")),
                any()))
                .thenReturn(
                        createdAssociation);

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
                repository.createDraft(
                        command);

        assertDoesNotThrow(
                () -> UUID.fromString(
                        technicalId));

        ArgumentCaptor<QName> associationCaptor =
                ArgumentCaptor.forClass(
                        QName.class);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<QName, Serializable>>
                propertiesCaptor =
                ArgumentCaptor.forClass(
                        Map.class);

        verify(nodeService).createNode(
                eq(departmentFolder),
                eq(ContentModel.ASSOC_CONTAINS),
                associationCaptor.capture(),
                eq(qname("correspondence")),
                propertiesCaptor.capture());

        assertEquals(
                "correspondence-" + technicalId,
                associationCaptor
                        .getValue()
                        .getLocalName());

        Map<QName, Serializable> properties =
                propertiesCaptor.getValue();

        assertEquals(
                "111 - "
                        + technicalId
                        + " - Quarterly Strategy Review",
                properties.get(
                        ContentModel.PROP_NAME));

        assertEquals(
                technicalId,
                properties.get(
                        qname("technicalId")));

        assertEquals(
                "Quarterly Strategy Review",
                properties.get(
                        qname("subject")));

        assertEquals(
                "111",
                properties.get(
                        qname("originDepartmentCode")));

        assertEquals(
                CorrespondenceState.DRAFT.name(),
                properties.get(
                        qname("state")));

        assertEquals(
                "18280",
                properties.get(
                        qname("createdByEmployeeId")));

        assertEquals(
                "Almunder Salih Melod Sahboun",
                properties.get(
                        qname("createdByNameEnglish")));

        assertEquals(
                "المنذر صالح ميلود سحبون",
                properties.get(
                        qname("createdByNameArabic")));

        assertEquals(
                0L,
                properties.get(
                        qname("lastActionSequence")));
    }

    @Test
    void readsCurrentPersistedState() {

        NodeRef correspondence =
                nodeRef(
                        "00000000-0000-0000-0000-000000000900");

        when(nodeLocator.locate(
                TECHNICAL_ID))
                .thenReturn(
                        correspondence);

        when(nodeService.getProperty(
                correspondence,
                qname("state")))
                .thenReturn(
                        CorrespondenceState.WITH_PRESIDENT.name());

        CorrespondenceState state =
                repository.getCurrentState(
                        TECHNICAL_ID);

        assertEquals(
                CorrespondenceState.WITH_PRESIDENT,
                state);
    }

    @Test
    void persistsFirstWorkflowActionAndUpdatesAssignment() {

        NodeRef correspondence =
                nodeRef(
                        "00000000-0000-0000-0000-000000000900");

        NodeRef actionNode =
                nodeRef(
                        "00000000-0000-0000-0000-000000000901");

        ChildAssociationRef actionAssociation =
                mock(ChildAssociationRef.class);

        when(nodeLocator.locate(
                TECHNICAL_ID))
                .thenReturn(
                        correspondence);

        when(nodeService.getProperty(
                correspondence,
                qname("state")))
                .thenReturn(
                        CorrespondenceState.DRAFT.name());

        when(nodeService.getProperty(
                correspondence,
                qname("lastActionSequence")))
                .thenReturn(
                        0L);

        when(actionAssociation.getChildRef())
                .thenReturn(
                        actionNode);

        when(nodeService.createNode(
                eq(correspondence),
                eq(ContentModel.ASSOC_CONTAINS),
                any(QName.class),
                eq(qname("workflowAction")),
                any()))
                .thenReturn(
                        actionAssociation);

        TransitionCorrespondenceCommand command =
                transitionCommand(
                        CorrespondenceActorRole.DIRECTOR,
                        CorrespondenceAction.SEND_TO_OFFICE,
                        null,
                        null);

        repository.applyTransition(
                command,
                CorrespondenceState.DRAFT,
                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                CorrespondenceActorRole.PRESIDENT_OFFICE);

        ArgumentCaptor<QName> associationCaptor =
                ArgumentCaptor.forClass(
                        QName.class);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<QName, Serializable>>
                propertiesCaptor =
                ArgumentCaptor.forClass(
                        Map.class);

        verify(nodeService).createNode(
                eq(correspondence),
                eq(ContentModel.ASSOC_CONTAINS),
                associationCaptor.capture(),
                eq(qname("workflowAction")),
                propertiesCaptor.capture());

        assertEquals(
                "action-000001",
                associationCaptor
                        .getValue()
                        .getLocalName());

        Map<QName, Serializable> properties =
                propertiesCaptor.getValue();

        assertEquals(
                "action-000001",
                properties.get(
                        ContentModel.PROP_NAME));

        assertEquals(
                1L,
                properties.get(
                        qname("actionSequence")));

        assertEquals(
                CorrespondenceAction.SEND_TO_OFFICE.name(),
                properties.get(
                        qname("actionType")));

        assertEquals(
                CorrespondenceState.DRAFT.name(),
                properties.get(
                        qname("fromState")));

        assertEquals(
                CorrespondenceState.WITH_PRESIDENT_OFFICE.name(),
                properties.get(
                        qname("toState")));

        assertEquals(
                CorrespondenceActorRole.PRESIDENT_OFFICE.name(),
                properties.get(
                        qname("targetRole")));

        assertEquals(
                "18280",
                properties.get(
                        qname("actorEmployeeId")));

        assertEquals(
                CorrespondenceActorRole.DIRECTOR.name(),
                properties.get(
                        qname("actorRole")));

        assertInstanceOf(
                Date.class,
                properties.get(
                        qname("actedAt")));

        assertEquals(
                Date.from(FIXED_INSTANT),
                properties.get(
                        qname("actedAt")));

        verify(nodeService).setProperty(
                correspondence,
                qname("state"),
                CorrespondenceState.WITH_PRESIDENT_OFFICE.name());

        verify(nodeService).setProperty(
                correspondence,
                qname("assignedRole"),
                CorrespondenceActorRole.PRESIDENT_OFFICE.name());

        verify(nodeService).removeProperty(
                correspondence,
                qname("assignedDepartmentCode"));

        verify(nodeService).setProperty(
                correspondence,
                qname("lastActionSequence"),
                1L);
    }

    @Test
    void persistsDepartmentWhenRoutingToDirector() {

        NodeRef correspondence =
                nodeRef(
                        "00000000-0000-0000-0000-000000000900");

        NodeRef actionNode =
                nodeRef(
                        "00000000-0000-0000-0000-000000000902");

        ChildAssociationRef actionAssociation =
                mock(ChildAssociationRef.class);

        when(nodeLocator.locate(
                TECHNICAL_ID))
                .thenReturn(
                        correspondence);

        when(nodeService.getProperty(
                correspondence,
                qname("state")))
                .thenReturn(
                        CorrespondenceState.WITH_PRESIDENT_OFFICE.name());

        when(nodeService.getProperty(
                correspondence,
                qname("lastActionSequence")))
                .thenReturn(
                        1L);

        when(actionAssociation.getChildRef())
                .thenReturn(
                        actionNode);

        when(nodeService.createNode(
                eq(correspondence),
                eq(ContentModel.ASSOC_CONTAINS),
                any(QName.class),
                eq(qname("workflowAction")),
                any()))
                .thenReturn(
                        actionAssociation);

        DepartmentCode targetDepartment =
                new DepartmentCode("117");

        TransitionCorrespondenceCommand command =
                transitionCommand(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.SEND_TO_DIRECTOR,
                        targetDepartment,
                        null);

        repository.applyTransition(
                command,
                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                CorrespondenceState.WITH_DIRECTOR,
                CorrespondenceActorRole.DIRECTOR);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<QName, Serializable>>
                propertiesCaptor =
                ArgumentCaptor.forClass(
                        Map.class);

        verify(nodeService).createNode(
                eq(correspondence),
                eq(ContentModel.ASSOC_CONTAINS),
                any(QName.class),
                eq(qname("workflowAction")),
                propertiesCaptor.capture());

        Map<QName, Serializable> properties =
                propertiesCaptor.getValue();

        assertEquals(
                2L,
                properties.get(
                        qname("actionSequence")));

        assertEquals(
                "117",
                properties.get(
                        qname("targetDepartmentCode")));

        assertEquals(
                CorrespondenceActorRole.DIRECTOR.name(),
                properties.get(
                        qname("targetRole")));

        verify(nodeService).setProperty(
                correspondence,
                qname("assignedRole"),
                CorrespondenceActorRole.DIRECTOR.name());

        verify(nodeService).setProperty(
                correspondence,
                qname("assignedDepartmentCode"),
                "117");

        verify(nodeService).setProperty(
                correspondence,
                qname("lastActionSequence"),
                2L);
    }

    @Test
    void rejectsTransitionWhenPersistedStateChanged() {

        NodeRef correspondence =
                nodeRef(
                        "00000000-0000-0000-0000-000000000900");

        when(nodeLocator.locate(
                TECHNICAL_ID))
                .thenReturn(
                        correspondence);

        when(nodeService.getProperty(
                correspondence,
                qname("state")))
                .thenReturn(
                        CorrespondenceState.WITH_PRESIDENT.name());

        TransitionCorrespondenceCommand command =
                transitionCommand(
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        CorrespondenceAction.SEND_TO_DIRECTOR,
                        new DepartmentCode("117"),
                        null);

        assertThrows(
                CorrespondenceRepositoryException.class,
                () -> repository.applyTransition(
                        command,
                        CorrespondenceState.WITH_PRESIDENT_OFFICE,
                        CorrespondenceState.WITH_DIRECTOR,
                        CorrespondenceActorRole.DIRECTOR));

        verify(nodeService, never())
                .getProperty(
                        correspondence,
                        qname("lastActionSequence"));
    }

    private static TransitionCorrespondenceCommand transitionCommand(
            CorrespondenceActorRole actorRole,
            CorrespondenceAction action,
            DepartmentCode targetDepartmentCode,
            String closureReason) {

        return new TransitionCorrespondenceCommand(
                TECHNICAL_ID,
                actorRole,
                action,
                new EmployeeId("18280"),
                "Test Employee",
                "موظف اختبار",
                targetDepartmentCode,
                null,
                closureReason);
    }

    private static QName qname(
            String localName) {

        return QName.createQName(
                NAMESPACE_URI,
                localName);
    }

    private static NodeRef nodeRef(
            String id) {

        return new NodeRef(
                "workspace://SpacesStore/" + id);
    }
}