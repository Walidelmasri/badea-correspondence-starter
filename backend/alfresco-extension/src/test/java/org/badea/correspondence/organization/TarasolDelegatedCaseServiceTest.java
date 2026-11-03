package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.badea.correspondence.application.*;
import org.badea.correspondence.directory.*;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceNodeLocator;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TarasolDelegatedCaseServiceTest {
    private static final String ID = "7fc41b5b-aceb-48fc-9f36-367e4a36e207";
    private final DepartmentCode unit = new DepartmentCode("118");
    private final EmployeeDirectoryEntry actor = new EmployeeDirectoryEntry(
        new EmployeeId("901"), new DirectoryUsername("delegate.test"),
        "Delegated Employee", null, Optional.empty());
    private final TarasolEffectiveAccessReader access = mock(TarasolEffectiveAccessReader.class);
    private final CorrespondenceTransitionRepository transitions = mock(CorrespondenceTransitionRepository.class);
    private final CorrespondenceDetailsRepository details = mock(CorrespondenceDetailsRepository.class);
    private final CorrespondenceDocumentContentRepository content = mock(CorrespondenceDocumentContentRepository.class);
    private final CorrespondenceTransitionService workflow = mock(CorrespondenceTransitionService.class);
    private final CorrespondenceNodeLocator locator = mock(CorrespondenceNodeLocator.class);
    private final NodeService nodes = mock(NodeService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final NodeRef node = new NodeRef("workspace://SpacesStore/" + UUID.randomUUID());
    private final TarasolDelegatedCaseService service = new TarasolDelegatedCaseService(
        access, transitions, details, content, workflow, locator, nodes, permissions);

    @BeforeEach void setup() {
        when(locator.locate(ID)).thenReturn(node);
        when(nodes.getProperty(node, ContentModel.PROP_CREATED)).thenReturn(new Date(1790000000000L));
        when(transitions.getAccessContext(ID)).thenReturn(new CorrespondenceAccessContext(
            CorrespondenceState.WITH_DIRECTOR, new DepartmentCode("101"),
            CorrespondenceActorRole.DIRECTOR, unit, List.of(), List.of(), null, null));
        when(access.findActiveDelegation(eq(actor.employeeId()), eq(unit), eq(ID), any()))
            .thenReturn(Optional.of(new TarasolEffectiveAccessReader.Delegation(51, 42, "CASE_HANDOFF")));
        when(details.findByTechnicalId(ID)).thenReturn(new CorrespondenceDetails(ID,
            CorrespondenceState.WITH_DIRECTOR, "Test", null, null, null, null,
            "101", "Creator", null, List.of()));
    }

    @Test void delegateMayReadOnlyTheExplicitPersistedCase() {
        assertEquals("Test", service.read(actor, unit, ID).subject());
        verify(access).findActiveDelegation(eq(actor.employeeId()), eq(unit), eq(ID), any());
    }

    @Test void noGrantOrRevokedGrantDeniesWithoutLeakingDetails() {
        when(access.findActiveDelegation(eq(actor.employeeId()), eq(unit), eq(ID), any()))
            .thenReturn(Optional.empty());
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.read(actor, unit, ID));
        verifyNoInteractions(details);
    }

    @Test void wrongDepartmentDeniedBeforeGrantLookup() {
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.read(actor, new DepartmentCode("117"), ID));
        verifyNoInteractions(access, details);
    }

    @Test void caseScopedGrantWorksWhenOriginFolderBelongsToAnotherUnit() {
        assertEquals("Test", service.read(actor, unit, ID).subject());
        verify(permissions, never()).hasPermission(node, PermissionService.READ);
    }

    @Test void onlyCurrentDirectorActionsCanBeDelegatedAndActorIsAudited() {
        when(workflow.transition(any())).thenReturn(CorrespondenceState.WITH_PRESIDENT_OFFICE);
        assertEquals(CorrespondenceState.WITH_PRESIDENT_OFFICE,
            service.act(actor, unit, ID, CorrespondenceAction.SEND_TO_OFFICE));
        var command = ArgumentCaptor.forClass(TransitionCorrespondenceCommand.class);
        verify(workflow).transition(command.capture());
        assertEquals(actor.employeeId(), command.getValue().actorEmployeeId());
        assertEquals(unit, command.getValue().actorDepartmentCode());
        assertEquals(CorrespondenceActorRole.DIRECTOR, command.getValue().actorRole());
        assertTrue(command.getValue().explanation().contains("delegation ID 51"));
        verify(permissions, never()).hasPermission(node, PermissionService.WRITE);
    }

    @Test void cannotUseDelegationToReachPresidentOrOtherActions() {
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.act(actor, unit, ID, CorrespondenceAction.SEND_TO_PRESIDENT));
        verifyNoInteractions(workflow);
    }

    @Test void revokedGrantCannotTransitionRegardlessOfRepositoryAcl() {
        when(access.findActiveDelegation(eq(actor.employeeId()), eq(unit), eq(ID), any()))
            .thenReturn(Optional.empty());
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.act(actor, unit, ID, CorrespondenceAction.SEND_TO_OFFICE));
        verifyNoInteractions(workflow);
    }

    @Test void historicalCaseHandoffReadNeverPermitsTransition() {
        when(transitions.getAccessContext(ID)).thenReturn(new CorrespondenceAccessContext(
            CorrespondenceState.ARCHIVED, unit, null, null, List.of(), List.of(), null, null));
        assertEquals("Test", service.read(actor, unit, ID).subject());
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.act(actor, unit, ID, CorrespondenceAction.SEND_TO_OFFICE));
    }

    @Test void malformedCaseIdFailsBeforeAnyRepositoryLookup() {
        assertThrows(IllegalArgumentException.class,
            () -> service.read(actor, unit, "../../another"));
        verifyNoInteractions(details, access);
    }
    @Test void activeCaseHandoffIsDiscoverableOnlyAfterIndividualAuthorization() {
        when(access.findCurrentCaseHandoffs(actor.employeeId(), unit)).thenReturn(List.of(ID));
        var result = service.caseHandoffs(actor, unit);
        assertEquals(1, result.size());
        assertEquals(ID, result.getFirst().technicalId());
        assertEquals("Test", result.getFirst().subject());
    }

    @Test void expiredOrRevokedCaseHandoffDoesNotAppearInDiscovery() {
        when(access.findCurrentCaseHandoffs(actor.employeeId(), unit)).thenReturn(List.of(ID));
        when(access.findActiveDelegation(eq(actor.employeeId()), eq(unit), eq(ID), any()))
            .thenReturn(Optional.empty());
        assertTrue(service.caseHandoffs(actor, unit).isEmpty());
        verifyNoInteractions(details);
    }

}
