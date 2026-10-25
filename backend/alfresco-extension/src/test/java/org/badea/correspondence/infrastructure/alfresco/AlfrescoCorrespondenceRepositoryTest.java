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
import org.badea.correspondence.application.CorrespondenceAccessContext;
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
        assertEquals(1L, properties.get(qname("currentWorkflowCycle")));
        // Director creation never gains executive-only metadata.
        org.junit.jupiter.api.Assertions.assertFalse(
                properties.containsKey(qname("executiveComposeJson")));
        org.junit.jupiter.api.Assertions.assertFalse(
                properties.containsKey(qname("createdByCorrespondenceRole")));
    }

    @Test
    void preservesExecutiveComposeJsonAndAuthenticatedOriginRole() {
        DepartmentCode department = new DepartmentCode("111");
        NodeRef folder = nodeRef("00000000-0000-0000-0000-000000000111");
        NodeRef created = nodeRef("00000000-0000-0000-0000-000000000999");
        ChildAssociationRef association = mock(ChildAssociationRef.class);
        when(folderLocator.locateDepartmentFolder(department)).thenReturn(folder);
        when(association.getChildRef()).thenReturn(created);
        when(nodeService.createNode(eq(folder), eq(ContentModel.ASSOC_CONTAINS),
                any(QName.class), eq(qname("correspondence")), any()))
                .thenReturn(association);

        String json = "{\"recipient\":\"Legal Affairs\"}";
        CreateCorrespondenceCommand command = new CreateCorrespondenceCommand(
                department, "Presidential correspondence", null, null, null,
                null, new EmployeeId("18280"), "Author", null,
                CorrespondenceActorRole.PRESIDENT, json);
        repository.createDraft(command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<QName, Serializable>> captured =
                ArgumentCaptor.forClass(Map.class);
        verify(nodeService).createNode(eq(folder), eq(ContentModel.ASSOC_CONTAINS),
                any(QName.class), eq(qname("correspondence")), captured.capture());
        assertEquals("PRESIDENT", captured.getValue().get(
                qname("createdByCorrespondenceRole")));
        assertEquals(json, captured.getValue().get(qname("executiveComposeJson")));
    }

    @Test
    void readsCorrespondenceAccessContext() {

        NodeRef correspondence =
                nodeRef(
                        "00000000-0000-0000-0000-000000000900");

        when(nodeLocator.locate(
                TECHNICAL_ID))
                .thenReturn(
                        correspondence);

        when(nodeService.getProperty(
                correspondence,
                qname("technicalId")))
                .thenReturn(
                        TECHNICAL_ID);

        when(nodeService.getProperty(
                correspondence,
                qname("state")))
                .thenReturn(
                        CorrespondenceState.WITH_DIRECTOR.name());

        when(nodeService.getProperty(
                correspondence,
                qname("originDepartmentCode")))
                .thenReturn(
                        "111");

        when(nodeService.getProperty(
                correspondence,
                qname("assignedRole")))
                .thenReturn(
                        CorrespondenceActorRole.DIRECTOR.name());

        when(nodeService.getProperty(
                correspondence,
                qname("assignedDepartmentCode")))
                .thenReturn(
                        "117");

        CorrespondenceAccessContext context =
                repository.getAccessContext(
                        TECHNICAL_ID);

        assertEquals(
                CorrespondenceState.WITH_DIRECTOR,
                context.state());

        assertEquals(
                new DepartmentCode("111"),
                context.originDepartmentCode());

        assertEquals(
                CorrespondenceActorRole.DIRECTOR,
                context.assignedRole());

        assertEquals(
                new DepartmentCode("117"),
                context.assignedDepartmentCode());
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
                        new DepartmentCode("117"),
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
                Date.from(
                        FIXED_INSTANT),
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
                        new DepartmentCode("101"),
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
                        new DepartmentCode("101"),
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

    @Test
    void officeCanForwardNewDirectorHandoffToPresident() {
        NodeRef correspondence = nodeRef("00000000-0000-0000-0000-000000000900");
        NodeRef previous = nodeRef("00000000-0000-0000-0000-000000000901");
        ChildAssociationRef previousAssoc = mock(ChildAssociationRef.class);
        ChildAssociationRef newAssoc = mock(ChildAssociationRef.class);
        when(nodeLocator.locate(TECHNICAL_ID)).thenReturn(correspondence);
        when(nodeService.getProperty(correspondence, qname("state")))
            .thenReturn(CorrespondenceState.WITH_PRESIDENT_OFFICE.name());
        when(nodeService.getProperty(correspondence, qname("lastActionSequence")))
            .thenReturn(1L);
        when(previousAssoc.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(previousAssoc.getQName()).thenReturn(qname("action-000001"));
        when(previousAssoc.getChildRef()).thenReturn(previous);
        when(nodeService.getChildAssocs(correspondence))
            .thenReturn(java.util.List.of(previousAssoc));
        when(nodeService.getType(previous)).thenReturn(qname("workflowAction"));
        when(nodeService.getProperty(previous, qname("actionSequence"))).thenReturn(1L);
        when(nodeService.getProperty(previous, qname("actionType"))).thenReturn("SEND_TO_OFFICE");
        when(nodeService.getProperty(previous, qname("actorRole"))).thenReturn("DIRECTOR");
        when(nodeService.getProperty(previous, qname("toState")))
            .thenReturn("WITH_PRESIDENT_OFFICE");
        when(newAssoc.getChildRef()).thenReturn(nodeRef("00000000-0000-0000-0000-000000000902"));
        when(nodeService.createNode(eq(correspondence), eq(ContentModel.ASSOC_CONTAINS),
                any(QName.class), eq(qname("workflowAction")), any()))
            .thenReturn(newAssoc);

        repository.applyTransition(
            transitionCommand(CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceAction.SEND_TO_PRESIDENT, new DepartmentCode("101"), null, null),
            CorrespondenceState.WITH_PRESIDENT_OFFICE,
            CorrespondenceState.WITH_PRESIDENT,
            CorrespondenceActorRole.PRESIDENT);

        verify(nodeService).setProperty(correspondence, qname("state"), "WITH_PRESIDENT");
        verify(nodeService).setProperty(correspondence, qname("lastActionSequence"), 2L);
    }

    @Test
    void officeCannotForwardPresidentReturnBackToPresident() {
        NodeRef correspondence = nodeRef("00000000-0000-0000-0000-000000000900");
        NodeRef previous = nodeRef("00000000-0000-0000-0000-000000000903");
        ChildAssociationRef previousAssoc = mock(ChildAssociationRef.class);
        when(nodeLocator.locate(TECHNICAL_ID)).thenReturn(correspondence);
        when(nodeService.getProperty(correspondence, qname("state")))
            .thenReturn(CorrespondenceState.WITH_PRESIDENT_OFFICE.name());
        when(nodeService.getProperty(correspondence, qname("lastActionSequence")))
            .thenReturn(3L);
        when(previousAssoc.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(previousAssoc.getQName()).thenReturn(qname("action-000003"));
        when(previousAssoc.getChildRef()).thenReturn(previous);
        when(nodeService.getChildAssocs(correspondence))
            .thenReturn(java.util.List.of(previousAssoc));
        when(nodeService.getType(previous)).thenReturn(qname("workflowAction"));
        when(nodeService.getProperty(previous, qname("actionSequence"))).thenReturn(3L);
        when(nodeService.getProperty(previous, qname("actionType"))).thenReturn("SEND_TO_OFFICE");
        when(nodeService.getProperty(previous, qname("actorRole"))).thenReturn("PRESIDENT");
        when(nodeService.getProperty(previous, qname("toState")))
            .thenReturn("WITH_PRESIDENT_OFFICE");

        assertThrows(org.badea.correspondence.workflow.InvalidCorrespondenceTransitionException.class,
            () -> repository.applyTransition(
                transitionCommand(CorrespondenceActorRole.PRESIDENT_OFFICE,
                    CorrespondenceAction.SEND_TO_PRESIDENT, new DepartmentCode("101"), null, null),
                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                CorrespondenceState.WITH_PRESIDENT,
                CorrespondenceActorRole.PRESIDENT));

        verify(nodeService, never()).setProperty(
            correspondence, qname("state"), "WITH_PRESIDENT");
        verify(nodeService, never()).createNode(
            eq(correspondence), eq(ContentModel.ASSOC_CONTAINS),
            any(QName.class), eq(qname("workflowAction")), any());
    }

    @Test
    void officeCannotForwardMissingOrUnverifiedHistory() {
        NodeRef correspondence = nodeRef("00000000-0000-0000-0000-000000000900");
        when(nodeLocator.locate(TECHNICAL_ID)).thenReturn(correspondence);
        when(nodeService.getProperty(correspondence, qname("state")))
            .thenReturn(CorrespondenceState.WITH_PRESIDENT_OFFICE.name());
        when(nodeService.getProperty(correspondence, qname("lastActionSequence")))
            .thenReturn(0L);
        assertThrows(org.badea.correspondence.workflow.InvalidCorrespondenceTransitionException.class,
            () -> repository.applyTransition(
                transitionCommand(CorrespondenceActorRole.PRESIDENT_OFFICE,
                    CorrespondenceAction.SEND_TO_PRESIDENT, new DepartmentCode("101"), null, null),
                CorrespondenceState.WITH_PRESIDENT_OFFICE,
                CorrespondenceState.WITH_PRESIDENT,
                CorrespondenceActorRole.PRESIDENT));
        verify(nodeService, never()).setProperty(
            correspondence, qname("state"), "WITH_PRESIDENT");
    }

    @Test
    void refusesArchivingWhenPersistedDepartmentIsStillPending() {
        NodeRef parent = nodeRef("00000000-0000-0000-0000-000000000900");
        NodeRef pending = nodeRef("00000000-0000-0000-0000-000000000904");
        ChildAssociationRef association = mock(ChildAssociationRef.class);
        when(nodeLocator.locate(TECHNICAL_ID)).thenReturn(parent);
        when(nodeService.getProperty(parent, qname("state"))).thenReturn("WITH_DIRECTOR");
        when(nodeService.getProperty(parent, qname("technicalId"))).thenReturn(TECHNICAL_ID);
        when(nodeService.getChildAssocs(parent)).thenReturn(java.util.List.of(association));
        when(association.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(association.getChildRef()).thenReturn(pending);
        when(nodeService.getType(pending)).thenReturn(qname("departmentAssignment"));
        when(nodeService.getProperty(pending, qname("assignmentId"))).thenReturn("assignment-117");
        when(nodeService.getProperty(pending, qname("assignmentDepartmentCode"))).thenReturn("117");
        when(nodeService.getProperty(pending, qname("assignmentRecipientType"))).thenReturn("TO");
        when(nodeService.getProperty(pending, qname("assignmentStatus"))).thenReturn("PENDING");
        when(nodeService.getProperty(pending, qname("assignmentByEmployeeId"))).thenReturn("18280");
        when(nodeService.getProperty(pending, qname("assignmentAt"))).thenReturn(Date.from(FIXED_INSTANT));
        TransitionCorrespondenceCommand close = transitionCommand(
                CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceAction.ADMINISTRATIVE_CLOSE, new DepartmentCode("101"), null,
                "Premature close");
        assertThrows(org.badea.correspondence.workflow.InvalidCorrespondenceTransitionException.class,
                () -> repository.applyTransition(close, CorrespondenceState.WITH_DIRECTOR,
                        CorrespondenceState.ARCHIVED, null));
        verify(nodeService, never()).createNode(eq(parent), eq(ContentModel.ASSOC_CONTAINS),
                any(QName.class), eq(qname("workflowAction")), any());
        verify(nodeService, never()).setProperty(parent, qname("state"), "ARCHIVED");
    }

    private static TransitionCorrespondenceCommand transitionCommand(
            CorrespondenceActorRole actorRole,
            CorrespondenceAction action,
            DepartmentCode actorDepartmentCode,
            DepartmentCode targetDepartmentCode,
            String closureReason) {

        return new TransitionCorrespondenceCommand(
                TECHNICAL_ID,
                actorRole,
                action,
                new EmployeeId("18280"),
                actorDepartmentCode,
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