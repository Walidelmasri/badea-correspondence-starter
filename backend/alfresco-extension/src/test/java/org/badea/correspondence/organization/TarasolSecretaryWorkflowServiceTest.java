package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import java.util.Optional;

import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceRepository;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TarasolSecretaryWorkflowServiceTest {
    private static final String CASE = "7fc41b5b-aceb-48fc-9f36-367e4a36e207";
    private final DepartmentCode hr = new DepartmentCode("118");
    private final DepartmentCode office = new DepartmentCode("101");
    private final TarasolEffectiveAccessReader access = mock(TarasolEffectiveAccessReader.class);
    private final TarasolOrganizationReader organization = mock(TarasolOrganizationReader.class);
    private final CorrespondenceActorRoleResolver roles = mock(CorrespondenceActorRoleResolver.class);
    private final AlfrescoCorrespondenceRepository repo = mock(AlfrescoCorrespondenceRepository.class);
    private final TarasolSecretaryWorkflowService service =
        new TarasolSecretaryWorkflowService(access, organization, roles, repo);

    private EmployeeDirectoryEntry employee(String id) {
        return new EmployeeDirectoryEntry(new EmployeeId(id), new DirectoryUsername("emp." + id),
            "Employee " + id, null, Optional.empty());
    }

    private void pending(DepartmentCode unit, String owner) {
        when(repo.readSecretaryDraft(CASE)).thenReturn(new AlfrescoCorrespondenceRepository.SecretaryDraft(
            CASE, unit.value(), "Annual report", "Please approve", owner, "PENDING"));
    }

    private void manager(DepartmentCode unit, EmployeeDirectoryEntry actor, CorrespondenceActorRole role) {
        when(roles.resolveWorkspace(unit.value(), actor))
            .thenReturn(new CorrespondenceActorRoleResolver.Workspace(role, unit));
    }

    private TarasolOrganizationReader.ManagerWorkspace workspace(DepartmentCode unit) {
        return new TarasolOrganizationReader.ManagerWorkspace(Long.parseLong(unit.value()),
            unit, "Test " + unit.value(), null, "MANAGER");
    }

    @Test
    void ordinaryDirectorApprovesAndRoutesToOffice() {
        var actor = employee("200");
        pending(hr, "300");
        manager(hr, actor, CorrespondenceActorRole.DIRECTOR);
        when(roles.resolveCurrentUserRoles()).thenReturn(Set.of(CorrespondenceActorRole.DIRECTOR));
        assertEquals(CorrespondenceState.WITH_PRESIDENT_OFFICE,
            service.approveAndSend(actor, hr, CASE));
        ArgumentCaptor<TransitionCorrespondenceCommand> action =
            ArgumentCaptor.forClass(TransitionCorrespondenceCommand.class);
        verify(repo).approveSecretaryDraft(action.capture(), eq(CorrespondenceState.WITH_PRESIDENT_OFFICE),
            eq(CorrespondenceActorRole.PRESIDENT_OFFICE));
        assertEquals(CorrespondenceAction.SEND_TO_OFFICE, action.getValue().action());
        assertEquals(actor.employeeId(), action.getValue().actorEmployeeId());
    }

    @Test
    void eligibleDualManagerApprovesDirectToPresident() {
        var actor = employee("18284");
        pending(hr, "300");
        manager(hr, actor, CorrespondenceActorRole.DIRECTOR);
        when(roles.resolveCurrentUserRoles()).thenReturn(Set.of(
            CorrespondenceActorRole.DIRECTOR, CorrespondenceActorRole.PRESIDENT_OFFICE));
        when(organization.findCurrentManagerWorkspaces(actor.employeeId()))
            .thenReturn(List.of(workspace(hr), workspace(office)));
        assertEquals(CorrespondenceState.WITH_PRESIDENT, service.approveAndSend(actor, hr, CASE));
        verify(repo).approveSecretaryDraft(argThat(c ->
            c.action() == CorrespondenceAction.SEND_TO_PRESIDENT &&
            c.actorRole() == CorrespondenceActorRole.DIRECTOR &&
            c.actorEmployeeId().equals(actor.employeeId())),
            eq(CorrespondenceState.WITH_PRESIDENT), eq(CorrespondenceActorRole.PRESIDENT));
    }

    @Test
    void officeSecretaryIsApprovedByOfficeManagerAndSentDirectlyToPresident() {
        var actor = employee("18284");
        pending(office, "300");
        manager(office, actor, CorrespondenceActorRole.PRESIDENT_OFFICE);
        assertEquals(CorrespondenceState.WITH_PRESIDENT,
            service.approveAndSend(actor, office, CASE));
        verify(repo).approveSecretaryDraft(argThat(c ->
                c.actorRole() == CorrespondenceActorRole.PRESIDENT_OFFICE &&
                c.action() == CorrespondenceAction.SEND_TO_PRESIDENT),
            eq(CorrespondenceState.WITH_PRESIDENT), eq(CorrespondenceActorRole.PRESIDENT));
    }

    @Test
    void priorOfficeAppointmentIsNotEnoughToSendDirectlyToPresident() {
        var actor = employee("18284");
        pending(hr, "300");
        manager(hr, actor, CorrespondenceActorRole.DIRECTOR);
        when(roles.resolveCurrentUserRoles()).thenReturn(Set.of(
            CorrespondenceActorRole.DIRECTOR, CorrespondenceActorRole.PRESIDENT_OFFICE));
        when(organization.findCurrentManagerWorkspaces(actor.employeeId()))
            .thenReturn(List.of(workspace(hr)));
        assertEquals(CorrespondenceState.WITH_PRESIDENT_OFFICE,
            service.approveAndSend(actor, hr, CASE));
    }

    @Test
    void incorrectUnitOrStaleApprovalCannotBeApproved() {
        var actor = employee("18284");
        manager(hr, actor, CorrespondenceActorRole.DIRECTOR);
        pending(office, "300");
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.approveAndSend(actor, hr, CASE));
        verify(repo, never()).approveSecretaryDraft(any(), any(), any());
    }

    @Test
    void secretaryCannotCreateOrSubmitWithoutActiveAssignment() {
        var secretary = employee("300");
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.create(secretary, hr, "Request", "Body"));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.submit(secretary, hr, CASE));
        verifyNoInteractions(repo);
    }

    @Test
    void secretarySubmitAndManagerReturnPreserveActualActors() {
        var secretary = employee("300");
        var manager = employee("200");
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), hr)).thenReturn(true);
        when(repo.readSecretaryDraft(CASE)).thenReturn(new AlfrescoCorrespondenceRepository.SecretaryDraft(
            CASE, hr.value(), "Report", "Body", secretary.employeeId().value(), "DRAFT"),
            new AlfrescoCorrespondenceRepository.SecretaryDraft(
                CASE, hr.value(), "Report", "Body", secretary.employeeId().value(), "PENDING"));
        manager(hr, manager, CorrespondenceActorRole.DIRECTOR);
        service.submit(secretary, hr, CASE);
        verify(repo).changeSecretaryStatus(argThat(c ->
            c.actorEmployeeId().equals(secretary.employeeId()) &&
            c.action() == CorrespondenceAction.SUBMIT_FOR_MANAGER_APPROVAL),
            eq("DRAFT"), eq("PENDING"), eq(CorrespondenceActorRole.DIRECTOR));
        service.returnForRevision(manager, hr, CASE, "Please revise the figures");
        verify(repo).changeSecretaryStatus(argThat(c ->
            c.actorEmployeeId().equals(manager.employeeId()) &&
            c.action() == CorrespondenceAction.RETURN_TO_SECRETARY &&
            "Please revise the figures".equals(c.explanation())),
            eq("PENDING"), eq("RETURNED"), eq(CorrespondenceActorRole.SECRETARY));
    }
}
